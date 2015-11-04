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

import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.With.id;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.ResourceType;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseMetricTypes {

    private BaseMetricTypes() {

    }

    public static class ReadWrite<BE>
            extends Mutator<BE, MetricType, MetricType.Blueprint, MetricType.Update, String>
            implements MetricTypes.ReadWrite {

        public ReadWrite(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        protected String getProposedId(MetricType.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<MetricType> wireUpNewEntity(BE entity, MetricType.Blueprint blueprint,
                CanonicalPath parentPath, BE parent) {
            context.backend.update(entity, MetricType.Update.builder().withUnit(blueprint.getUnit()).build());

            return new EntityAndPendingNotifications<>(new MetricType(blueprint.getName(),
                    parentPath.extend(MetricType.class, context.backend.extractId(entity)).get(),
                    blueprint.getUnit(), blueprint.getType(), blueprint.getProperties()));
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
            if (blueprint.getType() == null || blueprint.getUnit() == null) {
                String msg = (blueprint.getType() == null ? "Data type" : "Metric unit") + " is null";
                throw new IllegalArgumentException(msg);
            }

            return new BaseMetricTypes.Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class ReadContained<BE> extends Fetcher<BE, MetricType, MetricType.Update>
            implements MetricTypes.ReadContained {

        public ReadContained(TraversalContext<BE, MetricType> context) {
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

    public static class Read<BE> extends Fetcher<BE, MetricType, MetricType.Update> implements MetricTypes.Read {

        public Read(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(Path id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceedTo(id));
        }
    }

    public static class ReadAssociate<BE> extends Associator<BE, MetricType>
            implements MetricTypes.ReadAssociate {

        public ReadAssociate(TraversalContext<BE, MetricType> context) {
            super(context, incorporates, ResourceType.class);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(Path id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceedTo(id));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, MetricType, MetricType.Update>
            implements MetricTypes.Single {

        public Single(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedTo(defines, Metric.class).get());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, MetricType, MetricType.Update>
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
