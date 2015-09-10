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
 * A visitor interface to accept different kinds of entities available in Hawkular.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface ElementVisitor<R, P> {

    R visitTenant(Tenant tenant, P parameter);

    R visitEnvironment(Environment environment, P parameter);

    R visitFeed(Feed feed, P parameter);

    R visitMetric(Metric metric, P parameter);

    R visitMetricType(MetricType definition, P parameter);

    R visitResource(Resource resource, P parameter);

    R visitResourceType(ResourceType type, P parameter);

    R visitRelationship(Relationship relationship, P parameter);

    R visitData(DataEntity data, P parameter);

    R visitOperationType(OperationType operationType, P parameter);

    R visitUnknown(Object entity, P parameter);

    /**
     * A simple implementation of the EntityVisitor interface that returns a default value (provided at construction
     * time) from the visit methods.
     */
    class Simple<R, P> implements ElementVisitor<R, P> {
        private final R defaultValue;

        /**
         * Constructs a new simple entity visitor by default returning null from every visit method.
         */
        public Simple() {
            this(null);
        }

        /**
         * Constructs a new simple entity visitor by default returning the provided value from every visit method.
         *
         * @param defaultValue the default value to return
         */
        public Simple(R defaultValue) {
            this.defaultValue = defaultValue;
        }

        /**
         * The default action executed from the visit methods. This returns the default value provided at
         * the construction time.
         *
         * @return the default value
         */
        protected R defaultAction() {
            return defaultValue;
        }

        @Override
        public R visitTenant(Tenant tenant, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitEnvironment(Environment environment, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitFeed(Feed feed, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetric(Metric metric, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetricType(MetricType type, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitResource(Resource resource, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitResourceType(ResourceType type, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitRelationship(Relationship relationship, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitData(DataEntity data, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitOperationType(OperationType operationType, P parameter) {
            return defaultAction();
        }

        @Override
        public R visitUnknown(Object entity, P parameter) {
            return defaultAction();
        }
    }
}
