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

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.OperationType;

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

        @Override protected String getProposedId(OperationType.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<OperationType> wireUpNewEntity(BE entity,
                                                                               OperationType.Blueprint blueprint,
                                                                               CanonicalPath parentPath, BE parent) {
            return new EntityAndPendingNotifications<>(new OperationType(blueprint.getName(),
                    parentPath.extend(OperationType.class, context.backend.extractId(entity)).get(),
                    blueprint.getProperties()));
        }

        @Override public OperationTypes.Multiple getAll(Filter[][] filters) {
            return new BaseOperationTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override public OperationTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseOperationTypes.Single<>(context.proceed().where(id(id)).get());
        }

        @Override public OperationTypes.Single create(OperationType.Blueprint blueprint) throws
                EntityAlreadyExistsException {
            return new BaseOperationTypes.Single<>(context.replacePath(doCreate(blueprint)));
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

    public static class Single<BE> extends SingleEntityFetcher<BE, OperationType, OperationType.Update>
            implements OperationTypes.Single {

        public Single(TraversalContext<BE, OperationType> context) {
            super(context);
        }

        @Override public Data.ReadWrite<OperationTypes.DataRole> data() {
            return new BaseData.ReadWrite<>(context.proceedTo(contains, DataEntity.class).get());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, OperationType, OperationType.Update>
            implements OperationTypes.Multiple {

        public Multiple(TraversalContext<BE, OperationType> context) {
            super(context);
        }


        @Override public Data.Read<OperationTypes.DataRole> data() {
            return new BaseData.Read<>(context.proceedTo(contains, DataEntity.class).get());
        }
    }
}
