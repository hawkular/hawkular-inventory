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
 * @since 0.0.1
 */
public interface ElementTypeVisitor<R, P> {

    static <P, R> R accept(ElementTypeVisitor<R, P> visitor, Class<? extends AbstractElement<?, ?>> elementType,
            P parameter) {

        if (Tenant.class.isAssignableFrom(elementType)) {
            return visitor.visitTenant(parameter);
        } else if (Environment.class.isAssignableFrom(elementType)) {
            return visitor.visitEnvironment(parameter);
        } else if (ResourceType.class.isAssignableFrom(elementType)) {
            return visitor.visitResourceType(parameter);
        } else if (MetricType.class.isAssignableFrom(elementType)) {
            return visitor.visitMetricType(parameter);
        } else if (Feed.class.isAssignableFrom(elementType)) {
            return visitor.visitFeed(parameter);
        } else if (Resource.class.isAssignableFrom(elementType)) {
            return visitor.visitResource(parameter);
        } else if (Metric.class.isAssignableFrom(elementType)) {
            return visitor.visitMetric(parameter);
        } else if (Relationship.class.isAssignableFrom(elementType)) {
            return visitor.visitRelationship(parameter);
        } else {
            throw new IllegalArgumentException("Unknown type of inventory element: " + elementType);
        }
    }

    R visitTenant(P parameter);

    R visitEnvironment(P parameter);

    R visitResourceType(P parameter);

    R visitMetricType(P parameter);

    R visitFeed(P parameter);

    R visitResource(P parameter);

    R visitMetric(P parameter);

    R visitRelationship(P parameter);

    class Simple<R, P> implements ElementTypeVisitor<R, P> {

        private final R defaultValue;

        public Simple() {
            defaultValue = null;
        }

        public Simple(R defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public R visitTenant(P parameter) {
            return defaultValue;
        }

        @Override
        public R visitEnvironment(P parameter) {
            return defaultValue;
        }

        @Override
        public R visitResourceType(P parameter) {
            return defaultValue;
        }

        @Override
        public R visitMetricType(P parameter) {
            return defaultValue;
        }

        @Override
        public R visitFeed(P parameter) {
            return defaultValue;
        }

        @Override
        public R visitResource(P parameter) {
            return defaultValue;
        }

        @Override
        public R visitMetric(P parameter) {
            return defaultValue;
        }

        @Override
        public R visitRelationship(P parameter) {
            return defaultValue;
        }
    }
}
