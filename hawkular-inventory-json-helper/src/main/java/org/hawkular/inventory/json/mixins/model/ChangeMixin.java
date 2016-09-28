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
package org.hawkular.inventory.json.mixins.model;

import java.io.IOException;
import java.time.Instant;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.Change;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.type.SimpleType;

/**
 * @author Lukas Krejci
 * @since 0.20.0
 */
@JsonSerialize(using = ChangeMixin.Serializer.class)
@JsonDeserialize(using = ChangeMixin.Deserializer.class)
public final class ChangeMixin {

    public static final class Serializer extends JsonSerializer<Change<?>> {

        @Override public void serialize(Change<?> value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("time", value.getTime().toEpochMilli());
            gen.writeStringField("action", value.getAction().asEnum().name().toLowerCase());
            gen.writeObjectField("context", value.getActionContext());
            gen.writeEndObject();
        }
    }

    public static final class Deserializer extends JsonDeserializer<Change<?>> {

        @Override public Change<?> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {

            Instant time = null;
            String action = null;
            JsonNode actionContext = null;

            String currentField = "";
            readFields: while (p.nextToken() != null) {
                switch (p.getCurrentToken()) {
                    case END_OBJECT:
                        break readFields;
                    case FIELD_NAME:
                        currentField = p.getCurrentName();
                        break;
                    default:
                        switch (currentField) {
                            case "time":
                                long timestamp = p.readValueAs(Long.class);
                                time = Instant.ofEpochMilli(timestamp);
                                break;
                            case "action":
                                action = p.readValueAs(String.class);
                                break;
                            case "context":
                                actionContext = p.readValueAsTree();
                                break;
                        }
                }
            }

            if (time == null || action == null || actionContext == null) {
                throw new JsonParseException("Incomplete change object.", JsonLocation.NA);
            }

            CanonicalPath cp = findCp(action, actionContext);
            @SuppressWarnings("unchecked")
            Class<? extends Entity<?, ?>> et = (Class<? extends Entity<?, ?>>)
                    Inventory.types().bySegment(cp.getSegment().getElementType()).getElementType();

            return construct(ctxt, (Class) et, action, time, actionContext);
        }

        private CanonicalPath findCp(String action, JsonNode context) {
            String cpStr;
            if ("updated".equals(action)) {
                cpStr = context.get("originalEntity").get("path").asText();
            } else {
                cpStr = context.get("path").asText();
            }

            return CanonicalPath.fromString(cpStr);
        }

        private <E extends Entity<B, U>, U extends Entity.Update, B extends Blueprint> Change<E>
        construct(DeserializationContext ctx, Class<E> entityType, String action, Instant time, JsonNode contextNode)
                throws IOException {

            JsonParser p = ctx.getParser();

            E entity;
            switch (action) {
                case "created":
                    entity = p.getCodec().treeToValue(contextNode, entityType);
                    return new Change<>(time, Action.created(), entity);
                case "updated":
                    Class<U> updateType = Inventory.types().byElement(entityType).getUpdateType();
                    JavaType updateJavaType = ctx.getTypeFactory()
                            .constructSimpleType(Action.Update.class, Action.Update.class,
                                    new JavaType[] {SimpleType.construct(entityType), SimpleType.construct(updateType)});

                    Action.Update<E, U> update = p.getCodec()
                            .readValue(p.getCodec().treeAsTokens(contextNode), updateJavaType);
                    return new Change<E>(time, Action.updated(), update);
                case "deleted":
                    entity = p.getCodec().treeToValue(contextNode, entityType);
                    return new Change<>(time, Action.deleted(), entity);
            }

            throw new JsonParseException("Unknown action in change descriptor: " + action, JsonLocation.NA);
        }
    }
}
