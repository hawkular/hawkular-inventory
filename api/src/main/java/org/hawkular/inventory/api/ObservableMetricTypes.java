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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;

import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class ObservableMetricTypes {
    private ObservableMetricTypes() {

    }

    static final class Read
            extends ObservableBase.Read<MetricTypes.Single, MetricTypes.Multiple, MetricTypes.Read>
            implements MetricTypes.Read {

        Read(MetricTypes.Read wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<MetricTypes.Single, ObservableContext, ? extends MetricTypes.Single> singleCtor() {
            return ObservableMetricTypes.Single::new;
        }

        @Override
        protected BiFunction<MetricTypes.Multiple, ObservableContext, ? extends MetricTypes.Multiple> multipleCtor() {
            return ObservableMetricTypes.Multiple::new;
        }
    }

    static final class ReadWrite
            extends ObservableBase.ReadWrite<MetricType, MetricType.Blueprint, MetricType.Update, MetricTypes.Single,
            MetricTypes.Multiple, MetricTypes.ReadWrite> implements MetricTypes.ReadWrite {

        ReadWrite(MetricTypes.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<MetricTypes.Single, ObservableContext, ? extends MetricTypes.Single> singleCtor() {
            return ObservableMetricTypes.Single::new;
        }

        @Override
        protected BiFunction<MetricTypes.Multiple, ObservableContext, ? extends MetricTypes.Multiple> multipleCtor() {
            return ObservableMetricTypes.Multiple::new;
        }
    }

    static final class ReadAssociate
            extends ObservableBase.Read<MetricTypes.Single, MetricTypes.Multiple, MetricTypes.ReadAssociate>
            implements MetricTypes.ReadAssociate {

        ReadAssociate(MetricTypes.ReadAssociate wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<MetricTypes.Single, ObservableContext, ? extends MetricTypes.Single> singleCtor() {
            return ObservableMetricTypes.Single::new;
        }

        @Override
        protected BiFunction<MetricTypes.Multiple, ObservableContext, ? extends MetricTypes.Multiple> multipleCtor() {
            return ObservableMetricTypes.Multiple::new;
        }

        @Override
        public Relationship associate(String id) {
            Relationship ret = wrapped.associate(id);
            notify(ret, Action.created());
            return ret;
        }

        @Override
        public Relationship disassociate(String id) {
            Relationship ret = wrapped.associate(id);
            notify(ret, Action.deleted());
            return ret;
        }

        @Override
        public Relationship associationWith(String id) throws RelationNotFoundException {
            return wrapped.associationWith(id);
        }
    }

    static final class Single extends ObservableBase.RelatableSingle<MetricType, MetricTypes.Single>
            implements MetricTypes.Single {

        Single(MetricTypes.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableMetrics.Read metrics() {
            return wrap(ObservableMetrics.Read::new, wrapped.metrics());
        }
    }

    static final class Multiple extends ObservableBase.RelatableMultiple<MetricType, MetricTypes.Multiple>
            implements MetricTypes.Multiple {

        Multiple(MetricTypes.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableMetrics.Read metrics() {
            return wrap(ObservableMetrics.Read::new, wrapped.metrics());
        }
    }
}
