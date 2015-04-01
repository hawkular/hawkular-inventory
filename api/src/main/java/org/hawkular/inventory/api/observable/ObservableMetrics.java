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
package org.hawkular.inventory.api.observable;

import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Relationship;

import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class ObservableMetrics {
    private ObservableMetrics() {

    }

    public static final class Read extends ObservableBase.Read<Metrics.Single, Metrics.Multiple, Metrics.Read>
        implements Metrics.Read {

        Read(Metrics.Read wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Metrics.Single, ObservableContext, ? extends Metrics.Single> singleCtor() {
            return ObservableMetrics.Single::new;
        }

        @Override
        protected BiFunction<Metrics.Multiple, ObservableContext, ? extends Metrics.Multiple> multipleCtor() {
            return ObservableMetrics.Multiple::new;
        }
    }

    public static final class ReadAssociate
            extends ObservableBase.Read<Metrics.Single, Metrics.Multiple, Metrics.ReadAssociate>
            implements Metrics.ReadAssociate {

        ReadAssociate(Metrics.ReadAssociate wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Metrics.Single, ObservableContext, ? extends Metrics.Single> singleCtor() {
            return ObservableMetrics.Single::new;
        }

        @Override
        protected BiFunction<Metrics.Multiple, ObservableContext, ? extends Metrics.Multiple> multipleCtor() {
            return ObservableMetrics.Multiple::new;
        }

        @Override
        public Relationship associate(String id) {
            return wrapped.associate(id);
        }

        @Override
        public void disassociate(String id) {
            wrapped.disassociate(id);
        }

        @Override
        public Relationship associationWith(String id) throws RelationNotFoundException {
            return wrapped.associationWith(id);
        }
    }

    public static final class ReadWrite
        extends ObservableBase.ReadWrite<Metric, Metric.Blueprint, Metrics.Single, Metrics.Multiple, Metrics.ReadWrite>
        implements Metrics.ReadWrite {

        ReadWrite(Metrics.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Metrics.Single, ObservableContext, ? extends Metrics.Single> singleCtor() {
            return ObservableMetrics.Single::new;
        }

        @Override
        protected BiFunction<Metrics.Multiple, ObservableContext, ? extends Metrics.Multiple> multipleCtor() {
            return ObservableMetrics.Multiple::new;
        }
    }

    public static final class Single extends ObservableBase.RelatableSingle<Metric, Metrics.Single>
            implements Metrics.Single {

        Single(Metrics.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }
    }

    public static final class Multiple extends ObservableBase.RelatableMultiple<Metric, Metrics.Multiple>
            implements Metrics.Multiple {

        Multiple(Metrics.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }
    }
}
