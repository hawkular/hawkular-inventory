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

import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.Action.updated;

import java.util.Map;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.QueryFragment;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.SwitchElementType;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.base.spi.Discriminator;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseRelationships {

    private BaseRelationships() {

    }

    public static class ReadWrite<BE> extends Traversal<BE, Relationship> implements Relationships.ReadWrite {

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
            return new Single<>(context.proceed().where(RelationWith.id(id)).get());
        }

        @Override
        public Relationships.Multiple getAll(RelationFilter... filters) {
            return new Multiple<>(direction, context.proceed().where(filters).get());
        }

        @SuppressWarnings("unchecked")
        @Override
        public Relationships.Single linkWith(String name, Path targetOrSource, Map<String, Object> properties)
                throws IllegalArgumentException {


            if (null == name) {
                throw new IllegalArgumentException("name was null");
            }
            if (null == targetOrSource) {
                throw new IllegalArgumentException("targetOrSource was null");
            }

            return inTx(tx -> {
                BE incidenceObject;
                incidenceObject = Util.find(context.discriminator(), tx, context.sourcePath, targetOrSource);

                BE origin = tx.querySingle(context.discriminator(), context.sourcePath);
                if (origin == null) {
                    throw new EntityNotFoundException(originEntityType, Query.filters(context.select().get()));
                }

                // if this is a well-known relationship, there might be some semantic checks for it...
                RelationshipRules.checkCreate(context.discriminator(), tx, origin, direction, name, incidenceObject);

                EntityAndPendingNotifications<BE, Relationship> relationshipObject;
                EntityAndPendingNotifications<BE, Relationship> relationshipObject2 = null;

                Discriminator disc = context.discriminator();
                switch (direction) {
                    case incoming:
                        relationshipObject = Util.createAssociation(disc, tx, incidenceObject, name, origin, properties);
                        break;
                    case outgoing:
                        relationshipObject = Util.createAssociation(disc, tx, origin, name, incidenceObject, properties);
                        break;
                    case both:
                        relationshipObject2 = Util.createAssociation(disc, tx, origin, name, incidenceObject, properties);

                        relationshipObject = Util.createAssociation(disc, tx, incidenceObject, name, origin, properties);

                        break;
                    default:
                        throw new AssertionError("Unhandled direction when linking. This shouldn't have happened.");
                }

                tx.getPreCommit().addNotifications(relationshipObject);

                if (relationshipObject2 != null) {
                    tx.getPreCommit().addNotifications(relationshipObject2);
                }

                return new Single<>(context.toCreatedEntity(relationshipObject.getEntity(), true));
            });
        }

        @Override
        public void update(String id, Relationship.Update update) throws RelationNotFoundException {
            //TODO this doesn't respect the current position in the graph
            inTx(tx -> {
                try {
                    BE relationshipObject = tx.find(context.discriminator(), CanonicalPath.of().relationship(id)
                            .get());
                    tx.update(context.discriminator(), relationshipObject, update);

                    Relationship r = tx.convert(context.discriminator(), relationshipObject, Relationship.class);

                    tx.getPreCommit().addNotifications(new EntityAndPendingNotifications<>(relationshipObject, r,
                            new Notification<>(new Action.Update<>(r, update), r, updated())));

                    return null;
                } catch (ElementNotFoundException e) {
                    throw new RelationNotFoundException(id,
                            Query.filters(context.select().with(RelationWith.id(id)).get()));
                }
            });
        }

        @Override
        public void delete(String id) throws RelationNotFoundException {
            //TODO this doesn't respect the current position in the graph
            inTx((tx) -> {
                try {
                    BE relationshipObject = tx.find(context.discriminator(), CanonicalPath.of().relationship(id).get());

                    BE source = tx.getRelationshipSource(context.discriminator(), relationshipObject);
                    BE target = tx.getRelationshipTarget(context.discriminator(), relationshipObject);
                    String relationshipName = tx.extractRelationshipName(relationshipObject);

                    RelationshipRules.checkDelete(context.discriminator(), tx, source, Relationships.Direction.outgoing,
                            relationshipName, target);

                    Relationship r = tx.convert(context.discriminator(), relationshipObject, Relationship.class);

                    tx.markDeleted(context.discriminator(), relationshipObject);

                    tx.getPreCommit().addNotifications(
                            new EntityAndPendingNotifications<>(relationshipObject, r, deleted()));
                    return null;
                } catch (ElementNotFoundException e) {
                    throw new RelationNotFoundException(id,
                            Query.filters(context.select().with(RelationWith.id(id)).get()));
                }
            });
        }
    }

    public static class Read<BE> extends Traversal<BE, Relationship> implements Relationships.Read {
        private final Relationships.Direction direction;

        public Read(TraversalContext<BE, Relationship> context) {
            super(context);
            QueryFragment[] filters = context.selectCandidates.getFragments();
            if (filters.length == 0) {
                direction = Relationships.Direction.outgoing;
            } else {
                direction = ((SwitchElementType) filters[filters.length - 1].getFilter()).getDirection();
            }
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
            return new Single<>(context.proceed().where(RelationWith.id(id)).get());
        }

        @Override
        public Relationships.Multiple getAll(RelationFilter... filters) {
            return new Multiple<>(direction, context.proceed().where(filters).get());
        }
    }

    public static class Single<BE> extends Fetcher<BE, Relationship, Relationship.Update>
            implements Relationships.Single {

        public Single(TraversalContext<BE, Relationship> context) {
            super(context);
        }
    }

    public static class Multiple<BE> extends Fetcher<BE, Relationship, Relationship.Update>
            implements Relationships.Multiple {

        private final Relationships.Direction direction;

        public Multiple(Relationships.Direction direction, TraversalContext<BE, Relationship> context) {
            super(context);
            this.direction = direction;
        }

        @Override
        public Tenants.Read tenants() {
            return new BaseTenants.Read<>(context.proceedFromRelationshipsTo(direction, Tenant.class).get());
        }

        @Override
        public Environments.Read environments() {
            return new BaseEnvironments.Read<>(context.proceedFromRelationshipsTo(direction, Environment.class).get());
        }

        @Override
        public Feeds.Read feeds() {
            return new BaseFeeds.Read<>(context.proceedFromRelationshipsTo(direction, Feed.class).get());
        }

        @Override
        public MetricTypes.Read metricTypes() {
            return new BaseMetricTypes.Read<>(context.proceedFromRelationshipsTo(direction, MetricType.class).get());
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedFromRelationshipsTo(direction, Metric.class).get());
        }

        @Override
        public Resources.Read resources() {
            return new BaseResources.Read<>(context.proceedFromRelationshipsTo(direction, Resource.class).get());
        }

        @Override
        public ResourceTypes.Read resourceTypes() {
            return new BaseResourceTypes.Read<>(context.proceedFromRelationshipsTo(direction, ResourceType.class).
                    get());
        }
    }
}
