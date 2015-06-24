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
package org.hawkular.inventory.base;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseFeeds {

    private BaseFeeds() {

    }

    public static class ReadWrite<BE> extends Mutator<BE, Feed, Feed.Blueprint, Feed.Update>
            implements Feeds.ReadWrite {

        public ReadWrite(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Feed.Blueprint blueprint) {
            Page<BE> envs = context.backend.query(context.sourcePath.extend().filter().with(type(Environment.class))
                    .get(), Pager.single());

            if (envs.isEmpty()) {
                throw new EntityNotFoundException(Environment.class, Query.filters(context.sourcePath));
            }

            BE envObject = envs.get(0);

            Environment env = context.backend.convert(envObject, Environment.class);

            String envId = env.getId();
            String tenantId = env.getTenantId();

            return context.configuration.getFeedIdStrategy().generate(context.inventory,
                    new Feed(CanonicalPath.of().tenant(tenantId).environment(envId).feed(blueprint.getId()).get()));
        }

        @Override
        protected EntityAndPendingNotifications<Feed> wireUpNewEntity(BE entity, Feed.Blueprint blueprint,
                CanonicalPath parentPath, BE parent) {
            return new EntityAndPendingNotifications<>(new Feed(parentPath.extend(Feed.class,
                    context.backend.extractId(entity)).get(), blueprint.getProperties()));
        }

        @Override
        public Feeds.Single create(Feed.Blueprint blueprint) {
            return new Single<>(context.replacePath(doCreate(blueprint)));
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

    public static class ReadContained<BE> extends Fetcher<BE, Feed> implements Feeds.ReadContained {

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

    public static class Read<BE> extends Fetcher<BE, Feed> implements Feeds.Read {

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

    public static class Single<BE> extends SingleEntityFetcher<BE, Feed> implements Feeds.Single {

        public Single(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        public Resources.ReadWrite resources() {
            return new BaseResources.ReadWrite<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.ReadWrite metrics() {
            return new BaseMetrics.ReadWrite<>(context.proceedTo(contains, Metric.class).get());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Feed> implements Feeds.Multiple {

        public Multiple(TraversalContext<BE, Feed> context) {
            super(context);
        }

        @Override
        public Resources.ReadContained resources() {
            return new BaseResources.ReadContained<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.ReadContained metrics() {
            return new BaseMetrics.ReadContained<>(context.proceedTo(contains, Metric.class).get());
        }
    }
}
