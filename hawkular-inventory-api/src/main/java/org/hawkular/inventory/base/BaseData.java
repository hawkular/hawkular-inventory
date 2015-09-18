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

import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.api.filters.With.id;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Log;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ValidationException;
import org.hawkular.inventory.api.ValidationException.ValidationMessage;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.ShallowStructuredData;

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


    public static final class Read<BE, R extends DataEntity.Role> extends Traversal<BE, DataEntity>
            implements Data.Read<R> {
        public Read(TraversalContext<BE, DataEntity> context) {
            super(context);
        }

        @Override
        public Data.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Data.Single get(R role) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(role.name())).get());
        }
    }

    public static final class ReadWrite<BE, R extends DataEntity.Role>
            extends Mutator<BE, DataEntity, DataEntity.Blueprint<R>, DataEntity.Update, R>
            implements Data.ReadWrite<R> {

        public ReadWrite(TraversalContext<BE, DataEntity> context) {
            super(context);
        }

        @Override
        protected String getProposedId(DataEntity.Blueprint blueprint) {
            return blueprint.getRole().name();
        }

        @Override
        protected EntityAndPendingNotifications<DataEntity> wireUpNewEntity(BE entity,
                DataEntity.Blueprint<R> blueprint, CanonicalPath parentPath, BE parent) {

            DataEntity data = new DataEntity(parentPath, blueprint.getRole(), blueprint.getValue());

            Validator.validate(context, blueprint.getValue(), entity);

            BE value = context.backend.persist(blueprint.getValue());

            //don't report this relationship, it is implicit
            //also, don't run the RelationshipRules checks - we're in the "privileged code" that is allowed to do
            //this
            context.backend.relate(entity, value, hasData.name(), null);

            return new EntityAndPendingNotifications<>(data, Collections.emptyList());
        }

        @Override
        public Data.Single create(DataEntity.Blueprint<R> data) {
            return new Single<>(context.replacePath(doCreate(data)));
        }

        @Override
        protected void cleanup(R role, BE entityRepresentation) {
            cleanup(context, entityRepresentation);
        }

        @Override
        protected void preUpdate(R r, BE entityRepresentation, DataEntity.Update update) {
            preUpdate(context, entityRepresentation, update);
        }

        @Override
        public Data.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Data.Single get(R role) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(role.name())).get());
        }

        private static <BE> void cleanup(TraversalContext<BE, DataEntity> context, BE entityRepresentation) {
            Set<BE> rels = context.backend.getRelationships(entityRepresentation, Relationships.Direction.outgoing,
                    hasData.name());

            if (rels.isEmpty()) {
                Log.LOGGER.wNoDataAssociatedWithEntity(context.backend.extractCanonicalPath(entityRepresentation));
                return;
            }

            BE dataRel = rels.iterator().next();

            BE structuredData = context.backend.getRelationshipTarget(dataRel);

            context.backend.deleteStructuredData(structuredData);
            context.backend.delete(dataRel);
        }

        private static <BE> void preUpdate(TraversalContext<BE, DataEntity> context, BE entityRepresentation,
                DataEntity.Update update) {
            Validator.validate(context, update.getValue(), entityRepresentation);
        }
    }

    public static final class Single<BE>
            extends SingleEntityFetcher<BE, DataEntity, DataEntity.Update>
            implements Data.Single {

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
        public StructuredData flatData(RelativePath dataPath) {
            return loadEntity((b, e) -> {
                BE dataEntity = context.backend.descendToData(b, dataPath);
                return dataEntity == null ? null : context.backend.convert(dataEntity, ShallowStructuredData.class)
                        .getData();
            });
        }

        @Override
        protected void cleanup(BE deletedEntity) {
            ReadWrite.cleanup(context, deletedEntity);
        }

        @Override
        protected void preUpdate(BE updatedEntity, DataEntity.Update update) {
            ReadWrite.preUpdate(context, updatedEntity, update);
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
            return loadEntities(pager, (b, e) -> {
                BE dataEntity = context.backend.descendToData(b, dataPath);
                return context.backend.convert(dataEntity, StructuredData.class);
            });
        }

        @Override
        public Page<StructuredData> flatData(RelativePath dataPath, Pager pager) {
            return loadEntities(pager, (b, e) -> {
                BE dataEntity = context.backend.descendToData(b, dataPath);
                return context.backend.convert(dataEntity, ShallowStructuredData.class).getData();
            });
        }
    }

    public static final class Validator {

        private static final JsonValidator VALIDATOR = JsonSchemaFactory.newBuilder()
                .setReportProvider(new ListReportProvider(LogLevel.INFO, LogLevel.FATAL)).freeze().getValidator();

        public static <BE> void validate(TraversalContext<BE, DataEntity> context, StructuredData data, BE dataEntity) {
            CanonicalPath path = context.backend.extractCanonicalPath(dataEntity);

            DataEntity.Role role = path.ids().getDataRole();

            if (role.isSchema()) {
                try {
                    JsonNode schema = new JsonNodeReader(new ObjectMapper())
                            .fromInputStream(BaseData.class.getResourceAsStream("/json-meta-schema.json"));

                    CanonicalPath dataPath = context.backend.extractCanonicalPath(dataEntity);

                    validate(dataPath, convert(data), schema);
                } catch (IOException e) {
                    throw new IllegalStateException("Could not load the embedded JSON Schema meta-schema.");
                }
            } else {
                validateIfSchemaFound(context, data, dataEntity, Query.path().with(role.navigateToSchema()).get());
            }
        }

        private static <BE> void validateIfSchemaFound(TraversalContext<BE, DataEntity> context, StructuredData data,
                BE dataEntity, Query query) {

            BE possibleSchema = context.backend.traverseToSingle(dataEntity, query);
            if (possibleSchema == null) {
                //no schema means anything is OK
                return;
            }

            DataEntity schemaEntity = context.backend.convert(possibleSchema, DataEntity.class);

            CanonicalPath dataPath = context.backend.extractCanonicalPath(dataEntity);

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
                throw new ValidationException(dataPath, Collections.emptyList(), e);
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
}
