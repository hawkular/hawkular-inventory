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
package org.hawkular.inventory.api.model;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public interface ElementBlueprintVisitor<R, P> {
    R visitTenant(Tenant.Blueprint tenant, P parameter);

    R visitEnvironment(Environment.Blueprint environment, P parameter);

    R visitFeed(Feed.Blueprint feed, P parameter);

    R visitMetric(Metric.Blueprint metric, P parameter);

    R visitMetricType(MetricType.Blueprint definition, P parameter);

    R visitResource(Resource.Blueprint resource, P parameter);

    R visitResourceType(ResourceType.Blueprint type, P parameter);

    R visitRelationship(Relationship.Blueprint relationship, P parameter);

    R visitData(DataEntity.Blueprint<?> data, P parameter);

    R visitOperationType(OperationType.Blueprint operationType, P parameter);

    R visitMetadataPack(MetadataPack.Blueprint metadataPack, P parameter);

    R visitUnknown(Object blueprint, P parameter);

    class Simple<R, P> implements ElementBlueprintVisitor<R, P> {
        private R defaultValue;

        public Simple() {
            this(null);
        }

        public Simple(R defaultValue) {
            this.defaultValue = defaultValue;
        }

        protected R defaultAction(Object blueprint, P parameter) {
            return defaultValue;
        }

        @Override
        public R visitTenant(Tenant.Blueprint tenant, P parameter) {
            return defaultAction(tenant, parameter);
        }

        @Override
        public R visitEnvironment(Environment.Blueprint environment, P parameter) {
            return defaultAction(environment, parameter);
        }

        @Override
        public R visitFeed(Feed.Blueprint feed, P parameter) {
            return defaultAction(feed, parameter);
        }

        @Override
        public R visitMetric(Metric.Blueprint metric, P parameter) {
            return defaultAction(metric, parameter);
        }

        @Override
        public R visitMetricType(MetricType.Blueprint type, P parameter) {
            return defaultAction(type, parameter);
        }

        @Override
        public R visitResource(Resource.Blueprint resource, P parameter) {
            return defaultAction(resource, parameter);
        }

        @Override
        public R visitResourceType(ResourceType.Blueprint type, P parameter) {
            return defaultAction(type, parameter);
        }

        @Override
        public R visitRelationship(Relationship.Blueprint relationship, P parameter) {
            return defaultAction(relationship, parameter);
        }

        @Override
        public R visitData(DataEntity.Blueprint<?> data, P parameter) {
            return defaultAction(data, parameter);
        }

        @Override
        public R visitOperationType(OperationType.Blueprint operationType, P parameter) {
            return defaultAction(operationType, parameter);
        }

        @Override
        public R visitMetadataPack(MetadataPack.Blueprint metadataPack, P parameter) {
            return defaultAction(metadataPack, parameter);
        }

        @Override
        public R visitUnknown(Object blueprint, P parameter) {
            return defaultAction(blueprint, parameter);
        }
    }
}
