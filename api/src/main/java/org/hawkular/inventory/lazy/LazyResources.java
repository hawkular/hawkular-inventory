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
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.TenantBasedEntity;
import org.hawkular.inventory.lazy.NewEntityAndPendingNotifications.Notification;
import org.hawkular.inventory.lazy.spi.CanonicalPath;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.api.filters.With.id;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class LazyResources {

    private LazyResources() {

    }

    public static final class ReadWrite<BE> extends Mutator<BE, Resource, Resource.Blueprint, Resource.Update>
            implements Resources.ReadWrite {

        public ReadWrite(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Resource.Blueprint entity) {
            return entity.getId();
        }

        @Override
        protected NewEntityAndPendingNotifications<Resource> wireUpNewEntity(BE entity,
                Resource.Blueprint blueprint, CanonicalPath parentPath, BE parent) {

            Class<? extends AbstractElement<?, ?>> parentType = context.backend.getType(parent);

            @SuppressWarnings("unchecked")
            TenantBasedEntity<?, ?> parentEntity = context.backend.convert(parent,
                    (Class<? extends TenantBasedEntity<?, ?>>) parentType);

            BE resourceTypeObject = context.backend.find(CanonicalPath.builder()
                    .withTenantId(parentEntity.getTenantId()).withResourceTypeId(blueprint.getResourceTypeId())
                    .build());

            BE r = context.backend.relate(resourceTypeObject, entity, defines.name(), null);

            ResourceType resourceType = context.backend.convert(resourceTypeObject, ResourceType.class);

            Resource ret = new Resource(parentPath.getTenantId(), parentPath.getEnvironmentId(), parentPath.getFeedId(),
                    context.backend.extractId(entity), resourceType, blueprint.getProperties());

            Relationship rel = new Relationship(context.backend.extractId(r), defines.name(), resourceType, ret);

            return new NewEntityAndPendingNotifications<>(ret, new Notification<>(rel, rel, created()));
        }

        @Override
        public Resources.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public Resources.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Resources.Single create(Resource.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static final class Read<BE> extends Traversal<BE, Resource> implements Resources.Read {

        public Read(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Resources.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public Resources.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static final class Single<BE> extends SingleEntityFetcher<BE, Resource> implements Resources.Single {

        public Single(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Metrics.ReadAssociate metrics() {
            return new LazyMetrics.ReadAssociate<>(context.proceedTo(owns, Metric.class).get());
        }
    }

    public static final class Multiple<BE> extends MultipleEntityFetcher<BE, Resource> implements Resources.Multiple {

        public Multiple(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new LazyMetrics.Read<>(context.proceedTo(owns, Metric.class).get());
        }
    }
}
