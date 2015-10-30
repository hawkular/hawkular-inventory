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
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.Related.asTargetBy;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.base.spi.ElementNotFoundException;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseResourceTypes {

    private BaseResourceTypes() {

    }

    public static class ReadWrite<BE>
            extends Mutator<BE, ResourceType, ResourceType.Blueprint, ResourceType.Update, String>
            implements ResourceTypes.ReadWrite {

        public ReadWrite(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        protected String getProposedId(ResourceType.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<ResourceType> wireUpNewEntity(BE entity,
                ResourceType.Blueprint blueprint, CanonicalPath parentPath, BE parent) {

            context.backend.update(entity, ResourceType.Update.builder().build());

            return new EntityAndPendingNotifications<>(new ResourceType(blueprint.getName(),
                    parentPath.extend(ResourceType.class, context.backend.extractId(entity)).get(),
                    blueprint.getProperties()));
        }

        @Override
        public ResourceTypes.Multiple getAll(Filter[][] filters) {
            return new BaseResourceTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public ResourceTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseResourceTypes.Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public ResourceTypes.Single create(ResourceType.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new BaseResourceTypes.Single<>(context.replacePath(doCreate(blueprint)));
        }

        @Override
        protected void cleanup(String s, BE entityRepresentation) {
            if (isResourceTypeInMetadataPack(context, entityRepresentation)) {
                throw new IllegalArgumentException("Cannot delete a resource type that is part of a metadata pack. " +
                        "This would invalidate the meta data pack's identity.");
            }
        }

        @Override
        protected void preUpdate(String s, BE entityRepresentation, ResourceType.Update update) {
        }

        private static <BE> boolean isResourceTypeInMetadataPack(TraversalContext<BE, ?> context, BE resourceType) {
            return context.backend.traverseToSingle(resourceType, Query.path()
                    .with(asTargetBy(incorporates), type(MetadataPack.class)).get()) != null;
        }
    }

    public static class ReadContained<BE> extends Fetcher<BE, ResourceType, ResourceType.Update>
            implements ResourceTypes.ReadContained {

        public ReadContained(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        public ResourceTypes.Multiple getAll(Filter[][] filters) {
            return new BaseResourceTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public ResourceTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseResourceTypes.Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Fetcher<BE, ResourceType, ResourceType.Update> implements ResourceTypes.Read {

        public Read(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        public ResourceTypes.Multiple getAll(Filter[][] filters) {
            return new BaseResourceTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public ResourceTypes.Single get(Path id) throws EntityNotFoundException {
            return new BaseResourceTypes.Single<>(context.proceedTo(id));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, ResourceType, ResourceType.Update>
            implements ResourceTypes.Single {

        public Single(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        public Resources.Read resources() {
            return new BaseResources.Read<>(context.proceedTo(defines, Resource.class).get());
        }

        @Override
        public MetricTypes.ReadAssociate metricTypes() {
            return new BaseMetricTypes.ReadAssociate<>(context.proceedTo(incorporates, MetricType.class).get());
        }

        @Override
        public OperationTypes.ReadWrite operationTypes() {
            return new BaseOperationTypes.ReadWrite<>(context.proceedTo(contains, OperationType.class).get());
        }

        @Override
        protected void cleanup(BE deletedEntity) {
            if (ReadWrite.isResourceTypeInMetadataPack(context, deletedEntity)) {
                throw new IllegalArgumentException("Cannot delete a resource type that is part of a metadata pack. " +
                        "This would invalidate the meta data pack's identity.");
            }
        }

        @Override
        protected void preUpdate(BE updatedEntity, ResourceType.Update update) {
        }

        @Override
        public Data.ReadWrite<ResourceTypes.DataRole> data() {
            return new BaseData.ReadWrite<>(context.proceedTo(contains, DataEntity.class).get(),
                    new ResourceTypeDataModificationChecks<>(context));
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, ResourceType, ResourceType.Update>
            implements ResourceTypes.Multiple {

        public Multiple(TraversalContext<BE, ResourceType> context) {
            super(context);
        }

        @Override
        public Resources.Read resources() {
            return new BaseResources.Read<>(context.proceedTo(defines, Resource.class).get());
        }

        @Override
        public MetricTypes.ReadContained metricTypes() {
            return new BaseMetricTypes.ReadContained<>(context.proceedTo(incorporates, MetricType.class).get());
        }

        @Override public OperationTypes.ReadContained operationTypes() {
            return new BaseOperationTypes.ReadContained<>(context.proceedTo(contains, OperationType.class).get());
        }

        @Override
        public Data.Read<ResourceTypes.DataRole> data() {
            return new BaseData.Read<>(context.proceedTo(contains, DataEntity.class).get(),
                    new ResourceTypeDataModificationChecks<>(context));
        }
    }


    private static class ResourceTypeDataModificationChecks<BE> implements BaseData.DataModificationChecks<BE> {
        private final TraversalContext<BE, ?> context;

        public ResourceTypeDataModificationChecks(TraversalContext<BE, ?> context) {
            this.context = context;
        }

        @Override
        public void preCreate(DataEntity.Blueprint blueprint) {
            BE pack = context.backend.querySingle(context.select().path().with(Related
                    .asTargetBy(incorporates), With.type(MetadataPack.class)).get());

            if (pack != null) {
                BE rt = context.backend.querySingle(context.select().get());
                throw new IllegalArgumentException(
                        "Data '" + blueprint.getId() + "' cannot be created" +
                                " under resource type " + context.backend.extractCanonicalPath(rt) +
                                ", because it is part of a meta data pack." +
                                " Doing this would invalidate meta data pack's identity.");
            }
        }

        @Override
        public void preUpdate(BE dataEntity, DataEntity.Update update) {
            if (update.getValue() == null) {
                return;
            }

            BE mp = context.backend.traverseToSingle(dataEntity, Query.path().with(
                    Related.asTargetBy(contains), //up to resource type
                    Related.asTargetBy(incorporates), With.type(MetadataPack.class) // up to the pack
            ).get());

            if (mp != null) {
                CanonicalPath dataPath = context.backend.extractCanonicalPath(dataEntity);
                throw new IllegalArgumentException(
                        "Data '" + dataPath.getSegment().getElementId() + "' cannot be updated" +
                                " under resource type " + dataPath.up() +
                                ", because it is part of a meta data pack." +
                                " Doing this would invalidate meta data pack's identity.");
            }
        }

        @Override
        public void preDelete(BE dataEntity) {
            CanonicalPath dataPath = context.backend.extractCanonicalPath(dataEntity);
            BE rt = null;
            try {
                rt = context.backend.find(dataPath.up());
            } catch (ElementNotFoundException e) {
                Fetcher.throwNotFoundException(context);
            }

            if (ReadWrite.isResourceTypeInMetadataPack(context, rt)) {
                throw new IllegalArgumentException(
                        "Data '" + dataPath.getSegment().getElementId() + "' cannot be deleted" +
                                " under resource type " + dataPath.up() +
                                ", because it is part of a meta data pack." +
                                " Doing this would invalidate meta data pack's identity.");
            }
        }
    }
}
