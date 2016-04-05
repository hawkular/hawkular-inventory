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

import static java.util.Collections.emptyList;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.Relationships.WellKnown.isParentOf;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.base.spi.RecurseFilter;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseEnvironments {

    private BaseEnvironments() {

    }

    private static <BE>
    BaseResources.Read<BE> proceedToResources(TraversalContext<BE, Environment> context,
                                              Environments.ResourceParents... parents) {
        return new BaseResources.Read<>(context.proceedWithParents(Resource.class,
                Environments.ResourceParents.class, Environments.ResourceParents.ENVIRONMENT, parents,
                (p, extender) -> {
                    switch (p) {
                        case ENVIRONMENT:
                            extender.path().with(by(contains), type(Resource.class));
                            break;
                        case FEED:
                            extender.path()
                                    .with(by(incorporates), type(Feed.class), by(contains), type(Resource.class));
                            break;
                        case RESOURCE:
                            //go to root resources, wherever they are
                            extender.path().with(new Filter[][]{
                                    {by(contains), type(Resource.class)},
                                    {by(incorporates), type(Feed.class), by(contains), type(Resource.class)}});

                            //and then go to the child resources
                            extender.path().with(RecurseFilter.builder().addChain(by(isParentOf), type(Resource
                                    .class)).build());
                            break;
                        default:
                            throw new AssertionError("Unhandled type of resource parent under environment.");
                    }
                }));
    }

    private static <BE>
    BaseMetrics.Read<BE> proceedToMetrics(TraversalContext<BE, Environment> context,
                                          Environments.MetricParents... parents) {
        return new BaseMetrics.Read<>(context.proceedWithParents(Metric.class,
                Environments.MetricParents.class, Environments.MetricParents.ENVIRONMENT, parents, (p, extender) -> {
                    switch (p) {
                        case ENVIRONMENT:
                            extender.path().with(by(contains), type(Metric.class));
                            break;
                        case FEED:
                            extender.path().with(by(incorporates), type(Feed.class), by(contains), type(Metric.class));
                            break;
                        case RESOURCE:
                            //out by all possible paths from env to a resource
                            extender.path().with(new Filter[][]{
                                    {by(contains), type(Resource.class)},
                                    {by(incorporates), type(Feed.class), by(contains), type(Resource.class)}});

                            //out to direct metrics and also metrics of child resources
                            extender.path().with(new Filter[][]{
                                    {by(contains), type(Metric.class)},
                                    {RecurseFilter.builder().addChain(by(contains), type(Resource.class)).build(),
                                        by(contains), type(Metric.class)}});
                            break;
                        default:
                            throw new AssertionError("Unhandled type of metric parent under environment.");
                    }
                }));
    }

    public static class ReadWrite<BE>
            extends Mutator<BE, Environment, Environment.Blueprint, Environment.Update, String>
            implements Environments.ReadWrite {

        public ReadWrite(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Transaction<BE> tx, Environment.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<BE, Environment>
        wireUpNewEntity(BE entity, Environment.Blueprint blueprint, CanonicalPath parentPath, BE parent,
                        Transaction<BE> tx) {
            return new EntityAndPendingNotifications<>(entity, new Environment(blueprint.getName(),
                    parentPath.extend(Environment.class, tx.extractId(entity)).get(),
                    blueprint.getProperties()), emptyList());
        }

        @Override
        public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
            //TODO implement
            throw new UnsupportedOperationException();
        }

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Environments.Single create(Environment.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class ReadContained<BE> extends Traversal<BE, Environment> implements Environments.ReadContained {

        public ReadContained(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Traversal<BE, Environment> implements Environments.Read {

        public Read(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Environments.Single get(Path id) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(id));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, Environment, Environment.Update>
            implements Environments.Single {

        public Single(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.ReadAssociate feeds() {
            return new BaseFeeds.ReadAssociate<>(context.proceedTo(incorporates, Feed.class).get());
        }

        @Override
        public Resources.ReadWrite resources() {
            return new BaseResources.ReadWrite<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.ReadWrite metrics() {
            return new BaseMetrics.ReadWrite<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public Metrics.Read metricsUnder(Environments.MetricParents... parents) {
            return proceedToMetrics(context, parents);
        }

        @Override
        public Resources.Read resourcesUnder(Environments.ResourceParents... parents) {
            return proceedToResources(context, parents);
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Environment, Environment.Update>
            implements Environments.Multiple {

        public Multiple(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.Read feeds() {
            return new BaseFeeds.Read<>(context.proceedTo(incorporates, Feed.class).get());
        }

        @Override
        public Resources.ReadContained resources() {
            return new BaseResources.ReadContained<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.ReadContained metrics() {
            return new BaseMetrics.ReadContained<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public Metrics.Read metricsUnder(Environments.MetricParents... parents) {
            return proceedToMetrics(context, parents);
        }

        @Override
        public Resources.Read resourcesUnder(Environments.ResourceParents... parents) {
            return proceedToResources(context, parents);
        }
    }
}
