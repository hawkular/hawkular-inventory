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
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.api.filters.With.id;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Log;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ValidationException;
import org.hawkular.inventory.api.ValidationException.ValidationMessage;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.ShallowStructuredData;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.RelativePath;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ListReportProvider;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;

/**
 * Contains access interface implementations for accessing data entities.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
public final class BaseData {

    private BaseData() {
    }


    public static final class Read<BE, R extends DataRole> extends Traversal<BE, DataEntity>
            implements Data.Read<R> {

        private final DataModificationChecks<BE> checks;

        public Read(TraversalContext<BE, DataEntity> context, DataModificationChecks<BE> checks) {
            super(context);
            this.checks = checks;
        }

        @Override
        public Data.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Data.Single get(R role) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(role.name())).get(), checks);
        }
    }

    public static final class ReadWrite<BE, R extends DataRole>
            extends Mutator<BE, DataEntity, DataEntity.Blueprint<R>, DataEntity.Update, R>
            implements Data.ReadWrite<R> {

        private final DataModificationChecks<BE> checks;
        private final Class<R> dataRoleClass;

        public ReadWrite(TraversalContext<BE, DataEntity> context, Class<R> dataRoleClass,
                         DataModificationChecks<BE> checks) {
            super(context);
            this.dataRoleClass = dataRoleClass;
            this.checks = checks;
        }

        @Override
        protected String getProposedId(Transaction<BE> tx, DataEntity.Blueprint<R> blueprint) {
            if (!dataRoleClass.equals(blueprint.getRole().getClass())) {
                throw new IllegalArgumentException("Invalid role/id. Admissible values for this data position are: "
                    + Arrays.asList(dataRoleClass.getEnumConstants()).stream().map(DataRole::name));
            }
            return blueprint.getRole().name();
        }

        @Override
        protected EntityAndPendingNotifications<BE, DataEntity> wireUpNewEntity(BE entity,
                                                                                DataEntity.Blueprint<R> blueprint,
                                                                                CanonicalPath parentPath, BE parent,
                                                                                Transaction<BE> tx) {
            Validator.validate(tx, blueprint.getValue(), entity);
            BE value = tx.persist(blueprint.getValue());

            //don't report this relationship, it is implicit
            //also, don't run the RelationshipRules checks - we're in the "privileged code" that is allowed to do
            //this
            tx.relate(entity, value, hasData.name(), null);

            DataEntity data = new DataEntity(parentPath, blueprint.getRole(), blueprint.getValue(), null,
                    null, null, blueprint.getProperties());

            return new EntityAndPendingNotifications<>(entity, data, emptyList());
        }

        @Override
        public Data.Single create(DataEntity.Blueprint<R> data, boolean cache) {
            return new Single<>(context.toCreatedEntity(doCreate(data), cache), checks);
        }

        @Override
        protected void preCreate(DataEntity.Blueprint<R> blueprint, Transaction<BE> transaction) {
            preCreate(checks, blueprint, transaction);
        }

        @Override
        protected void postCreate(BE entityObject, DataEntity entity, Transaction<BE> transaction) {
            postCreate(checks, entityObject, transaction);
        }

        @Override
        protected void preDelete(R role, BE entityRepresentation, Transaction<BE> transaction) {
            preDelete(checks, entityRepresentation, transaction);
        }

        @Override protected void postDelete(BE entityRepresentation, Transaction<BE> transaction) {
            postDelete(checks, entityRepresentation, transaction);
        }

        @Override
        protected void preUpdate(R role, BE entityRepresentation, DataEntity.Update update,
                                 Transaction<BE> transaction) {
            preUpdate(checks, entityRepresentation, update, transaction);
        }

        @Override
        protected void postUpdate(BE entityRepresentation, Transaction<BE> transaction) {
            postUpdate(checks, entityRepresentation, transaction);
        }

        @Override
        public Data.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Data.Single get(R role) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(role.name())).get(), checks);
        }

        private static <BE, R extends DataRole>
        void preCreate(DataModificationChecks<BE> checks, DataEntity.Blueprint<R> blueprint,
                       Transaction<BE> transaction) {
            checks.preCreate(blueprint, transaction);
        }

        private static <BE> void postCreate(DataModificationChecks<BE> checks, BE entity,
                                            Transaction<BE> transaction) {
            checks.postCreate(entity, transaction);
        }

        private static <BE> void preUpdate(DataModificationChecks<BE> checks, BE entityRepresentation,
                                           DataEntity.Update update, Transaction<BE> transaction) {
            checks.preUpdate(entityRepresentation, update, transaction);
            Validator.validate(transaction, update.getValue(), entityRepresentation);
        }

        private static <BE> void postUpdate(DataModificationChecks<BE> checks, BE entity,
                                            Transaction<BE> transaction) {
            checks.postCreate(entity, transaction);
        }

        private static <BE> void preDelete(DataModificationChecks<BE> checks, BE entityRepresentation,
                                           Transaction<BE> tx) {
            checks.preDelete(entityRepresentation, tx);

            Set<BE> rels = tx.getRelationships(entityRepresentation, Relationships.Direction.outgoing,
                    hasData.name());

            if (rels.isEmpty()) {
                Log.LOGGER.wNoDataAssociatedWithEntity(tx.extractCanonicalPath(entityRepresentation));
                return;
            }

            BE dataRel = rels.iterator().next();

            BE structuredData = tx.getRelationshipTarget(dataRel);

            tx.deleteStructuredData(structuredData);
        }

        private static <BE> void postDelete(DataModificationChecks<BE> checks, BE entity,
                                            Transaction<BE> transaction) {
            checks.postDelete(entity, transaction);
        }
    }

    public static final class Single<BE> extends SingleSyncedFetcher<BE, DataEntity, DataEntity.Blueprint<?>,
                DataEntity.Update> implements Data.Single {

        private final DataModificationChecks<BE> checks;

        public Single(TraversalContext<BE, DataEntity> context, DataModificationChecks<BE> checks) {
            super(context);
            this.checks = checks;
        }

        @Override
        public StructuredData data(RelativePath dataPath) {
            //doing this in 2 queries might seem inefficient but this I think needs to be done to be able to
            //do the filtering
            return loadEntity((b, e, tx) -> {
                BE dataEntity = tx.descendToData(b, dataPath);
                return dataEntity == null ? null : tx.convert(dataEntity, StructuredData.class);
            });
        }

        @Override
        public StructuredData flatData(RelativePath dataPath) {
            return loadEntity((b, e, tx) -> {
                BE dataEntity = tx.descendToData(b, dataPath);
                return dataEntity == null ? null : tx.convert(dataEntity, ShallowStructuredData.class)
                        .getData();
            });
        }

        @Override
        protected void preDelete(BE deletedEntity, Transaction<BE> transaction) {
            ReadWrite.preDelete(checks, deletedEntity, transaction);
        }

        @Override
        protected void postDelete(BE deletedEntity, Transaction<BE> transaction) {
            ReadWrite.postDelete(checks, deletedEntity, transaction);
        }

        @Override
        protected void preUpdate(BE updatedEntity, DataEntity.Update update, Transaction<BE> t) {
            ReadWrite.preUpdate(checks, updatedEntity, update, t);
        }

        @Override
        protected void postUpdate(BE updatedEntity, Transaction<BE> transaction) {
            ReadWrite.postUpdate(checks, updatedEntity, transaction);
        }
    }

    public static final class Multiple<BE>
            extends MultipleEntityFetcher<BE, DataEntity, DataEntity.Update>
            implements Data.Multiple {

        public Multiple(TraversalContext<BE, DataEntity> context) {
            super(context);
        }

        @Override
        public Page<StructuredData> data(RelativePath dataPath, Pager pager) {
            return loadEntities(pager, (b, e, tx) -> {
                BE dataEntity = tx.descendToData(b, dataPath);
                return tx.convert(dataEntity, StructuredData.class);
            });
        }

        @Override
        public Page<StructuredData> flatData(RelativePath dataPath, Pager pager) {
            return loadEntities(pager, (b, e, tx) -> {
                BE dataEntity = tx.descendToData(b, dataPath);
                return tx.convert(dataEntity, ShallowStructuredData.class).getData();
            });
        }
    }

    public static final class Validator {

        private static final JsonValidator VALIDATOR = JsonSchemaFactory.newBuilder()
                .setReportProvider(new ListReportProvider(LogLevel.INFO, LogLevel.FATAL)).freeze().getValidator();

        private static Filter[] navigateToSchema(DataRole role) {
            if (role == DataRole.Resource.configuration) {
                return new Filter[]{
                        //up to the containing resource
                        Related.asTargetBy(contains),
                        //up to the defining resource type
                        Related.asTargetBy(defines),
                        //down to the contained data entity
                        Related.by(contains), With.type(DataEntity.class),
                        //with id of configuration schema
                        With.id(DataRole.ResourceType.configurationSchema.name())
                };
            } else if (role == DataRole.Resource.connectionConfiguration) {
                return new Filter[]{
                        //up to the containing resource
                        Related.asTargetBy(contains),
                        //up to the defining resource type
                        Related.asTargetBy(defines),
                        //down to the contained data entity
                        Related.by(contains), With.type(DataEntity.class),
                        //with id of configuration schema
                        With.id(DataRole.ResourceType.connectionConfigurationSchema.name())
                };
            } else {
                throw new IllegalStateException("Incomplete mapping of navigation to data schema. Role '" + role + "'" +
                        " is not handled.");
            }
        }

        public static <BE> void validate(Transaction<BE> tx, StructuredData data, BE dataEntity) {
            CanonicalPath path = tx.extractCanonicalPath(dataEntity);

            DataRole role = DataRole.valueOf(path.ids().getDataRole());

            if (role.isSchema()) {
                try {
                    JsonNode schema = new JsonNodeReader(new ObjectMapper())
                            .fromInputStream(BaseData.class.getResourceAsStream("/json-meta-schema.json"));

                    CanonicalPath dataPath = tx.extractCanonicalPath(dataEntity);

                    validate(dataPath, convert(data), schema);
                } catch (IOException e) {
                    throw new IllegalStateException("Could not load the embedded JSON Schema meta-schema.");
                }
            } else {
                validateIfSchemaFound(tx, data, dataEntity, Query.path().with(navigateToSchema(role)).get());
            }
        }

        private static <BE> void validateIfSchemaFound(Transaction<BE> tx, StructuredData data,
                BE dataEntity, Query query) {

            BE possibleSchema = tx.traverseToSingle(dataEntity, query);
            if (possibleSchema == null) {
                //no schema means anything is OK
                return;
            }

            DataEntity schemaEntity = tx.convert(possibleSchema, DataEntity.class);

            CanonicalPath dataPath = tx.extractCanonicalPath(dataEntity);

            validate(dataPath, convert(data), convert(schemaEntity.getValue()));
        }

        private static void validate(CanonicalPath dataPath, JsonNode dataNode, JsonNode schemaNode) {
            //explicitly allow null schemas
            if (dataNode == null || dataNode.isNull()) {
                return;
            }

            try {
                ProcessingReport report = VALIDATOR.validate(schemaNode, dataNode, true);
                if (!report.isSuccess()) {
                    List<ValidationMessage> messages = new ArrayList<>();
                    report.forEach((m) ->
                            messages.add(new ValidationMessage(m.getLogLevel().name(), m.toString())));

                    throw new ValidationException(dataPath, messages, null);
                }
            } catch (ProcessingException e) {
                throw new ValidationException(dataPath, emptyList(), e);
            }
        }

        private static JsonNode convert(StructuredData data) {
            return data.accept(new StructuredData.Visitor.Simple<JsonNode, Void>() {
                @Override
                public JsonNode visitBool(boolean value, Void ignored) {
                    return JsonNodeFactory.instance.booleanNode(value);
                }

                @Override
                public JsonNode visitFloatingPoint(double value, Void ignored) {
                    return JsonNodeFactory.instance.numberNode(value);
                }

                @Override
                public JsonNode visitIntegral(long value, Void ignored) {
                    return JsonNodeFactory.instance.numberNode(value);
                }

                @Override
                public JsonNode visitList(List<StructuredData> value, Void ignored) {
                    ArrayNode list = JsonNodeFactory.instance.arrayNode();
                    value.forEach((s) -> list.add(s.accept(this, null)));
                    return list;
                }

                @Override
                public JsonNode visitMap(Map<String, StructuredData> value, Void ignored) {
                    ObjectNode object = JsonNodeFactory.instance.objectNode();
                    value.forEach((k, v) -> object.set(k, v.accept(this, null)));
                    return object;
                }

                @Override
                public JsonNode visitString(String value, Void ignored) {
                    return JsonNodeFactory.instance.textNode(value);
                }

                @Override
                public JsonNode visitUndefined(Void ignored) {
                    return JsonNodeFactory.instance.nullNode();
                }
            }, null);
        }
    }

    public interface DataModificationChecks<BE> {
        static <BE> DataModificationChecks<BE> none() {
            return new DataModificationChecks<BE>() {
            };
        }

        default void preCreate(DataEntity.Blueprint blueprint, Transaction<BE> transaction) {

        }

        default void postCreate(BE dataEntity, Transaction<BE> transaction) {

        }

        default void preUpdate(BE dataEntity, DataEntity.Update update, Transaction<BE> transaction) {

        }

        default void postUpdate(BE dataEntity, Transaction<BE> transaction) {

        }

        default void preDelete(BE dataEntity, Transaction<BE> transaction) {

        }

        default void postDelete(BE dataEntity, Transaction<BE> transaction) {

        }
    }
}
