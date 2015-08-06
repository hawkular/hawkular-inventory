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
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.api.filters.With.id;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hawkular.inventory.api.Datas;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Log;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.ShallowStructuredData;

/**
 * This a helper class for working with structured data.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
public final class BaseDatas {

    private BaseDatas() {
    }


    public static final class Read<BE> extends Traversal<BE, DataEntity> implements Datas.Read {

        private final DataEntity.Role role;

        public Read(DataEntity.Role role, TraversalContext<BE, DataEntity> context) {
            super(context);
            this.role = role;
        }

        @Override
        public Datas.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Datas.Single get(Void ignored) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(role.name())).get());
        }

        @Override
        public Resources.Single resource() {
            return new BaseResources.Single<>(context.retreatTo(contains, Resource.class).get());
        }
    }

    public static final class ReadWrite<BE>
            extends Mutator<BE, DataEntity, DataEntity.Blueprint, DataEntity.Update, Void>
            implements Datas.ReadWrite {

        private final DataEntity.Role role;

        public ReadWrite(DataEntity.Role role, TraversalContext context) {
            super(context);
            this.role = role;
        }

        @Override
        protected String getProposedId(DataEntity.Blueprint blueprint) {
            return role.name();
        }

        @Override
        protected EntityAndPendingNotifications<DataEntity> wireUpNewEntity(BE entity,
                DataEntity.Blueprint blueprint, CanonicalPath parentPath, BE parent) {

            List<EntityAndPendingNotifications.Notification<?, ?>> notifications = new ArrayList<>();

            DataEntity data = new DataEntity(parentPath, role, blueprint.getValue());

            Relationships.WellKnown rel = role.getCorrespondingRelationship();
            BE storedRel = relate(parent, entity, rel.name());
            Relationship relationship = new Relationship(context.backend.extractId(storedRel), rel.name(), parentPath,
                    data.getPath());
            notifications.add(new EntityAndPendingNotifications.Notification<>(relationship, relationship, created()));

            BE value = context.backend.persist(blueprint.getValue());

            //don't report this relationship, it is implicit
            relate(entity, value, hasData.name());

            return new EntityAndPendingNotifications<>(data, notifications);
        }

        @Override
        public Datas.Single create(DataEntity.Blueprint data) {
            return new Single<>(context.replacePath(doCreate(data)));
        }

        @Override
        protected void cleanup(Void ignored, BE entityRepresentation) {
            Set<BE> rels = context.backend.getRelationships(entityRepresentation, Relationships.Direction.outgoing,
                    hasData.name());

            if (rels.isEmpty()) {
                Log.LOGGER.wNoDataAssociatedWithEntity(context.backend.extractCanonicalPath(entityRepresentation));
                return;
            }

            BE dataRel = rels.iterator().next();

            BE structuredData = context.backend.getRelationshipTarget(dataRel);

            context.backend.deleteStructuredData(structuredData);
        }

        @Override
        public Resources.Single resource() {
            return new BaseResources.Single<>(context.retreatTo(contains, Resource.class).get());
        }

        @Override
        public Datas.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Datas.Single get(Void ignored) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(role.name())).get());
        }
    }

    public static final class Single<BE>
            extends SingleEntityFetcher<BE, DataEntity, DataEntity.Update>
            implements Datas.Single {

        public Single(TraversalContext<BE, DataEntity> context) {
            super(context);
        }

        @Override
        public StructuredData data(RelativePath dataPath) {
            //doing this in 2 queries might seem inefficient but this I think needs to be done to be able to
            //do the filtering
            return loadEntity((b, e) -> {
                BE dataEntity = context.backend.descendToData(b, dataPath);
                return dataEntity == null ? null : context.backend.convert(dataEntity, StructuredData.class);
            });
        }

        @Override
        public StructuredData bareData(RelativePath dataPath) {
            return loadEntity((b, e) -> {
                BE dataEntity = context.backend.descendToData(b, dataPath);
                return dataEntity == null ? null : context.backend.convert(dataEntity, ShallowStructuredData.class)
                        .getData();
            });
        }
    }

    public static final class Multiple<BE>
            extends MultipleEntityFetcher<BE, DataEntity, DataEntity.Update>
            implements Datas.Multiple {

        public Multiple(TraversalContext<BE, DataEntity> context) {
            super(context);
        }

        @Override
        public Page<StructuredData> datas(RelativePath dataPath, Pager pager) {
            return loadEntities(pager, (b, e) -> {
                BE dataEntity = context.backend.descendToData(b, dataPath);
                return context.backend.convert(dataEntity, StructuredData.class);
            });
        }

        @Override
        public Page<StructuredData> bareDatas(RelativePath dataPath, Pager pager) {
            return loadEntities(pager, (b, e) -> {
                BE dataEntity = context.backend.descendToData(b, dataPath);
                return context.backend.convert(dataEntity, ShallowStructuredData.class).getData();
            });
        }
    }
}
