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
 * @since 0.1.0
 */
public interface ElementTypeVisitor<R, P> {

    static <R, P> R accept(Class<?> entityType, ElementTypeVisitor<R, P> visitor, P parameter) {
        if (Tenant.class.equals(entityType)) {
            return visitor.visitTenant(parameter);
        } else if (Environment.class.equals(entityType)) {
            return visitor.visitEnvironment(parameter);
        } else if (Feed.class.equals(entityType)) {
            return visitor.visitFeed(parameter);
        } else if (Metric.class.equals(entityType)) {
            return visitor.visitMetric(parameter);
        } else if (MetricType.class.equals(entityType)) {
            return visitor.visitMetricType(parameter);
        } else if (Resource.class.equals(entityType)) {
            return visitor.visitResource(parameter);
        } else if (ResourceType.class.equals(entityType)) {
            return visitor.visitResourceType(parameter);
        } else if (Relationship.class.equals(entityType)) {
            return visitor.visitRelationship(parameter);
        } else if (DataEntity.class.equals(entityType)) {
            return visitor.visitData(parameter);
        } else if (OperationType.class.equals(entityType)) {
            return visitor.visitOperationType(parameter);
        } else if (MetadataPack.class.equals(entityType)) {
            return visitor.visitMetadataPack(parameter);
        } else {
            return visitor.visitUnknown(parameter);
        }
    }

    R visitTenant(P parameter);

    R visitEnvironment(P parameter);

    R visitFeed(P parameter);

    R visitMetric(P parameter);

    R visitMetricType(P parameter);

    R visitResource(P parameter);

    R visitResourceType(P parameter);

    R visitRelationship(P parameter);

    R visitData(P parameter);

    R visitOperationType(P parameter);

    R visitMetadataPack(P parameter);

    R visitUnknown(P parameter);

    /**
     * A simple implementation of the EntityVisitor interface that returns a default value (provided at construction
     * time) from the visit methods.
     */
    class Simple<R, P> implements ElementTypeVisitor<R, P> {
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
        public R visitTenant(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitEnvironment(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitFeed(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetric(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetricType(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitResource(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitResourceType(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitRelationship(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitData(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitOperationType(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitMetadataPack(P parameter) {
            return defaultAction();
        }

        @Override
        public R visitUnknown(P parameter) {
            return defaultAction();
        }
    }
}
