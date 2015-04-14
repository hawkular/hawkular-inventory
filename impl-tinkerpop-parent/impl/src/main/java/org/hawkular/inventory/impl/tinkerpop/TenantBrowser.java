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
package org.hawkular.inventory.impl.tinkerpop;

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Set;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class TenantBrowser extends AbstractBrowser<Tenant, Tenant.Blueprint, Tenant.Update> {
    private TenantBrowser(InventoryContext context, FilterApplicator... path) {
        super(context, Tenant.class, path);
    }

    public static Tenants.Single single(InventoryContext context, FilterApplicator... path) {
        TenantBrowser b = new TenantBrowser(context, path);

        return new Tenants.Single() {
            @Override
            public ResourceTypes.ReadWrite resourceTypes() {
                return b.types();
            }

            @Override
            public MetricTypes.ReadWrite metricTypes() {
                return b.metricDefinitions();
            }

            @Override
            public Environments.ReadWrite environments() {
                return b.environments();
            }

            @Override
            public Relationships.ReadWrite relationships() {
                return b.relationships();
            }

            @Override
            public Relationships.ReadWrite relationships(Relationships.Direction direction) {
                return b.relationships(direction);
            }

            @Override
            public Tenant entity() {
                return b.entity();
            }
        };
    }

    public static Tenants.Multiple multiple(InventoryContext context, FilterApplicator... path) {
        TenantBrowser b = new TenantBrowser(context, path);
        return new Tenants.Multiple() {
            @Override
            public ResourceTypes.Read resourceTypes() {
                return b.types();
            }

            @Override
            public MetricTypes.Read metricTypes() {
                return b.metricDefinitions();
            }

            @Override
            public Environments.Read environments() {
                return b.environments();
            }

            @Override
            public Relationships.Read relationships() {
                return b.relationships();
            }

            @Override
            public Relationships.Read relationships(Relationships.Direction direction) {
                return b.relationships(direction);
            }

            @Override
            public Set<Tenant> entities() {
                return b.entities();
            }
        };
    }

    public EnvironmentsService environments() {
        return new EnvironmentsService(context,
                pathToHereWithSelect(Filter.by(Related.by(contains), With.type(Environment.class))));
    }

    public ResourceTypesService types() {
        return new ResourceTypesService(context, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(ResourceType.class))));
    }

    public MetricTypesService metricDefinitions() {
        return new MetricTypesService(context, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(MetricType.class))));
    }
}
