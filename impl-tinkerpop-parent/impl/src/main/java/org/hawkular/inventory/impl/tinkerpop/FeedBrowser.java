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

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
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
final class FeedBrowser extends AbstractBrowser<Feed, Feed.Blueprint, Feed.Update> {

    public static Feeds.Single single(InventoryContext context, FilterApplicator.Tree path) {
        FeedBrowser b = new FeedBrowser(context, path);

        return new Feeds.Single() {

            @Override
            public Feed entity() throws EntityNotFoundException, RelationNotFoundException {
                return b.entity();
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
            public Resources.ReadWrite resources() {
                return b.resources();
            }

            @Override
            public Metrics.ReadWrite metrics() {
                return b.metrics();
            }
        };
    }

    public static Feeds.Multiple multiple(InventoryContext context, FilterApplicator.Tree path) {
        FeedBrowser b = new FeedBrowser(context, path);

        return new Feeds.Multiple() {

            @Override
            public Page<Feed> entities(Pager pager) {
                return b.entities(pager);
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
            public Resources.Read resources() {
                return b.resources();
            }

            @Override
            public Metrics.Read metrics() {
                return b.metrics();
            }
        };
    }

    FeedBrowser(InventoryContext context, FilterApplicator.Tree path) {
        super(context, Feed.class, path);
    }

    public ResourcesService resources() {
        return new ResourcesService(context, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(Resource.class))));
    }

    public MetricsService metrics() {
        return new MetricsService(context, pathToHereWithSelect(Filter.by(Related.by(contains),
                With.type(Metric.class))));
    }
}
