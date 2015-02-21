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
 * @since 1.0
 */
public interface EntityVisitor<R, P> {

    R visitTenant(Tenant tenant, P parameter);

    R visitEnvironment(Environment environment, P parameter);

    R visitFeed(Feed feed, P parameter);

    R visitMetric(Metric metric, P parameter);

    R visitMetricType(MetricType definition, P parameter);

    R visitResource(Resource resource, P parameter);

    R visitResourceType(ResourceType type, P parameter);

    public static class Simple<R, P> implements EntityVisitor<R, P> {
        private final R defaultValue;

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
        public R visitMetricType(MetricType definition, P parameter) {
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
    }
}
