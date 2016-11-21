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

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.paths.RelativePath;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
public final class InventoryStructureSerializer extends JsonSerializer<InventoryStructure<?>> {
    @Override public void serialize(InventoryStructure<?> inventoryStructure, JsonGenerator jsonGenerator,
                                    SerializerProvider serializerProvider) throws IOException {

        Object blueprint = inventoryStructure.getRoot();
        InventoryStructure.EntityType blueprintType = InventoryStructure.EntityType.ofBlueprint(blueprint.getClass());
        if (blueprintType == null) {
            throw new IllegalArgumentException("Unsupported type of root element: " + blueprint);
        }

        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("type", blueprintType.name());
        jsonGenerator.writeObjectField("data", inventoryStructure.getRoot());

        jsonGenerator.writeFieldName("children");
        jsonGenerator.writeStartObject();
        serializeLevel(inventoryStructure, RelativePath.empty(), jsonGenerator);
        jsonGenerator.writeEndObject();
        jsonGenerator.writeEndObject();
    }

    private void serializeLevel(InventoryStructure<?> structure, RelativePath.Extender root, JsonGenerator gen)
            throws IOException {
        RelativePath rootPath = root.get();
        for (InventoryStructure.EntityType entityType : InventoryStructure.EntityType.values()) {
            @SuppressWarnings("unchecked")
            List<? extends Entity.Blueprint> children = getChildren(structure, rootPath,
                    (Class) entityType.elementType);

            if (!children.isEmpty()) {
                gen.writeFieldName(entityType.name());
                gen.writeStartArray();

                for (Entity.Blueprint bl : children) {
                    gen.writeStartObject();

                    gen.writeObjectField("data", bl);

                    gen.writeFieldName("children");
                    gen.writeStartObject();
                    serializeLevel(structure, rootPath.modified().extend(entityType.segmentType, bl.getId()), gen);
                    gen.writeEndObject();

                    gen.writeEndObject();
                }
                gen.writeEndArray();
            }
        }
    }

    private <E extends Entity<B, ?>, B extends Entity.Blueprint> List<B> getChildren(InventoryStructure<?> structure,
                                                                      RelativePath path, Class<E> type) {
        try (Stream<B> s = structure.getChildren(path, type)) {
            return s.collect(toList());
        }
    }
}
