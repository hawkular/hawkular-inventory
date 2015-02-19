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

package org.hawkular.inventory.impl.blueprints;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Entity;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractBrowser<E extends Entity> extends AbstractSourcedGraphService<Void, E, Void> {
    AbstractBrowser(TransactionalGraph graph, Class<E> entityClass, Filter... path) {
        super(graph, entityClass, new PathContext(path, null));
    }

    public E entity() {
        HawkularPipeline<?, Vertex> q = source();

        if (!q.hasNext()) {
            throw new IllegalArgumentException("Entity does not exist.");
        }

        return entityClass.cast(convert(q.next()));
    }

    public RelationshipService relationships() {
        return new RelationshipService(graph, source().next());
    }

    @Override
    protected final Void createBrowser(Filter... path) {
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
