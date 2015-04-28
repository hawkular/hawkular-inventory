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
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractBrowser<E extends Entity<B, U>, B extends Entity.Blueprint, U extends Entity.Update>
        extends AbstractSourcedGraphService<Void, Void, E, B, U> {

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

    public Page<E> entities(Pager pager) {
        List<E> ret = new ArrayList<>();

        HawkularPipeline<?, Vertex> q = source().counter("total").page(pager);
        q.forEach(v -> ret.add(entityClass.cast(convert(v))));

        return new Page<>(ret, pager, q.getCount("total"));
    }

    public RelationshipService<E, B, U> relationships() {
        return relationships(Relationships.Direction.outgoing);
    }

    public RelationshipService<E, B, U> relationships(Relationships.Direction direction) {
        return new RelationshipService<>(context, new PathContext(path, Filter.all()), entityClass, direction);
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
    protected final String getProposedId(Entity.Blueprint b) {
        throw new IllegalStateException("This method is not valid on a browser interface.");
    }

    @Override
    protected final Filter[] initNewEntity(Vertex newEntity, Entity.Blueprint blueprint) {
        throw new IllegalStateException("This method is not valid on a browser interface.");
    }
}
