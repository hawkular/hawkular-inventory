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
package org.hawkular.inventory.json;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.InventoryStructure;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
public class InventoryStructureDeserializer extends JsonDeserializer<InventoryStructure.Offline<?>> {

    public static final String LEGAL_ENTITY_TYPES = Stream.of(InventoryStructure.EntityType.values())
            .map(Enum::name)
            .collect(Collectors.joining("', '", "'", "'"));

    @Override public InventoryStructure.Offline<?> deserialize(JsonParser jsonParser,
                                                               DeserializationContext deserializationContext)
            throws IOException {


        JsonNode tree = jsonParser.readValueAsTree();
        if (tree == null) {
            throw new JsonParseException("Inventory structure expected but got nothing.",
                    jsonParser.getCurrentLocation());
        }

        JsonToken token = tree.asToken();
        if (token != JsonToken.START_OBJECT) {
            throw new JsonParseException("Expected object but got " + token.asString(), JsonLocation.NA);
        }

        JsonNode typeNode = tree.get("type");
        if (!typeNode.isTextual()) {
            throw new JsonParseException("'type' must be a text", JsonLocation.NA);
        }

        String typeName = typeNode.textValue();

        InventoryStructure.EntityType type;
        try {
            type = InventoryStructure.EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unrecognized value of 'type'. Supported values are " + LEGAL_ENTITY_TYPES
                    + " but got '" + typeName + "'.", JsonLocation.NA);
        }

        Entity.Blueprint root = deserializationContext.readValue(prepareTraverse(tree.get("data")), type.blueprintType);

        InventoryStructure.Builder bld = InventoryStructure.Offline.of(root);
        parseChildren(tree, bld, deserializationContext);

        return bld.build();
    }

    private void parseChildren(JsonNode root, InventoryStructure.AbstractBuilder<?> bld, DeserializationContext mapper)
            throws IOException {

        JsonNode children = root.get("children");
        if (children == null) {
            return;
        }

        if (!children.isObject()) {
            throw new JsonParseException("The 'children' is supposed to be an object.", JsonLocation.NA);
        } else {
            Iterator<Map.Entry<String, JsonNode>> fields = children.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String typeName = e.getKey();
                JsonNode childrenNode = e.getValue();

                if (!childrenNode.isArray()) {
                    continue;
                }

                InventoryStructure.EntityType type = InventoryStructure.EntityType.valueOf(typeName);

                Iterator<JsonNode> childrenNodes = childrenNode.elements();
                while (childrenNodes.hasNext()) {
                    JsonNode childNode = childrenNodes.next();

                    Entity.Blueprint bl = mapper.readValue(prepareTraverse(childNode.get("data")), type.blueprintType);

                    InventoryStructure.ChildBuilder<?> childBld = bld.startChild(bl);

                    parseChildren(childNode, childBld, mapper);

                    childBld.end();
                }
            }
        }
    }

    private static JsonParser prepareTraverse(JsonNode node) throws IOException {
        JsonParser parser = node.traverse();
        parser.nextToken();
        return parser;
    }
}
