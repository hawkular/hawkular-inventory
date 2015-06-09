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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.spi.CanonicalPath;
import org.hawkular.inventory.lazy.spi.SwitchElementType;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class LazyRelationships {

    private LazyRelationships() {

    }

    public static final class ReadWrite<BE> extends Traversal<BE, Relationship> implements Relationships.ReadWrite {

        private final Relationships.Direction direction;
        private final Class<? extends Entity<?, ?>> originEntityType;

        public ReadWrite(TraversalContext<BE, Relationship> context, Class<? extends Entity<?, ?>> originEntityType) {
            super(context);
            QueryFragment[] filters = context.selectCandidates.getFragments();
            direction = ((SwitchElementType) filters[filters.length - 1].getFilter()).getDirection();
            this.originEntityType = originEntityType;
        }

        @Override
        public Relationships.Multiple named(String name) {
            return new Multiple<>(direction, context.proceed().where(RelationWith.name(name)).get());
        }

        @Override
        public Relationships.Multiple named(Relationships.WellKnown name) {
            return named(name.name());
        }

        @Override
        public Relationships.Single get(String id) throws EntityNotFoundException, RelationNotFoundException {
            return new Single<>(context.proceed().where(With.id(id)).get());
        }

        @Override
        public Relationships.Multiple getAll(RelationFilter... filters) {
            return new Multiple<>(direction, context.proceed().where(filters).get());
        }

        @Override
        public Relationships.Single linkWith(String name, Entity<?, ?> targetOrSource,
                Map<String, Object> properties) throws IllegalArgumentException {

            if (null == name) {
                throw new IllegalArgumentException("name was null");
            }
            if (null == targetOrSource) {
                throw new IllegalArgumentException("targetOrSource was null");
            }

            BE incidenceObject = context.backend.find(CanonicalPath.of(targetOrSource));

            Page<BE> origins = context.backend.query(context.sourcePath, Pager.single());
            if (origins.isEmpty()) {
                throw new EntityNotFoundException(originEntityType, QueryFragmentTree.filters(context.select().get()));
            }

            BE origin = origins.get(0);

            if (Relationships.WellKnown.contains.name().equals(name)) {
                checkContains(origin, direction, incidenceObject);
            }

            BE relationshipObject;

            try {
                switch (direction) {
                    case incoming:
                        relationshipObject = context.backend.relate(incidenceObject, origin, name, properties);
                        break;
                    case outgoing:
                        relationshipObject = context.backend.relate(origin, incidenceObject, name, properties);
                        break;
                    case both:
                        context.backend.relate(incidenceObject, origin, name, properties);
                        relationshipObject = context.backend.relate(origin, incidenceObject, name, properties);
                        break;
                    default:
                        throw new AssertionError("Unhandled direction when linking. This shouldn't have happened.");
                }

                context.backend.commit();
            } catch (Throwable t) {
                context.backend.rollback();
                throw t;
            }

            String id = context.backend.extractId(relationshipObject);

            return new Single<>(context.replacePath(QueryFragmentTree.path().with(RelationWith.id(id)).get()));
        }

        @Override
        public Relationships.Single linkWith(Relationships.WellKnown name, Entity<?, ?> targetOrSource,
                Map<String, Object> properties) throws IllegalArgumentException {
            return linkWith(name.name(), targetOrSource, properties);
        }

        @Override
        public void update(String id, Relationship.Update update) throws RelationNotFoundException {
            //TODO this doesn't respect the current position in the graph
            BE relationshipObject = context.backend.find(CanonicalPath.builder().withRelationshipId(id).build());
            try {
                context.backend.update(relationshipObject, update);
                context.backend.commit();
            } catch (Throwable t) {
                context.backend.rollback();
                throw t;
            }
        }

        @Override
        public void delete(String id) throws RelationNotFoundException {
            //TODO this doesn't respect the current position in the graph
            //TODO this probably should not allow to delete "contains" and other semantically-rich rels
            try {
                BE relationshipObject = context.backend.find(CanonicalPath.builder().withRelationshipId(id).build());
                context.backend.delete(relationshipObject);
            } catch (NoSuchElementException e) {
                throw new RelationNotFoundException("Could not find relationship to delete",
                        QueryFragmentTree.filters(context.select().with(RelationWith.id(id)).get()));
            }
        }


        private void checkContains(BE origin, Relationships.Direction direction, BE incidenceObject) {
            if (direction == Relationships.Direction.both) {
                throw new IllegalArgumentException("2 vertices cannot contain each other.");
            }

            //check for diamonds
            if (direction == outgoing && context.backend
                    .hasRelationship(incidenceObject, Relationships.Direction.incoming, contains.name())) {
                throw new IllegalArgumentException("The target is already contained in another entity.");
            } else if (direction == Relationships.Direction.incoming) {
                if (context.backend.hasRelationship(origin, Relationships.Direction.incoming, contains.name())) {
                    throw new IllegalArgumentException("The source is already contained in another entity.");
                }
            }

            //check for loops
            if (origin.equals(incidenceObject)) {
                throw new IllegalArgumentException("An entity cannot contain itself.");
            }

            if (direction == Relationships.Direction.incoming) {
                Iterator<BE> containees = context.backend.getTransitiveClosureOver(origin, contains.name(), outgoing);

                while (containees.hasNext()) {
                    BE containee = containees.next();
                    if (containee.equals(incidenceObject)) {
                        throw new IllegalArgumentException("The target (indirectly) contains the source." +
                                " The source therefore cannot contain the target.");
                    }
                }
            } else if (direction == outgoing) {
                Iterator<BE> containers = context.backend.getTransitiveClosureOver(origin, contains.name(), incoming);

                while (containers.hasNext()) {
                    BE container = containers.next();
                    if (container.equals(incidenceObject)) {
                        throw new IllegalArgumentException("The source (indirectly) contains the target." +
                                " The target therefore cannot contain the source.");
                    }
                }
            }
        }
    }

    public static final class Read<BE> extends Traversal<BE, Relationship> implements Relationships.Read {
        private final Relationships.Direction direction;

        public Read(TraversalContext<BE, Relationship> context) {
            super(context);
            QueryFragment[] filters = context.selectCandidates.getFragments();
            direction = ((SwitchElementType) filters[filters.length - 1].getFilter()).getDirection();
        }

        @Override
        public Relationships.Multiple named(String name) {
            return new Multiple<>(direction, context.proceed().where(RelationWith.name(name)).get());
        }

        @Override
        public Relationships.Multiple named(Relationships.WellKnown name) {
            return named(name.name());
        }

        @Override
        public Relationships.Single get(String id) throws EntityNotFoundException, RelationNotFoundException {
            return new Single<>(context.proceed().where(With.id(id)).get());
        }

        @Override
        public Relationships.Multiple getAll(RelationFilter... filters) {
            return new Multiple<>(direction, context.proceed().where(filters).get());
        }
    }

    public static final class Single<BE> extends Fetcher<BE, Relationship> implements Relationships.Single {

        public Single(TraversalContext<BE, Relationship> context) {
            super(context);
        }
    }

    public static final class Multiple<BE> extends Fetcher<BE, Relationship> implements Relationships.Multiple {

        private final Relationships.Direction direction;

        public Multiple(Relationships.Direction direction, TraversalContext<BE, Relationship> context) {
            super(context);
            this.direction = direction;
        }

        @Override
        public Tenants.Read tenants() {
            return new LazyTenants.Read<>(context.proceedFromRelationshipsTo(direction, Tenant.class).get());
        }

        @Override
        public Environments.Read environments() {
            return new LazyEnvironments.Read<>(context.proceedFromRelationshipsTo(direction, Environment.class).get());
        }

        @Override
        public Feeds.Read feeds() {
            return new LazyFeeds.Read<>(context.proceedFromRelationshipsTo(direction, Feed.class).get());
        }

        @Override
        public MetricTypes.Read metricTypes() {
            return new LazyMetricTypes.Read<>(context.proceedFromRelationshipsTo(direction, MetricType.class).get());
        }

        @Override
        public Metrics.Read metrics() {
            return new LazyMetrics.Read<>(context.proceedFromRelationshipsTo(direction, Metric.class).get());
        }

        @Override
        public Resources.Read resources() {
            return new LazyResources.Read<>(context.proceedFromRelationshipsTo(direction, Resource.class).get());
        }

        @Override
        public ResourceTypes.Read resourceTypes() {
            return new LazyResourceTypes.Read<>(context.proceedFromRelationshipsTo(direction, ResourceType.class).
                    get());
        }
    }
}
