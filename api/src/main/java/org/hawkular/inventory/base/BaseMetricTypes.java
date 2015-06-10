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

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.base.spi.CanonicalPath;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.api.filters.Related.asTargetBy;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseMetricTypes {

    private BaseMetricTypes() {

    }

    public static class ReadWrite<BE>
            extends Mutator<BE, MetricType, MetricType.Blueprint, MetricType.Update>
            implements MetricTypes.ReadWrite {

        public ReadWrite(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        protected String getProposedId(MetricType.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected NewEntityAndPendingNotifications<MetricType> wireUpNewEntity(BE entity,
                MetricType.Blueprint blueprint, CanonicalPath parentPath,
                BE parent) {
            context.backend.update(entity, MetricType.Update.builder().withUnit(blueprint.getUnit()).build());

            return new NewEntityAndPendingNotifications<>(new MetricType(parentPath.getTenantId(),
                    context.backend.extractId(entity), blueprint.getUnit(), blueprint.getProperties()));
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public MetricTypes.Single create(MetricType.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new BaseMetricTypes.Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class Read<BE> extends Fetcher<BE, MetricType> implements MetricTypes.Read {

        public Read(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class ReadAssociate<BE> extends Associator<BE, MetricType>
            implements MetricTypes.ReadAssociate {

        public ReadAssociate(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public Relationship associate(String id) throws EntityNotFoundException, RelationAlreadyExistsException {
            //the sourcePath is a resource type - that is the only way the API allows this method
            //to be called.
            //to get from the resource type to the metric type in question, we first go out of the metric types to
            //the owner then down again to metric types and finally filter by the id.
            Query getMetric = context.sourcePath.extend().path().with(asTargetBy(contains), by(contains),
                    type(MetricType.class), id(id)).get();

            BE metric = getSingle(getMetric, MetricType.class);

            return createAssociation(ResourceType.class, owns, metric);
        }

        @Override
        public Relationship disassociate(String id) throws EntityNotFoundException {
            Query getMetric = context.sourcePath.extend().path().with(asTargetBy(contains), by(contains),
                    type(MetricType.class), id(id)).get();

            BE metric = getSingle(getMetric, MetricType.class);

            return deleteAssociation(ResourceType.class, owns, MetricType.class, metric);
        }

        @Override
        public Relationship associationWith(String id) throws RelationNotFoundException {
            return getAssociation(ResourceType.class, id, MetricType.class, owns);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, MetricType> implements MetricTypes.Single {

        public Single(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedTo(defines, Metric.class).get());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, MetricType>
            implements MetricTypes.Multiple {

        public Multiple(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedTo(defines, Metric.class).get());
        }
    }

}
