/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Change;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.api.paging.TransformingPage;
import org.hawkular.inventory.base.spi.CommitFailureException;

/**
 * A base class for all interface impls that need to resolve the entities.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
abstract class Fetcher<BE, E extends AbstractElement<?, U>, U extends AbstractElement.Update>
        extends Traversal<BE, E> implements ResolvableToSingle<E, U>, ResolvableToMany<E> {

    private boolean useCachedEntity = true;

    public Fetcher(TraversalContext<BE, E> context) {
        super(context);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E entity() throws EntityNotFoundException, RelationNotFoundException {
        if (useCachedEntity && context.getCreatedEntity() != null) {
            useCachedEntity = false;
            return context.getCreatedEntity();
        }
        return loadEntity((b, e, tx) -> e);
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
    protected <T> T loadEntity(EntityConvertor<BE, E, T> conversion)
            throws EntityNotFoundException, RelationNotFoundException {

        return inTx(tx -> {
            BE result = tx.querySingle(context.select().get());

            if (result == null) {
                throwNotFoundException();
            }

            E entity = tx.convert(result, context.entityClass);

            if (!isApplicable(entity)) {
                throwNotFoundException();
            }

            return conversion.convert(result, entity, tx);
        });
    }

    @Override
    public void delete(Instant time) {
        //TODO implement
        useCachedEntity = false;
        context.setCreatedEntity(null);
    }

    @Override public void eradicate() {
        inTx(tx -> {
            Util.delete(context.entityClass, tx, context.select().get(), this::preDelete, this::postDelete);
            return null;
        });
        useCachedEntity = false;
        context.setCreatedEntity(null);
    }

    @Override public List<Change<E, ?>> history() {
        //TODO implement
        return Collections.emptyList();
    }

    @Override
    public void update(U u) throws EntityNotFoundException, RelationNotFoundException {
        inTx(tx -> {
            Util.update(context.entityClass, tx, context.select().get(), u, this::preUpdate, this::postUpdate);
            return null;
        });

        if (useCachedEntity && context.getCreatedEntity() != null) {
            context.setCreatedEntity((E) context.getCreatedEntity().update().with(u));
        }
    }

    /**
     * Serves the same purpose as {@link Mutator#preDelete(Object, Object, Transaction <BE>)} and is
     * called during the {@link #delete()} method inside the transaction.
     *
     * @param deletedEntity the backend representation of the deleted entity
     * @param transaction the transaction in which the delete is executing
     */
    protected void preDelete(BE deletedEntity, Transaction<BE> transaction) {

    }

    protected void postDelete(BE deletedEntity, Transaction<BE> transaction) {

    }

    /**
     * Hook to be run prior to update. Serves the same purpose as
     * {@link Mutator#preUpdate(Object, Object, Entity.Update, Transaction <BE>)} but is not supplied
     * the id object that can be determined from the updated entity.
     *
     * <p>By default, this does nothing.
     *
     * @param updatedEntity the backend representation of the updated entity
     * @param update        the update object
     * @param transaction   the transaction in which the update is executing
     */
    protected void preUpdate(BE updatedEntity, U update, Transaction<BE> transaction) {

    }

    /**
     * Hook to be run just after an update to the entity was made but before the transaction has been committed.
     * This is for occasions where it is easier to read the already updated data from the backend rather than seeing
     * the unmodified original data and having the update object at hand.
     *
     * @param updatedEntity the entity to which the update has been applied
     * @param transaction   the transaction in which the update is executing
     */
    protected void postUpdate(BE updatedEntity, Transaction<BE> transaction) {

    }

    @Override
    public Page<E> entities(Pager pager) {
        return loadEntities(pager, (b, e, tx) -> e);
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
    protected <T> Page<T> loadEntities(Pager pager, EntityConvertor<BE, E, T> conversionFunction) {
        return inCommittableTx(tx -> {
            Function<BE, Pair<BE, E>> conversion =
                    (e) -> new Pair<>(e, tx.convert(e, context.entityClass));

            Function<Pair<BE, E>, Boolean> filter = context.configuration.getResultFilter() == null ? null :
                    (p) -> context.configuration.getResultFilter().isApplicable(p.second);

            Page<Pair<BE, E>> intermediate =
                    tx.<Pair<BE, E>>query(context.select().get(), pager, conversion, filter);

            return new TransformingPage<Pair<BE, E>, T>(intermediate,
                    (p) -> conversionFunction.convert(p.first, p.second, tx)) {
                @Override public void close() {
                    try {
                        tx.commit();
                    } catch (CommitFailureException e) {
                        throw new IllegalStateException("Failed to commit the read operation.", e);
                    }
                    super.close();
                }
            };
        });
    }

    @SuppressWarnings("unchecked")
    protected void throwNotFoundException() {
        throwNotFoundException(context);
    }

    static void throwNotFoundException(TraversalContext<?, ?> context) {
        if (Entity.class.isAssignableFrom(context.entityClass)) {
            throw new EntityNotFoundException(context.entityClass, Query.filters(context.select().get()));
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

    interface EntityConvertor<BE, E extends AbstractElement<?, ?>, T> {
        T convert(BE backendRepresentation, E entity, Transaction<BE> tx);
    }
}
