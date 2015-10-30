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
public interface ElementUpdateVisitor<R, P> {
    R visitTenant(Tenant.Update tenant, P parameter);

    R visitEnvironment(Environment.Update environment, P parameter);

    R visitFeed(Feed.Update feed, P parameter);

    R visitMetric(Metric.Update metric, P parameter);

    R visitMetricType(MetricType.Update definition, P parameter);

    R visitResource(Resource.Update resource, P parameter);

    R visitResourceType(ResourceType.Update type, P parameter);

    R visitRelationship(Relationship.Update relationship, P parameter);

    R visitData(DataEntity.Update data, P parameter);

    R visitOperationType(OperationType.Update operationType, P parameter);

    R visitMetadataPack(MetadataPack.Update metadataPack, P parameter);

    R visitUnknown(Object update, P parameter);

    class Simple<R, P> implements ElementUpdateVisitor<R, P> {
        private R defaultValue;

        public Simple() {
            this(null);
        }

        public Simple(R defaultValue) {
            this.defaultValue = defaultValue;
        }

        protected R defaultAction() {
            return defaultValue;
        }

        @Override
        public R visitTenant(Tenant.Update tenant, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitEnvironment(Environment.Update environment, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitFeed(Feed.Update feed, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetric(Metric.Update metric, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetricType(MetricType.Update definition, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitResource(Resource.Update resource, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitResourceType(ResourceType.Update type, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitRelationship(Relationship.Update relationship, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitData(DataEntity.Update data, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitOperationType(OperationType.Update operationType, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetadataPack(MetadataPack.Update metadataPack, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitUnknown(Object blueprint, P parameter) {
            return defaultAction();
        }
    }
}
