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

package org.hawkular.inventory.impl.tinkerpop;

import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Entity;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractBrowser<E extends Entity> extends AbstractSourcedGraphService<Void, Void, E, Void> {

    AbstractBrowser(InventoryContext context, Class<E> entityClass, FilterApplicator... path) {
        super(context, entityClass, new PathContext(path, null));
    }

    public E entity() {
        HawkularPipeline<?, Vertex> q = source();

        if (!q.hasNext()) {
            throw new EntityNotFoundException(entityClass, FilterApplicator.filters(pathContext.path));
        }

        return entityClass.cast(convert(q.next()));
    }

    public Set<E> entities() {
        Set<E> ret = new HashSet<>();

        source().forEach(v -> ret.add(entityClass.cast(convert(v))));

        return ret;
    }

    public RelationshipService relationships() {
        return new RelationshipService(context.getGraph(), new PathContext(path, Filter.all()), entityClass);
    }

    @Override
    protected final Void createSingleBrowser(FilterApplicator... path) {
        throw new IllegalStateException("This method is not valid on a browser interface.");
    }

    @Override
    protected final Void createMultiBrowser(FilterApplicator... path) {
        throw new IllegalStateException("This method is not valid on a browser interface.");
    }

    @Override
    protected final String getProposedId(Void b) {
        throw new IllegalStateException("This method is not valid on a browser interface.");
    }

    @Override
    protected final Filter[] initNewEntity(Vertex newEntity, Void blueprint) {
        throw new IllegalStateException("This method is not valid on a browser interface.");
    }
}
