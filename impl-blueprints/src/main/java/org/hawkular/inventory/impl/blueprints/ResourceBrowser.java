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
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;

import static org.hawkular.inventory.api.Relationships.WellKnown.owns;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class ResourceBrowser extends AbstractBrowser<Resource> implements Resources.Browser {
    ResourceBrowser(TransactionalGraph graph, Filter... path) {
        super(graph, Resource.class, path);
    }

    @Override
    public Metrics.ReadRelate metrics() {
        return new MetricsService(graph, pathToHereWithSelect(Filter.by(Related.by(owns), With.type(Metric.class))));
    }
}
