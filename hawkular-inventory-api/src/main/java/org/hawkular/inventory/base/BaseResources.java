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

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.Relationships.WellKnown.isParentOf;
import static org.hawkular.inventory.api.filters.With.id;

import java.util.ArrayList;
import java.util.List;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.base.spi.ElementNotFoundException;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseResources {

    private BaseResources() {

    }

    public static class ReadWrite<BE> extends Mutator<BE, Resource, Resource.Blueprint, Resource.Update, String>
            implements Resources.ReadWrite {

        public ReadWrite(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Resource.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<Resource> wireUpNewEntity(BE entity,
                Resource.Blueprint blueprint, CanonicalPath parentPath, BE parent) {

            BE resourceTypeObject;
            try {
                CanonicalPath tenant = CanonicalPath.of().tenant(parentPath.ids().getTenantId()).get();
                CanonicalPath resourceTypePath = Util.canonicalize(blueprint.getResourceTypePath(), tenant,
                        parentPath, ResourceType.class);
                resourceTypeObject = context.backend.find(resourceTypePath);
            } catch (ElementNotFoundException e) {
                throw new IllegalArgumentException("Resource type '" + blueprint.getResourceTypePath() + "' not found" +
                        " in tenant '" + parentPath.getRoot().getSegment().getElementId() + "'.");
            }

            BE r = relate(resourceTypeObject, entity, defines.name());

            CanonicalPath entityPath = context.backend.extractCanonicalPath(entity);
            CanonicalPath resourceTypePath = context.backend.extractCanonicalPath(resourceTypeObject);

            ResourceType resourceType = context.backend.convert(resourceTypeObject, ResourceType.class);

            Resource ret = new Resource(parentPath.extend(Resource.class, context.backend.extractId(entity)).get(),
                    resourceType, blueprint.getProperties());

            Relationship definesRel = new Relationship(context.backend.extractId(r), defines.name(), resourceTypePath,
                    entityPath);

            List<Notification<?, ?>> notifications = new ArrayList<>();
            notifications.add(new Notification<>(definesRel, definesRel, created()));

            if (context.backend.extractType(parent).equals(Resource.class)) {
                //we're creating a child resource... need to also create the implicit isParentOf
                r = relate(parent, entity, isParentOf.name());

                Relationship parentRel = new Relationship(context.backend.extractId(r), isParentOf.name(),
                        parentPath, entityPath);

                notifications.add(new Notification<>(parentRel, parentRel, created()));
            }

            return new EntityAndPendingNotifications<>(ret, notifications);
        }

        @Override
        public Resources.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Resources.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Resources.Single create(Resource.Blueprint blueprint) throws EntityAlreadyExistsException {
            if (blueprint.getResourceTypePath() == null) {
                throw new IllegalArgumentException("ResourceType path is null");
            }

            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class ReadContained<BE> extends Traversal<BE, Resource> implements Resources.ReadContained {

        public ReadContained(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Resources.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Resources.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Traversal<BE, Resource> implements Resources.Read {

        public Read(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Resources.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Resources.Single get(Path id) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(id));
        }
    }

    public static class ReadAssociate<BE> extends Read<BE> implements Resources.ReadAssociate {
        public ReadAssociate(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Relationship associate(Path id) throws EntityNotFoundException,
                RelationAlreadyExistsException {
            Query sourceQuery = context.sourcePath;
            Query targetResource = Util.queryTo(context, id);

            BE targetEntity = getSingle(targetResource, Resource.class);

            EntityAndPendingNotifications<Relationship> rel = Util.createAssociation(context, sourceQuery,
                    Resource.class, isParentOf.name(), targetEntity);

            context.notifyAll(rel);
            return rel.getEntity();
        }

        @Override
        public Relationship disassociate(Path id) throws EntityNotFoundException, IllegalArgumentException {
            Query sourceQuery = context.select().get();
            Query targetResource = Util.queryTo(context, id);

            BE targetEntity = getSingle(targetResource, Resource.class);

            EntityAndPendingNotifications<Relationship> rel = Util.deleteAssociation(context, sourceQuery,
                    Resource.class, isParentOf.name(), targetEntity);

            context.notifyAll(rel);
            return rel.getEntity();
        }

        @Override
        public Relationship associationWith(Path path) throws RelationNotFoundException {
            Query sourceQuery = context.select().get();
            Query targetResource = Util.queryTo(context, path);

            return Util.getAssociation(context, sourceQuery, Resource.class, targetResource, Resource.class,
                    isParentOf.name());
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, Resource, Resource.Update>
            implements Resources.Single {

        public Single(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Metrics.ReadAssociate metrics() {
            return new BaseMetrics.ReadAssociate<>(context.proceedTo(incorporates, Metric.class).get());
        }

        @Override
        public Resources.ReadAssociate allChildren() {
            return new ReadAssociate<>(context.proceed().hop(Related.by(isParentOf), With.type(Resource.class)).get());
        }

        @Override
        public Resources.ReadWrite containedChildren() {
            return new ReadWrite<>(context.proceed().hop(Related.by(contains), With.type(Resource.class)).get());
        }

        @Override
        public Resources.Single parent() {
            return new Single<>(context.proceed().hop(Related.asTargetBy(contains), With.type(Resource.class)).get());
        }

        @Override
        public Resources.Read parents() {
            return new Read<>(context.proceed().hop(Related.asTargetBy(isParentOf), With.type(Resource.class)).get());
        }

        @Override
        public Data.ReadWrite<Resources.DataRole> data() {
            return new BaseData.ReadWrite<>(context.proceedTo(contains, DataEntity.class).get());
        }

    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Resource, Resource.Update>
            implements Resources.Multiple {

        public Multiple(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedTo(incorporates, Metric.class).get());
        }

        @Override
        public Resources.ReadAssociate allChildren() {
            return new ReadAssociate<>(context.proceed().hop(Related.by(isParentOf), With.type(Resource.class)).get());
        }

        @Override
        public Resources.ReadWrite containedChildren() {
            return new ReadWrite<>(context.proceed().hop(Related.by(contains), With.type(Resource.class)).get());
        }

        @Override
        public Resources.Read parents() {
            return new Read<>(context.proceed().hop(Related.asTargetBy(isParentOf), With.type(Resource.class)).get());
        }

        @Override
        public Data.Read<Resources.DataRole> data() {
            return new BaseData.Read<>(context.proceedTo(contains, DataEntity.class).get());
        }

    }
}
