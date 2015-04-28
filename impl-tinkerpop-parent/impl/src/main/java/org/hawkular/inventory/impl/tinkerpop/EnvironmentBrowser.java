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
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class EnvironmentBrowser extends AbstractBrowser<Environment, Environment.Blueprint, Environment.Update> {

    private EnvironmentBrowser(InventoryContext context, FilterApplicator.Tree path) {
        super(context, Environment.class, path);
    }

    public static Environments.Single single(InventoryContext context, FilterApplicator.Tree path) {
        EnvironmentBrowser b = new EnvironmentBrowser(context, path);
        return new Environments.Single() {
            @Override
            public Feeds.ReadWrite feeds() {
                return b.feeds();
            }

            @Override
            public Resources.ReadWrite feedlessResources() {
                return b.resources();
            }

            @Override
            public Metrics.ReadWrite feedlessMetrics() {
                return b.metrics();
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
            public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
                return b.allMetrics();
            }

            @Override
            public ResolvingToMultiple<Resources.Multiple> allResources() {
                return b.allResources();
            }

            @Override
            public Environment entity() {
                return b.entity();
            }
        };
    }

    public static Environments.Multiple multiple(InventoryContext context, FilterApplicator.Tree path) {
        EnvironmentBrowser b = new EnvironmentBrowser(context, path);
        return new Environments.Multiple() {
            @Override
            public Feeds.Read feeds() {
                return b.feeds();
            }

            @Override
            public Resources.Read feedlessResources() {
                return b.resources();
            }

            @Override
            public Metrics.Read feedlessMetrics() {
                return b.metrics();
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
            public Page<Environment> entities(Pager pager) {
                return b.entities(pager);
            }

            @Override
            public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
                return b.allMetrics();
            }

            @Override
            public ResolvingToMultiple<Resources.Multiple> allResources() {
                return b.allResources();
            }
        };
    }

    public FeedsService feeds() {
        return new FeedsService(context, pathToHereWithSelect(Filter.by(Related.by(contains), With.type(Feed.class))));
    }

    public ResourcesService resources() {
        return new ResourcesService(context, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(Resource.class))));
    }

    public MetricsService metrics() {
        return new MetricsService(context, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(Metric.class))));
    }

    public ResourcesService allResources() {
        return new ResourcesService(context, pathToHereWithSelects(
                Filter.by(Related.by(contains), With.type(Resource.class)),
                Filter.by(Related.by(contains), With.type(Feed.class), Related.by(contains),
                        With.type(Resource.class))));
    }

    public MetricsService allMetrics() {
        return new MetricsService(context, pathToHereWithSelects(
                Filter.by(Related.by(contains), With.type(Metric.class)),
                Filter.by(Related.by(contains), With.type(Feed.class), Related.by(contains),
                        With.type(Metric.class))));
    }
}
