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
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseTenants {

    private BaseTenants() {

    }

    public static class ReadWrite<BE> extends Mutator<BE, Tenant, Tenant.Blueprint, Tenant.Update, String>
            implements Tenants.ReadWrite {

        public ReadWrite(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Tenant.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<Tenant> wireUpNewEntity(BE entity, Tenant.Blueprint blueprint,
                CanonicalPath parentPath, BE parent) {

            return new EntityAndPendingNotifications<>(new Tenant(CanonicalPath.of()
                    .tenant(context.backend.extractId(entity)).get(), blueprint.getProperties()));
        }

        @Override
        public Tenants.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Tenants.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Tenants.Single create(Tenant.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class ReadContained<BE> extends Traversal<BE, Tenant> implements Tenants.ReadContained {

        public ReadContained(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        public Tenants.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Tenants.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Traversal<BE, Tenant> implements Tenants.Read {

        public Read(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        public Tenants.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Tenants.Single get(Path id) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(id));
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Tenant, Tenant.Update>
            implements Tenants.Multiple {

        public Multiple(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        public ResourceTypes.ReadContained feedlessResourceTypes() {
            return new BaseResourceTypes.ReadContained<>(context.proceedTo(contains, ResourceType.class).get());
        }

        @Override
        public MetricTypes.ReadContained feedlessMetricTypes() {
            return new BaseMetricTypes.ReadContained<>(context.proceedTo(contains, MetricType.class).get());
        }

        @Override
        public Environments.ReadContained environments() {
            return new BaseEnvironments.ReadContained<>(context.proceedTo(contains, Environment.class).get());
        }

        @Override
        public MetricTypes.Read allMetricTypes() {
            return new BaseMetricTypes.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(MetricType.class)},
                    {by(contains), type(Environment.class), by(contains), type(Feed.class),
                            by(contains), type(MetricType.class)}
            }).getting(MetricType.class));
        }

        @Override
        public ResourceTypes.Read allResourceTypes() {
            return new BaseResourceTypes.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(ResourceType.class)},
                    {by(contains), type(Environment.class), by(contains), type(Feed.class),
                            by(contains), type(ResourceType.class)}
            }).getting(ResourceType.class));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, Tenant, Tenant.Update> implements Tenants.Single {

        public Single(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        public ResourceTypes.ReadWrite feedlessResourceTypes() {
            return new BaseResourceTypes.ReadWrite<>(context.proceedTo(contains, ResourceType.class).get());
        }

        @Override
        public MetricTypes.ReadWrite feedlessMetricTypes() {
            return new BaseMetricTypes.ReadWrite<>(context.proceedTo(contains, MetricType.class).get());
        }

        @Override
        public Environments.ReadWrite environments() {
            return new BaseEnvironments.ReadWrite<>(context.proceedTo(contains, Environment.class).get());
        }

        @Override
        public MetricTypes.Read allMetricTypes() {
            return new BaseMetricTypes.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(MetricType.class)},
                    {by(contains), type(Environment.class), by(contains), type(Feed.class),
                            by(contains), type(MetricType.class)}
            }).getting(MetricType.class));
        }

        @Override
        public ResourceTypes.Read allResourceTypes() {
            return new BaseResourceTypes.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(ResourceType.class)},
                    {by(contains), type(Environment.class), by(contains), type(Feed.class),
                            by(contains), type(ResourceType.class)}
            }).getting(ResourceType.class));
        }
    }
}
