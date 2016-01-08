/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.base;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.base.spi.RecurseFilter;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseFeeds {

    private BaseFeeds() {

    }

    public static class ReadWrite<BE> extends Mutator<BE, Feed, Feed.Blueprint, Feed.Update, String>
            implements Feeds.ReadWrite {

        public ReadWrite(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Feed.Blueprint blueprint) {
            BE tenant = context.backend.querySingle(context.sourcePath.extend().filter()
                    .with(type(Tenant.class)).get());

            if (tenant == null) {
                throw new EntityNotFoundException(Tenant.class, Query.filters(context.sourcePath));
            }

            CanonicalPath feedPath = context.backend.extractCanonicalPath(tenant)
                    .extend(Feed.class, blueprint.getId()).get();

            return context.configuration.getFeedIdStrategy().generate(context.inventory, new Feed(feedPath));
        }

        @Override
        protected EntityAndPendingNotifications<Feed> wireUpNewEntity(BE entity, Feed.Blueprint blueprint,
                                                                      CanonicalPath parentPath, BE parent,
                                                                      InventoryBackend.Transaction transaction) {
            return new EntityAndPendingNotifications<>(new Feed(blueprint.getName(), parentPath.extend(Feed.class,
                    context.backend.extractId(entity)).get(), blueprint.getProperties()));
        }

        @Override
        public Feeds.Single create(Feed.Blueprint blueprint) {
            return new Single<>(context.toCreatedEntity(doCreate(blueprint)));
        }

        @Override
        public Feeds.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Feeds.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class ReadContained<BE> extends Fetcher<BE, Feed, Feed.Update> implements Feeds.ReadContained {

        public ReadContained(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        public Feeds.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Feeds.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Fetcher<BE, Feed, Feed.Update> implements Feeds.Read {

        public Read(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        public Feeds.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Feeds.Single get(Path id) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(id));
        }
    }

    public static class ReadAssociate<BE> extends Associator<BE, Feed> implements Feeds.ReadAssociate {

        public ReadAssociate(TraversalContext<BE, Feed> context) {
            super(context, incorporates, Environment.class);
        }

        @Override
        public Feeds.Multiple getAll(Filter[][] filters) {
            return new BaseFeeds.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Feeds.Single get(Path path) throws EntityNotFoundException {
            return new BaseFeeds.Single<>(context.proceedTo(path));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, Feed, Feed.Update> implements Feeds.Single {

        public Single(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        public Resources.ReadWrite resources() {
            return new BaseResources.ReadWrite<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Resources.Read resourcesUnder(Feeds.ResourceParents... parents) {
            return proceedToResources(context, parents);
        }

        @Override
        public Metrics.ReadWrite metrics() {
            return new BaseMetrics.ReadWrite<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public Metrics.Read metricsUnder(Feeds.MetricParents... parents) {
            return proceedToMetrics(context, parents);
        }

        @Override
        public MetricTypes.ReadWrite metricTypes() {
            return new BaseMetricTypes.ReadWrite<>(context.proceedTo(contains, MetricType.class).get());
        }

        @Override
        public ResourceTypes.ReadWrite resourceTypes() {
            return new BaseResourceTypes.ReadWrite<>(context.proceedTo(contains, ResourceType.class).get());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Feed, Feed.Update> implements Feeds.Multiple {

        public Multiple(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        public Resources.ReadContained resources() {
            return new BaseResources.ReadContained<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Resources.Read resourcesUnder(Feeds.ResourceParents... parents) {
            return proceedToResources(context, parents);
        }

        @Override public Metrics.ReadContained metrics() {
            return new BaseMetrics.ReadContained<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override public Metrics.Read metricsUnder(Feeds.MetricParents... parents) {
            return proceedToMetrics(context, parents);
        }

        @Override
        public MetricTypes.ReadContained metricTypes() {
            return new BaseMetricTypes.ReadContained<>(context.proceedTo(contains, MetricType.class).get());
        }

        @Override
        public ResourceTypes.ReadContained resourceTypes() {
            return new BaseResourceTypes.ReadContained<>(context.proceedTo(contains, ResourceType.class).get());
        }
    }

    private static <BE>
    BaseResources.Read<BE> proceedToResources(TraversalContext<BE, Feed> context, Feeds.ResourceParents... parents) {
        return new BaseResources.Read<>(context.proceedWithParents(Resource.class,
                Feeds.ResourceParents.class, Feeds.ResourceParents.FEED, parents, (p, extender) -> {
                    switch (p) {
                        case FEED:
                            extender.path().with(by(contains), type(Resource.class));
                            break;
                        case RESOURCE:
                            extender.path().with(by(contains), type(Resource.class), RecurseFilter.builder()
                                    .addChain(by(contains), type(Resource.class)).build());
                            break;
                        default:
                            throw new AssertionError("Unhandled type of resource parent under feed.");
                    }
                }));
    }

    private static <BE>
    BaseMetrics.Read<BE> proceedToMetrics(TraversalContext<BE, Feed> context, Feeds.MetricParents... parents) {
        return new BaseMetrics.Read<>(context.proceedWithParents(Metric.class,
                Feeds.MetricParents.class, Feeds.MetricParents.FEED, parents, (p, extender) -> {
                    switch (p) {
                        case FEED:
                            extender.path().with(by(contains), type(Metric.class));
                            break;
                        case RESOURCE:
                            //go to the root resources and their children
                            extender.path().with(new Filter[][]{
                                    {by(contains), type(Resource.class)},
                                    {by(contains), type(Resource.class), RecurseFilter.builder().addChain(
                                            by(contains), type(Resource.class)).build()}});

                            //and from the resources, go to the metrics
                            extender.path().with(by(contains), type(Metric.class));
                            break;
                        default:
                            throw new AssertionError("Unhandled type of resource parent under feed.");
                    }
                }));
    }
}
