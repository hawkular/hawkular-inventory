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

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

import java.util.function.Function;

/**
 * A base class for all interface impls that need to resolve the entities.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
abstract class Fetcher<BE, E extends AbstractElement<?, ?>> extends Traversal<BE, E> implements ResolvableToSingle<E>,
                                                                                                ResolvableToMany<E> {
    public Fetcher(TraversalContext<BE, E> context) {
        super(context);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E entity() throws EntityNotFoundException, RelationNotFoundException {
        return readOnly(() -> {
            Page<BE> results = context.backend.query(context.select().get(), Pager.single());

            if (results.isEmpty()) {
                throwNotFoundException();
            }

            BE entity = results.get(0);

            E ret = context.backend.convert(entity, context.entityClass);

            if (!isApplicable(ret)) {
                throwNotFoundException();
            }

            return ret;
        });
    }

    @Override
    public Page<E> entities(Pager pager) {
        return readOnly(() -> {
            Function<BE, E> conversion = (e) -> context.backend.convert(e, context.entityClass);
            Function<E, Boolean> filter = context.configuration.getResultFilter() == null ? null :
                    (e) -> context.configuration.getResultFilter().isApplicable(e);

            return context.backend.<E>query(context.select().get(), pager, conversion, filter);
        });
    }

    @SuppressWarnings("unchecked")
    private void throwNotFoundException() {
        if (Entity.class.isAssignableFrom(context.entityClass)) {
            throw new EntityNotFoundException((Class<Entity<?, ?>>) context.entityClass,
                    Query.filters(context.select().get()));
        } else {
            //TODO this is not correct?
            throw new RelationNotFoundException((Class<Entity<?, ?>>) context.entityClass,
                    Query.filters(context.sourcePath));
        }
    }
}
