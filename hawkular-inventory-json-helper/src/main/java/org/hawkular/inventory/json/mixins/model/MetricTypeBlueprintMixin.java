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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.paths.CanonicalPath;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Remove this mix-in once we drop the support for the old way of serializing the metric data type.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 * @deprecated since inception :)
 */
@Deprecated
@JsonSerialize(using = MetricTypeBlueprintMixin.Serializer.class)
@JsonDeserialize(using = MetricTypeBlueprintMixin.Deserializer.class)
public final class MetricTypeBlueprintMixin {

    /**
     * @deprecated don't use this once "type" does not need to be in the JSON
     */
    @Deprecated
    public static final class Serializer extends JsonSerializer<MetricType.Blueprint> {
        @Override public void serialize(MetricType.Blueprint value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("id", value.getId());
            gen.writeStringField("name", value.getName());
            if (value.getCollectionInterval() != null) {
                gen.writeNumberField("collectionInterval", value.getCollectionInterval());
            } else {
                gen.writeNullField("collectionInterval");
            }
            gen.writeStringField("type", value.getMetricDataType().name());
            gen.writeStringField("metricDataType", value.getMetricDataType().getDisplayName());
            gen.writeStringField("unit", value.getUnit().name());
            writeNonEmpty(value.getProperties(), "properties", gen);
            writeNonEmpty(value.getIncomingRelationships(), "incoming", gen);
            writeNonEmpty(value.getOutgoingRelationships(), "outgoing", gen);
            gen.writeEndObject();
        }
    }

    /**
     * @deprecated don't use this once "type" does not need to be in the JSON
     */
    @Deprecated
    public static final class Deserializer extends JsonDeserializer<MetricType.Blueprint> {
        @Override public MetricType.Blueprint deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            String id = null;
            String name = null;
            Long collectionInterval = null;
            MetricDataType type = null;
            MetricDataType metricDataType = null;
            MetricUnit unit = null;
            Map<String, Object> properties = null;
            Map<String, Set<CanonicalPath>> incomingRelationships = null;
            Map<String, Set<CanonicalPath>> outgoingRelationships = null;

            while (p.nextValue() != null) {
                if (p.getCurrentToken() == JsonToken.END_OBJECT) {
                    break;
                }
                String field = p.getCurrentName();
                switch (field) {
                    case "id" :
                        id = p.readValueAs(String.class);
                        break;
                    case "name":
                        name = p.readValueAs(String.class);
                        break;
                    case "collectionInterval":
                        collectionInterval = p.readValueAs(Long.class);
                        break;
                    case "type":
                        type = MetricDataType.valueOf(p.readValueAs(String.class));
                        break;
                    case "metricDataType":
                        metricDataType = p.readValueAs(MetricDataType.class);
                        break;
                    case "unit":
                        unit = p.readValueAs(MetricUnit.class);
                        break;
                    case "properties":
                        properties = p.readValueAs(new TypeReference<Map<String, Object>>() {});
                        break;
                    case "incoming":
                        incomingRelationships = p.readValueAs(new TypeReference<Map<String, Set<CanonicalPath>>>() {});
                        break;
                    case "outgoing":
                        outgoingRelationships = p.readValueAs(new TypeReference<Map<String, Set<CanonicalPath>>>() {});
                        break;
                }
            }

            if (metricDataType == null) {
                metricDataType = type;
            }

            return MetricType.Blueprint.builder(metricDataType).withId(id).withName(name)
                    .withInterval(collectionInterval).withUnit(unit).withProperties(nonNull(properties))
                    .withIncomingRelationships(nonNull(incomingRelationships))
                    .withOutgoingRelationships(nonNull(outgoingRelationships))
                    .build();
        }
    }

    private static void writeNonEmpty(Map<?, ?> map, String fieldName, JsonGenerator gen) throws IOException {
        if (map != null && !map.isEmpty()) {
            gen.writeObjectField(fieldName, map);
        }
    }

    private static <K, V> Map<K, V> nonNull(Map<K, V> map) {
        return map == null ? Collections.emptyMap() : map;
    }
}
