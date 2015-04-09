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

import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.model.Tenant;

import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class ObservableTenants {

    private ObservableTenants() {

    }

    public static final class Read extends ObservableBase.Read<Tenants.Single, Tenants.Multiple, Tenants.Read>
            implements Tenants.Read {

        Read(Tenants.Read wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Tenants.Single, ObservableContext, ? extends Tenants.Single> singleCtor() {
            return ObservableTenants.Single::new;
        }

        @Override
        protected BiFunction<Tenants.Multiple, ObservableContext, ? extends Tenants.Multiple> multipleCtor() {
            return ObservableTenants.Multiple::new;
        }
    }

    public static final class ReadWrite
            extends ObservableBase.ReadWrite<Tenant, Tenant.Blueprint, Tenants.Single, Tenants.Multiple,
            Tenants.ReadWrite> implements Tenants.ReadWrite {

        public ReadWrite(Tenants.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Tenants.Single, ObservableContext, ? extends Tenants.Single> singleCtor() {
            return ObservableTenants.Single::new;
        }

        @Override
        protected BiFunction<Tenants.Multiple, ObservableContext, ? extends Tenants.Multiple> multipleCtor() {
            return ObservableTenants.Multiple::new;
        }
    }

    public static final class Single extends ObservableBase.RelatableSingle<Tenant, Tenants.Single>
            implements Tenants.Single {

        Single(Tenants.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableResourceTypes.ReadWrite resourceTypes() {
            return wrap(ObservableResourceTypes.ReadWrite::new, wrapped.resourceTypes());
        }

        @Override
        public ObservableMetricTypes.ReadWrite metricTypes() {
            return wrap(ObservableMetricTypes.ReadWrite::new, wrapped.metricTypes());
        }

        @Override
        public ObservableEnvironments.ReadWrite environments() {
            return wrap(ObservableEnvironments.ReadWrite::new, wrapped.environments());
        }
    }

    public static final class Multiple extends ObservableBase.RelatableMultiple<Tenant, Tenants.Multiple>
            implements Tenants.Multiple {

        Multiple(Tenants.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableResourceTypes.Read resourceTypes() {
            return wrap(ObservableResourceTypes.Read::new, wrapped.resourceTypes());
        }

        @Override
        public ObservableMetricTypes.Read metricTypes() {
            return wrap(ObservableMetricTypes.Read::new, wrapped.metricTypes());
        }

        @Override
        public ObservableEnvironments.Read environments() {
            return wrap(ObservableEnvironments.Read::new, wrapped.environments());
        }
    }
}
