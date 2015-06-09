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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.lazy.spi.CanonicalPath;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class LazyEnvironments {

    private LazyEnvironments() {

    }

    public static final class ReadWrite<BE> extends Mutator<BE, Environment, Environment.Blueprint, Environment.Update>
            implements Environments.ReadWrite {

        public ReadWrite(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Environment.Blueprint entity) {
            return entity.getId();
        }

        @Override
        protected NewEntityAndPendingNotifications<Environment> wireUpNewEntity(BE entity,
                Environment.Blueprint blueprint, CanonicalPath parentPath,
                BE parent) {
            return new NewEntityAndPendingNotifications<>(new Environment(parentPath.getTenantId(),
                    context.backend.extractId(entity), blueprint.getProperties()));
        }

        @Override
        public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
            //TODO implement
            throw new UnsupportedOperationException();
        }

        @Override
        public Environments.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
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

    public static final class Read<BE> extends Traversal<BE, Environment> implements Environments.Read {

        public Read(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Environments.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static final class Single<BE> extends SingleEntityFetcher<BE, Environment> implements Environments.Single {

        public Single(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.ReadWrite feeds() {
            return new LazyFeeds.ReadWrite<>(context.proceedTo(contains, Feed.class).get());
        }

        @Override
        public Resources.ReadWrite feedlessResources() {
            return new LazyResources.ReadWrite<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.ReadWrite feedlessMetrics() {
            return new LazyMetrics.ReadWrite<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            return new LazyResources.Read<>(context.proceed().where(new Filter[][]{
                    {by(contains), type(Resource.class)},
                    {by(contains), type(Feed.class), by(contains), type(Resource.class)}
            }).getting(Resource.class));
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            return new LazyMetrics.Read<>(context.proceed().where(new Filter[][]{
                    {by(contains), type(Metric.class)},
                    {by(contains), type(Feed.class), by(contains), type(Metric.class)}
            }).getting(Metric.class));
        }
    }

    public static final class Multiple<BE> extends MultipleEntityFetcher<BE, Environment>
            implements Environments.Multiple {

        public Multiple(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.Read feeds() {
            return new LazyFeeds.Read<>(context.proceedTo(contains, Feed.class).get());
        }

        @Override
        public Resources.Read feedlessResources() {
            return new LazyResources.Read<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.Read feedlessMetrics() {
            return new LazyMetrics.Read<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            return new LazyResources.Read<>(context.proceed().where(new Filter[][]{
                    {by(contains), type(Resource.class)},
                    {by(contains), type(Feed.class), by(contains), type(Resource.class)}
            }).getting(Resource.class));
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            return new LazyMetrics.Read<>(context.proceed().where(new Filter[][]{
                    {by(contains), type(Metric.class)},
                    {by(contains), type(Feed.class), by(contains), type(Metric.class)}
            }).getting(Metric.class));
        }
    }
}
