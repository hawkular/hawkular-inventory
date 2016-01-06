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
package org.hawkular.inventory.api.model;

/**
 * A visitor interface to accept different kinds of entities available in Hawkular.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public interface ElementTypeVisitor<R, P> {

    static <R, P> R accept(Class<?> entityType, ElementTypeVisitor<R, P> visitor, P parameter) {
        return accept(SegmentType.fromElementType(entityType), visitor, parameter);
    }
    static <R, P> R accept(SegmentType entityType, ElementTypeVisitor<R, P> visitor, P parameter) {
        switch (entityType) {
            case t:
                return visitor.visitTenant(parameter);
            case e:
                return visitor.visitEnvironment(parameter);
            case f:
                return visitor.visitFeed(parameter);
            case m:
                return visitor.visitMetric(parameter);
            case mt:
                return visitor.visitMetricType(parameter);
            case r:
                return visitor.visitResource(parameter);
            case rt:
                return visitor.visitResourceType(parameter);
            case rl:
                return visitor.visitRelationship(parameter);
            case d:
                return visitor.visitData(parameter);
            case ot:
                return visitor.visitOperationType(parameter);
            case mp:
                return visitor.visitMetadataPack(parameter);
            default:
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
