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

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
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
import org.hawkular.inventory.api.filters.RecurseFilter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.SegmentType;

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
        protected String getProposedId(Transaction<BE> tx, Resource.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<BE, Resource> wireUpNewEntity(BE entity,
                                                                              Resource.Blueprint blueprint,
                                                                              CanonicalPath parentPath, BE parent,
                                                                              Transaction<BE> tx) {

            BE resourceTypeObject;
            CanonicalPath resourceTypePath = null;
            try {
                CanonicalPath tenant = CanonicalPath.of().tenant(parentPath.ids().getTenantId()).get();
                resourceTypePath = Util.canonicalize(blueprint.getResourceTypePath(), tenant,
                        parentPath, ResourceType.SEGMENT_TYPE);
                resourceTypeObject = tx.find(resourceTypePath);
            } catch (ElementNotFoundException e) {
                throw new IllegalArgumentException("Resource type '" + blueprint.getResourceTypePath() + "' not found" +
                        " when resolved to '" + resourceTypePath + "' while trying to wire up a new resource on path '"
                        + parentPath.extend(SegmentType.r, blueprint.getId()).get() + "'.");
            }

            //specifically do NOT check relationship rules, here because defines cannot be created "manually".
            //here we "know what we are doing" and need to create the defines relationship to capture the
            //contract of the resource.
            BE r = tx.relate(resourceTypeObject, entity, defines.name(), null);

            CanonicalPath entityPath = tx.extractCanonicalPath(entity);
            resourceTypePath = tx.extractCanonicalPath(resourceTypeObject);

            ResourceType resourceType = tx.convert(resourceTypeObject, ResourceType.class);

            Resource ret = new Resource(blueprint.getName(), parentPath.extend(Resource.SEGMENT_TYPE,
                    tx.extractId(entity)).get(), null, null, null, resourceType, blueprint.getProperties());

            Relationship definesRel = new Relationship(tx.extractId(r), defines.name(), resourceTypePath,
                    entityPath);

            List<Notification<?, ?>> notifications = new ArrayList<>();
            notifications.add(new Notification<>(definesRel, definesRel, created()));

            if (tx.extractType(parent).equals(Resource.class)) {
                //we're creating a child resource... need to also create the implicit isParentOf
                //in here, we do use the relationship rules to check if the hierarchy we're introducing by this call
                //conforms to the rules.
                String relationshipName = isParentOf.name();
                RelationshipRules.checkCreate(tx, parent, outgoing, relationshipName, entity);
                r = tx.relate(parent, entity, relationshipName, null);

                Relationship parentRel = new Relationship(tx.extractId(r), isParentOf.name(),
                        parentPath, entityPath);

                notifications.add(new Notification<>(parentRel, parentRel, created()));
            }

            return new EntityAndPendingNotifications<>(entity, ret, notifications);
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
        public Resources.Single create(Resource.Blueprint blueprint, boolean cache) throws EntityAlreadyExistsException {
            if (blueprint.getResourceTypePath() == null) {
                throw new IllegalArgumentException("ResourceType path is null");
            }

            return new Single<>(context.toCreatedEntity(doCreate(blueprint), cache));
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
            return Associator.associate(context, SegmentType.r, isParentOf, id);
        }

        @Override
        public Relationship disassociate(Path id) throws EntityNotFoundException, IllegalArgumentException {
            return Associator.disassociate(context, SegmentType.r, isParentOf, id);
        }

        @Override
        public Relationship associationWith(Path path) throws RelationNotFoundException {
            return Associator.associationWith(context, SegmentType.r, isParentOf, path);
        }
    }

    public static class Single<BE> extends SingleSyncedFetcher<BE, Resource, Resource.Blueprint,
                Resource.Update> implements Resources.Single {

        public Single(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Metrics.ReadWrite metrics() {
            return new BaseMetrics.ReadWrite<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public Metrics.ReadAssociate allMetrics() {
            return new BaseMetrics.ReadAssociate<>(context.proceedTo(incorporates, Metric.class).get());
        }

        @Override
        public Resources.ReadAssociate allResources() {
            return new ReadAssociate<>(context.proceed().hop(Related.by(isParentOf), With.type(
                    Resource.class)).get());
        }

        @Override
        public Resources.ReadWrite resources() {
            return new ReadWrite<>(context.proceed().hop(Related.by(contains), With.type(
                    Resource.class)).get());
        }

        @Override
        public Resources.Read recursiveResources() {
            return new Read<>(context.proceed().hop(RecurseFilter.builder().addChain(Related.by(contains), With
                    .type(Resource.class)).build()).get());
        }

        @Override
        public Resources.Single parent() {
            return new Single<>(context.proceed().hop(Related.asTargetBy(contains), With.type(
                    Resource.class)).get());
        }

        @Override
        public Resources.Read parents() {
            return new Read<>(context.proceed().hop(Related.asTargetBy(isParentOf), With.type(
                    Resource.class)).get());
        }

        @Override
        public Data.ReadWrite<DataRole.Resource> data() {
            return new BaseData.ReadWrite<>(context.proceedTo(contains, DataEntity.class).get(),
                    DataRole.Resource.class, BaseData.DataModificationChecks.<BE>none());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Resource, Resource.Update>
            implements Resources.Multiple {

        public Multiple(TraversalContext<BE, Resource> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public Metrics.Read allMetrics() {
            return new BaseMetrics.Read<>(context.proceedTo(incorporates, Metric.class).get());
        }

        @Override
        public Resources.Read allResources() {
            return new ReadAssociate<>(context.proceed().hop(Related.by(isParentOf), With.type(
                    Resource.class)).get());
        }

        @Override
        public Resources.Read recursiveResources() {
            return new Read<>(context.proceed().hop(RecurseFilter.builder().addChain(Related.by(isParentOf), With
                    .type(Resource.class)).build()).get());
        }

        @Override
        public Resources.ReadWrite resources() {
            return new ReadWrite<>(context.proceed().hop(Related.by(contains), With.type(
                    Resource.class)).get());
        }

        @Override
        public Resources.Read parents() {
            return new Read<>(context.proceed().hop(Related.asTargetBy(isParentOf), With.type(
                    Resource.class)).get());
        }

        @Override
        public Data.Read<DataRole.Resource> data() {
            return new BaseData.Read<>(context.proceedTo(contains, DataEntity.class).get(),
                    BaseData.DataModificationChecks.<BE>none());
        }

    }
}
