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
package org.hawkular.inventory.impl.blueprints;

import com.tinkerpop.blueprints.TransactionalGraph;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.MetricDefinitions;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.Types;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.MetricDefinition;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Set;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class TenantBrowser extends AbstractBrowser<Tenant> {
    private TenantBrowser(TransactionalGraph graph, FilterApplicator... path) {
        super(graph, Tenant.class, path);
    }

    public static Tenants.Single single(TransactionalGraph graph, FilterApplicator... path) {
        TenantBrowser b = new TenantBrowser(graph, path);

        return new Tenants.Single() {
            @Override
            public Types.ReadWrite types() {
                return b.types();
            }

            @Override
            public MetricDefinitions.ReadWrite metricDefinitions() {
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
            public Tenant entity() {
                return b.entity();
            }
        };
    }

    public static Tenants.Multiple multiple(TransactionalGraph graph, FilterApplicator... path) {
        TenantBrowser b = new TenantBrowser(graph, path);
        return new Tenants.Multiple() {
            @Override
            public Types.Read types() {
                return b.types();
            }

            @Override
            public MetricDefinitions.Read metricDefinitions() {
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
            public Set<Tenant> entities() {
                return b.entities();
            }
        };
    }

    public EnvironmentsService environments() {
        return new EnvironmentsService(graph,
                pathToHereWithSelect(Filter.by(Related.by(contains), With.type(Environment.class))));
    }

    public TypesService types() {
        return new TypesService(graph, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(ResourceType.class))));
    }

    public MetricDefinitionsService metricDefinitions() {
        return new MetricDefinitionsService(graph, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(MetricDefinition.class))));
    }
}
