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
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

/**
 * An abstract base class for all browser interface implementations. Browsers are interfaces like
 * {@link org.hawkular.inventory.api.Environments.Single} that the user can use to proceed with the traversal across
 * the inventory. Unlike the browsers, the "access" interfaces like {@link org.hawkular.inventory.api.Environments.Read}
 * to filter the the elements at the current position in the traversal.
 *
 * <p>For the sake of code reuse this class inherits from {@link AbstractSourcedGraphService} even though some of the
 * abstract methods defined there don't make sense in a browser interface and thus are implemented as final here and
 * throw exceptions.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
abstract class AbstractBrowser<E extends Entity<B, U>, B extends Entity.Blueprint, U extends Entity.Update>
        extends AbstractSourcedGraphService<Void, Void, E, B, U> {

    AbstractBrowser(InventoryContext context, Class<E> entityClass, FilterApplicator.Tree path) {
        super(context, entityClass, new PathContext(path, (Filter[]) null));
    }

    public E entity() {
        HawkularPipeline<?, Vertex> q = source();

        if (!q.hasNext()) {
            throw new EntityNotFoundException(entityClass, FilterApplicator.filters(pathContext.sourcePath));
        }

        Entity<?, ?> ret = convert(q.next());

        if (!isApplicable(ret)) {
            throw new EntityNotFoundException(entityClass, FilterApplicator.filters(pathContext.sourcePath));
        }

        return entityClass.cast(ret);
    }

    @SuppressWarnings("unchecked")
    public Page<E> entities(Pager pager) {
        HawkularPipeline<?, E> q;
        if (context.getResultFilter() == null) {
            //no result filtering so, we can save the relatively expensive conversion from the vertex to the entity
            //only for results we need.
            q = source().counter("total").page(pager).transform(v -> entityClass.cast(convert(v)));
        } else {
            //the ResultFilter interface requires an entity to check its applicability and can rule out some of the
            //entities from the result set, which affects the total count. We therefore need to convert to entity first
            //and only then filter, count and page.
            //Note that it would not be enough to pass the current path and the ID to the filter, because for the filter
            //to have stable ids, it needs to have the "canonical" path to the entity, which the inventory traversal
            //path might not be. The transformation of a non-canonical to canonical path is essentially identical
            //operation to converting the vertex to the entity.
            q = source().transform(v -> entityClass.cast(convert(v))).filter(this::isApplicable).counter("total")
                    .page(pager, (e, p) -> {
                        if (AbstractElement.ID_PROPERTY.equals(p)) {
                            return (Comparable) e.getId();
                        } else {
                            return (Comparable) e.getProperties().get(p);
                        }
                    });
        }

        return new Page<>(q.toList(), pager, q.getCount("total"));
    }

    public RelationshipService<E, B, U> relationships() {
        return relationships(Relationships.Direction.outgoing);
    }

    public RelationshipService<E, B, U> relationships(Relationships.Direction direction) {
        return new RelationshipService<>(context, new PathContext(sourcePaths, (Filter[]) null), entityClass,
                direction);
    }

    @Override
    protected final Void createSingleBrowser(FilterApplicator.Tree path) {
        throw new IllegalStateException("This method is not valid on a browser interface.");
    }

    @Override
    protected final Void createMultiBrowser(FilterApplicator.Tree path) {
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
