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

import com.tinkerpop.blueprints.TransactionalGraph;
import org.hawkular.inventory.api.MetricDefinitions;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Types;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.MetricDefinition;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;

import java.util.Set;

import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class TypeBrowser extends AbstractBrowser<ResourceType> {
    private TypeBrowser(TransactionalGraph graph, FilterApplicator... path) {
        super(graph, ResourceType.class, path);
    }

    public static Types.Single single(TransactionalGraph graph, FilterApplicator... path) {
        TypeBrowser b = new TypeBrowser(graph, path);

        return new Types.Single() {

            @Override
            public ResourceType entity() {
                return b.entity();
            }

            @Override
            public Relationships.ReadWrite relationships() {
                return b.relationships();
            }

            @Override
            public Resources.Read resources() {
                return b.resources();
            }

            @Override
            public MetricDefinitions.ReadRelate metricDefinitions() {
                return b.metricDefinitions();
            }
        };
    }

    public static Types.Multiple multiple(TransactionalGraph graph, FilterApplicator... path) {
        TypeBrowser b = new TypeBrowser(graph, path);

        return new Types.Multiple() {
            @Override
            public MetricDefinitions.Read metricDefinitions() {
                return b.metricDefinitions();
            }

            @Override
            public Resources.Read resources() {
                return b.resources();
            }

            @Override
            public Relationships.Read relationships() {
                return b.relationships();
            }

            @Override
            public Set<ResourceType> entities() {
                return b.entities();
            }
        };
    }

    private Resources.Read resources() {
        return new ResourcesService(graph, pathToHereWithSelect(Filter.by(Related.by(defines),
                With.type(Resource.class))));
    }

    private MetricDefinitions.ReadRelate metricDefinitions() {
        return new MetricDefinitionsService(graph, pathToHereWithSelect(Filter.by(Related.by(owns),
                With.type(MetricDefinition.class))));
    }
}
