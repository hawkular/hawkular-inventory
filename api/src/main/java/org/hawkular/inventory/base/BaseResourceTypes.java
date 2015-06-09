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

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.base.spi.CanonicalPath;

import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.api.filters.With.id;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseResourceTypes {

    private BaseResourceTypes() {

    }

    public static class ReadWrite<BE>
            extends Mutator<BE, ResourceType, ResourceType.Blueprint, ResourceType.Update>
            implements ResourceTypes.ReadWrite {

        public ReadWrite(TraversalContext context) {
            super(context);
        }

        @Override
        protected String getProposedId(ResourceType.Blueprint entity) {
            return entity.getId();
        }

        @Override
        protected NewEntityAndPendingNotifications<ResourceType> wireUpNewEntity(BE entity,
                ResourceType.Blueprint blueprint, CanonicalPath parentPath, BE parent) {

            context.backend.update(entity, ResourceType.Update.builder().withVersion(blueprint.getVersion()).build());

            return new NewEntityAndPendingNotifications<>(new ResourceType(parentPath.getTenantId(),
                    context.backend.extractId(entity), blueprint.getVersion(), blueprint.getProperties()));
        }

        @Override
        public ResourceTypes.Multiple getAll(Filter... filters) {
            return new BaseResourceTypes.Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public ResourceTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseResourceTypes.Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public ResourceTypes.Single create(ResourceType.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new BaseResourceTypes.Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class Read<BE> extends Fetcher<BE, ResourceType> implements ResourceTypes.Read {

        public Read(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        public ResourceTypes.Multiple getAll(Filter... filters) {
            return new BaseResourceTypes.Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public ResourceTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseResourceTypes.Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, ResourceType> implements ResourceTypes.Single {

        public Single(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        public Resources.Read resources() {
            return new BaseResources.Read<>(context.proceedTo(defines, Resource.class).get());
        }

        @Override
        public MetricTypes.ReadAssociate metricTypes() {
            return new BaseMetricTypes.ReadAssociate<>(context.proceedTo(owns, MetricType.class).get());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, ResourceType>
            implements ResourceTypes.Multiple {

        public Multiple(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        public Resources.Read resources() {
            return new BaseResources.Read<>(context.proceedTo(defines, Resource.class).get());
        }

        @Override
        public MetricTypes.Read metricTypes() {
            return new BaseMetricTypes.Read<>(context.proceedTo(owns, MetricType.class).get());
        }
    }
}
