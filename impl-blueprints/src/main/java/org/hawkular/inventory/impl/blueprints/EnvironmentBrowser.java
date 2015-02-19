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
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class EnvironmentBrowser extends AbstractBrowser<Environment> implements Environments.Browser {
    EnvironmentBrowser(TransactionalGraph graph, Filter... path) {
        super(graph, Environment.class, path);
    }

    @Override
    public Feeds.ReadAndRegister feeds() {
        return new FeedsService(graph, pathToHereWithSelect(Filter.by(Related.by(contains), With.type(Feed.class))));
    }

    @Override
    public Resources.ReadWrite resources() {
        return new ResourcesService(graph, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(Resource.class))));
    }

    @Override
    public Metrics.ReadWrite metrics() {
        return new MetricsService(graph, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(Metric.class))));
    }
}
