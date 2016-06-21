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
import static org.hawkular.inventory.api.filters.Related.asTargetBy;
import static org.hawkular.inventory.api.filters.With.id;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
public final class BaseOperationTypes {

    private BaseOperationTypes() {

    }

    public static class ReadWrite<BE>
            extends Mutator<BE, OperationType, OperationType.Blueprint, OperationType.Update, String>
            implements OperationTypes.ReadWrite {

        public ReadWrite(TraversalContext<BE, OperationType> context) {
            super(context);
        }

        @Override protected String getProposedId(Transaction<BE> tx, OperationType.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<BE, OperationType>
        wireUpNewEntity(BE entity, OperationType.Blueprint blueprint, CanonicalPath parentPath, BE parent,
                        Transaction<BE> tx) {
            return new EntityAndPendingNotifications<>(entity, new OperationType(blueprint.getName(),
                    parentPath.extend(OperationType.SEGMENT_TYPE, tx.extractId(entity)).get(), null,
                    blueprint.getProperties()), emptyList());
        }

        @Override public OperationTypes.Multiple getAll(Filter[][] filters) {
            return new BaseOperationTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override public OperationTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseOperationTypes.Single<>(context.proceed().where(id(id)).get());
        }

        @Override public OperationTypes.Single create(OperationType.Blueprint blueprint, boolean cache) throws
                EntityAlreadyExistsException {
            return new BaseOperationTypes.Single<>(context.toCreatedEntity(doCreate(blueprint), cache));
        }

        @Override protected void preCreate(OperationType.Blueprint blueprint, Transaction<BE> tx) {
            //disallow this if the parent resource type is a part of a metadata pack
            if (tx.traverseToSingle(getParent(tx), Query.path().with(asTargetBy(incorporates),
                    With.type(MetadataPack.class)).get()) != null) {
                throw new IllegalArgumentException("Cannot create an operation type of resource type included in " +
                        "a meta data pack. This would invalidate the metadata pack's identity.");
            }

        }

        @Override
        protected void preDelete(String s, BE entityRepresentation, Transaction<BE> tx) {
            if (isResourceTypeInMetadataPack(entityRepresentation, tx)) {
                throw new IllegalArgumentException("Cannot delete an operation type of resource type included in " +
                        "a meta data pack. This would invalidate the metadata pack's identity.");
            }
        }

        private static <BE> boolean isResourceTypeInMetadataPack(BE operationType, Transaction<BE> tx) {
            return tx.traverseToSingle(operationType, Query.path().with(asTargetBy(contains),
                    asTargetBy(incorporates), With.type(MetadataPack.class)).get()) != null;
        }
    }

    public static class ReadContained<BE> extends Fetcher<BE, OperationType, OperationType.Update>
            implements OperationTypes.ReadContained {

        public ReadContained(TraversalContext<BE, OperationType> context) {
            super(context);
        }

        @Override
        public OperationTypes.Multiple getAll(Filter[][] filters) {
            return new BaseOperationTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public OperationTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseOperationTypes.Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Single<BE> extends SingleIdentityHashedFetcher<BE, OperationType, OperationType.Blueprint,
            OperationType.Update> implements OperationTypes.Single {

        public Single(TraversalContext<BE, OperationType> context) {
            super(context);
        }

        @Override public Data.ReadWrite<DataRole.OperationType> data() {
            return new BaseData.ReadWrite<>(context.proceedTo(contains, DataEntity.class).get(),
                    DataRole.OperationType.class, new OperationTypeDataModificationChecks<>(context));
        }

        @Override protected void preDelete(BE deletedEntity, Transaction<BE> transaction) {
            if (ReadWrite.isResourceTypeInMetadataPack(deletedEntity, transaction)) {
                throw new IllegalArgumentException("Cannot delete an operation type of resource type included in " +
                        "a meta data pack. This would invalidate the metadata pack's identity.");
            }
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, OperationType, OperationType.Update>
            implements OperationTypes.Multiple {

        public Multiple(TraversalContext<BE, OperationType> context) {
            super(context);
        }


        @Override public Data.Read<DataRole.OperationType> data() {
            return new BaseData.Read<>(context.proceedTo(contains, DataEntity.class).get(),
                    new OperationTypeDataModificationChecks<>(context));
        }
    }


    private static class OperationTypeDataModificationChecks<BE> implements BaseData.DataModificationChecks<BE> {
        private final TraversalContext<BE, ?> context;

        private OperationTypeDataModificationChecks(TraversalContext<BE, ?> context) {
            this.context = context;
        }

        @Override
        public void preCreate(DataEntity.Blueprint blueprint, Transaction<BE> transaction) {
            BE mp = transaction.querySingle(context.select().path().with(asTargetBy(contains),
                    asTargetBy(incorporates), With.type(MetadataPack.class)).get());

            if (mp != null) {
                BE ot = transaction.querySingle(context.select().get());
                throw new IllegalArgumentException(
                        "Data '" + blueprint.getId() + "' cannot be created" +
                                " under operation type " + transaction.extractCanonicalPath(ot) +
                                ", because the owning resource type is part of a meta data pack." +
                                " Doing this would invalidate meta data pack's identity.");
            }
        }

        @Override
        public void preUpdate(BE dataEntity, DataEntity.Update update, Transaction<BE> tx) {
            if (update.getValue() == null) {
                return;
            }

            BE mp = tx.traverseToSingle(dataEntity, Query.path().with(
                    asTargetBy(contains), //up to operation type
                    asTargetBy(contains), //up to resource type
                    asTargetBy(incorporates), With.type(MetadataPack.class) // up to the pack
            ).get());

            if (mp != null) {
                CanonicalPath dataPath = tx.extractCanonicalPath(dataEntity);
                throw new IllegalArgumentException(
                        "Data '" + dataPath.getSegment().getElementId() + "' cannot be updated" +
                                " under operation type " + dataPath.up() +
                                ", because the owning resource type is part of a meta data pack." +
                                " Doing this would invalidate meta data pack's identity.");
            }
        }

        @Override
        public void postUpdate(BE dataEntity, Transaction<BE> tx) {
        }

        @Override
        public void preDelete(BE dataEntity, Transaction<BE> tx) {
            CanonicalPath dataPath = tx.extractCanonicalPath(dataEntity);
            BE ot = null;
            try {
                ot = tx.find(dataPath.up());
            } catch (ElementNotFoundException e) {
                Fetcher.throwNotFoundException(context);
            }

            if (ReadWrite.isResourceTypeInMetadataPack(ot, tx)) {
                throw new IllegalArgumentException(
                        "Data '" + dataPath.getSegment().getElementId() + "' cannot be deleted" +
                                " under operation type " + dataPath.up() +
                                ", because the owning resource type is part of a meta data pack." +
                                " Doing this would invalidate meta data pack's identity.");
            }
        }

        @Override
        public void postCreate(BE dataEntity, Transaction<BE> tx) {
        }

        @Override
        public void postDelete(BE dataEntity, Transaction<BE> tx) {
        }
    }
}
