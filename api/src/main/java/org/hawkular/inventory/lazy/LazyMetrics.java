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

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.TenantBasedEntity;
import org.hawkular.inventory.lazy.NewEntityAndPendingNotifications.Notification;
import org.hawkular.inventory.lazy.spi.CanonicalPath;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.api.filters.Related.asTargetBy;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class LazyMetrics {

    private LazyMetrics() {

    }

    public static final class ReadWrite<BE> extends Mutator<BE, Metric, Metric.Blueprint, Metric.Update>
            implements Metrics.ReadWrite {

        public ReadWrite(TraversalContext<BE, Metric> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Metric.Blueprint entity) {
            return entity.getId();
        }

        @Override
        protected NewEntityAndPendingNotifications<Metric> wireUpNewEntity(BE entity, Metric.Blueprint blueprint,
                CanonicalPath parentPath, BE parent) {

            Class<? extends AbstractElement<?, ?>> parentType = context.backend.getType(parent);

            @SuppressWarnings("unchecked")
            TenantBasedEntity<?, ?> parentEntity = context.backend.convert(parent,
                    (Class<? extends TenantBasedEntity<?, ?>>) parentType);

            BE metricTypeObject = context.backend.find(CanonicalPath.builder().withTenantId(parentEntity.getTenantId())
                    .withMetricTypeId(blueprint.getMetricTypeId()).build());

            BE r = context.backend.relate(metricTypeObject, entity, defines.name(), null);

            MetricType metricType = context.backend.convert(metricTypeObject, MetricType.class);

            Metric ret = new Metric(parentPath.getTenantId(), parentPath.getEnvironmentId(), parentPath.getFeedId(),
                    context.backend.extractId(entity), metricType, blueprint.getProperties());

            Relationship rel = new Relationship(context.backend.extractId(r), defines.name(), metricType, ret);

            return new NewEntityAndPendingNotifications<>(ret, new Notification<>(rel, rel, created()));
        }

        @Override
        public Metrics.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Metrics.Single create(Metric.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static final class Read<BE> extends Traversal<BE, Metric> implements Metrics.Read {

        public Read(TraversalContext<BE, Metric> context) {
            super(context);
        }

        @Override
        public Metrics.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static final class ReadAssociate<BE> extends Associator<BE, Metric> implements Metrics.ReadAssociate {

        public ReadAssociate(TraversalContext<BE, Metric> context) {
            super(context);
        }

        @Override
        public Relationship associate(String id) throws EntityNotFoundException, RelationAlreadyExistsException {
            //the sourcePath is a resource - that is the only way the API allows this method
            //to be called.
            //to get from the resource to the metric in question, we first go out of the metric to its owner
            //then down again to metrics and finally filter by the id.
            QueryFragmentTree getMetric = context.sourcePath.extend().path().with(asTargetBy(contains), by(contains),
                    type(Metric.class), id(id)).get();

            BE metric = getSingle(getMetric, Metric.class);

            return createAssociation(Resource.class, owns, metric);
        }

        @Override
        public Relationship disassociate(String id) throws EntityNotFoundException {
            QueryFragmentTree getMetric = context.sourcePath.extend().path().with(asTargetBy(contains), by(contains),
                    type(Metric.class), id(id)).get();

            BE metric = getSingle(getMetric, Metric.class);

            return deleteAssociation(Resource.class, owns, Metric.class, metric);
        }

        @Override
        public Relationship associationWith(String id) throws RelationNotFoundException {
            return getAssociation(Resource.class, id, Metric.class, owns);
        }

        @Override
        public Metrics.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static final class Single<BE> extends SingleEntityFetcher<BE, Metric> implements Metrics.Single {

        public Single(TraversalContext<BE, Metric> context) {
            super(context);
        }
    }

    public static final class Multiple<BE> extends MultipleEntityFetcher<BE, Metric> implements Metrics.Multiple {

        public Multiple(TraversalContext<BE, Metric> context) {
            super(context);
        }
    }
}
