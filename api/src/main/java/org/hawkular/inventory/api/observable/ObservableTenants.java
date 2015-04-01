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

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Set;
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
            return Single::new;
        }

        @Override
        protected BiFunction<Tenants.Multiple, ObservableContext, ? extends Tenants.Multiple> multipleCtor() {
            return Multiple::new;
        }
    }

    public static final class ReadWrite
            extends ObservableBase.ReadWrite<Tenant, String, Tenants.Single, Tenants.Multiple, Tenants.ReadWrite>
            implements Tenants.ReadWrite {

        public ReadWrite(Tenants.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Tenants.Single, ObservableContext, ? extends Tenants.Single> singleCtor() {
            return Single::new;
        }

        @Override
        protected BiFunction<Tenants.Multiple, ObservableContext, ? extends Tenants.Multiple> multipleCtor() {
            return Multiple::new;
        }
    }

    public static final class Single extends ObservableBase<Tenants.Single> implements Tenants.Single {

        Single(Tenants.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ResourceTypes.ReadWrite resourceTypes() {
            // return new ObservableResourceTypes.ReadWrite(wrapped.resourceTypes(), context);
            return null;
        }

        @Override
        public MetricTypes.ReadWrite metricTypes() {
            // return new ObservableMetricTypes.ReadWrite(wrapped.metricTypes(), context);
            return null;
        }

        @Override
        public Environments.ReadWrite environments() {
            // return new ObservableEnvironments.ReadWrite(wrapped.environments(), context);
            return null;
        }

        @Override
        public Relationships.ReadWrite relationships() {
            // return new ObservableRelationships.ReadWrite(wrapped.relationships(), context);
            return null;
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            // return new ObservableRelationships.ReadWrite(wrapped.relationships(direction), context);
            return null;
        }

        @Override
        public Tenant entity() {
            return wrapped.entity();
        }
    }

    public static final class Multiple extends ObservableBase<Tenants.Multiple> implements Tenants.Multiple {

        Multiple(Tenants.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ResourceTypes.Read resourceTypes() {
            // return new ObservableResourceTypes.Read(wrapped.resourceTypes(), context);
            return null;
        }

        @Override
        public MetricTypes.Read metricTypes() {
            // return new ObservableMetricTypes.Read(wrapped.metricTypes(), context);
            return null;
        }

        @Override
        public Environments.Read environments() {
            // return new ObservableEnvironments.Read(wrapped.environments(), context);
            return null;
        }

        @Override
        public Relationships.Read relationships() {
            // return new ObservableRelationships.Read(wrapped.relationships(), context);
            return null;
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            // return new ObservableRelationships.Read(wrapped.relationships(direction), context);
            return null;
        }

        @Override
        public Set<Tenant> entities() {
            return wrapped.entities();
        }
    }
}
