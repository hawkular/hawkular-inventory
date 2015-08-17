/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.base;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

/**
 * A base class for all interface impls that need to resolve the entities.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
abstract class Fetcher<BE, E extends AbstractElement<?, U>, U extends AbstractElement.Update>
        extends Traversal<BE, E> implements ResolvableToSingle<E, U>, ResolvableToMany<E> {

    public Fetcher(TraversalContext<BE, E> context) {
        super(context);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E entity() throws EntityNotFoundException, RelationNotFoundException {
        return loadEntity((b, e) -> e);
    }

    /**
     * Loads the entity from the backend and let's the caller do some conversion on either the backend representation of
     * the entity or the converted entity (both of these are required even during the loading so no unnecessary work is
     * done by providing both of the to the caller).
     *
     * @param conversion the conversion function taking the backend entity as well as the model entity
     * @param <T>        the result type
     * @return the converted result of loading the entity
     * @throws EntityNotFoundException
     * @throws RelationNotFoundException
     */
    protected <T> T loadEntity(BiFunction<BE, E, T> conversion)
            throws EntityNotFoundException, RelationNotFoundException {

        return readOnly(() -> {
            Page<BE> results = context.backend.query(context.select().get(), Pager.single());

            if (results.isEmpty()) {
                throwNotFoundException();
            }

            BE backendEntity = results.get(0);

            E entity = context.backend.convert(backendEntity, context.entityClass);

            if (!isApplicable(entity)) {
                throwNotFoundException();
            }

            return conversion.apply(backendEntity, entity);
        });
    }

    @Override
    public void delete() {
        Util.delete(context, context.select().get(), this::cleanup);
    }

    @Override
    public void update(U u) throws EntityNotFoundException, RelationNotFoundException {
        Util.update(context, context.select().get(), u, this::preUpdate);
    }

    /**
     * Serves the same purpose as {@link Mutator#cleanup(Object, Object)} and is called during the {@link #delete()}
     * method inside the transaction.
     *
     * @param deletedEntity the backend representation of the deleted entity
     */
    protected void cleanup(BE deletedEntity) {

    }

    /**
     * Hook to be run prior to update. Serves the same purpose as
     * {@link Mutator#preUpdate(Object, Object, AbstractElement.Update)} but is not supplied the id object that can be
     * determined from the updated entity.
     *
     * <p>By default, this does nothing.
     *
     * @param updatedEntity the backend representation of the updated entity
     * @param update        the update object
     */
    protected void preUpdate(BE updatedEntity, U update) {

    }

    @Override
    public Page<E> entities(Pager pager) {
        return loadEntities(pager, (b, e) -> e);
    }

    /**
     * Loads the entities given the pager and converts them using the provided conversion function to the desired type.
     *
     * <p>Note that the conversion function accepts both the backend entity and the converted entity because both
     * of them are required anyway during the loading and thus the function can choose which one to use without any
     * additional conversion cost.
     *
     * @param pager              the pager specifying the page of the data to load
     * @param conversionFunction the conversion function to convert to the desired target type
     * @param <T>                the desired target type of the elements of the returned page
     * @return the page of the results as specified by the pager
     */
    protected <T> Page<T> loadEntities(Pager pager, BiFunction<BE, E, T> conversionFunction) {
        return readOnly(() -> {
            Function<BE, Pair<BE, E>> conversion =
                    (e) -> new Pair<>(e, context.backend.convert(e, context.entityClass));

            Function<Pair<BE, E>, Boolean> filter = context.configuration.getResultFilter() == null ? null :
                    (p) -> context.configuration.getResultFilter().isApplicable(p.second);

            Page<Pair<BE, E>> intermediate =
                    context.backend.<Pair<BE, E>>query(context.select().get(), pager, conversion, filter);

            List<T> converted = intermediate.stream().map((p) -> conversionFunction.apply(p.first, p.second))
                    .collect(toList());

            return new Page<>(converted, intermediate.getPageContext(), intermediate.getTotalSize());
        });
    }

    @SuppressWarnings("unchecked")
    private void throwNotFoundException() {
        if (Entity.class.isAssignableFrom(context.entityClass)) {
            throw new EntityNotFoundException((Class<? extends Entity<?, ?>>) context.entityClass,
                    Query.filters(context.select().get()));
        } else {
            //TODO this is not correct?
            throw new RelationNotFoundException((String) null, Query.filters(context.sourcePath));
        }
    }

    private static final class Pair<F, S> {
        private final F first;
        private final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}
