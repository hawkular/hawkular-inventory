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
package org.hawkular.inventory.rest.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import java.lang.reflect.Type;
import java.util.Map;

import static org.hawkular.inventory.rest.json.RelationshipSerializer.VOCAB_PREFIX;

/**
 * @author jkremser
 * since 0.0.2
 *
 * See the JSON-LD example in {@link org.hawkular.inventory.rest.json.RelationshipSerializer}
 */
public class RelationshipDeserializer implements JsonDeserializer<Relationship> {

    public static Map<String, Class<? extends Entity>> entityMap;
    static {
        entityMap.put(Tenant.class.getSimpleName(), Tenant.class);
        entityMap.put(Environment.class.getSimpleName(), Environment.class);
        entityMap.put(ResourceType.class.getSimpleName(), ResourceType.class);
        entityMap.put(MetricType.class.getSimpleName(), MetricType.class);
        entityMap.put(Resource.class.getSimpleName(), Resource.class);
        entityMap.put(Metric.class.getSimpleName(), Metric.class);
    }

    @Override
    public Relationship deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext
            jsonDeserializationContext) throws JsonParseException {
        JsonObject json = jsonElement.getAsJsonObject();
        String id = json.getAsJsonPrimitive(VOCAB_PREFIX + ":shortId").getAsString();
        String name = json.getAsJsonPrimitive(VOCAB_PREFIX + ":label").getAsString();
        Entity source = deserializeEntity(json.getAsJsonObject(VOCAB_PREFIX + ":source"));
        Entity target = deserializeEntity(json.getAsJsonObject(VOCAB_PREFIX + ":target"));

        Relationship retValue = new Relationship(id, name, source, target);
        return retValue;
    }

    private Entity deserializeEntity(JsonObject json) {
        // there is no need to fully re-construct the original object, all is needed is the id. The reason is
        // following: When 'CRUDing' the Relationship it should not be possible to update the incidence entity
        final String type = json.getAsJsonPrimitive("@type").getAsString();
        final String id = json.getAsJsonPrimitive(VOCAB_PREFIX + ":shortId").getAsString();
        if (type.equals(VOCAB_PREFIX + ":" + Tenant.class.getSimpleName())) {
            return new Tenant(id);
        } else if (type.equals(VOCAB_PREFIX + ":" + Environment.class.getSimpleName())) {
            return new Environment(null, id);
        } else if (type.equals(VOCAB_PREFIX + ":" + Resource.class.getSimpleName())) {
            return new Resource(null, null, id, null);
        } else if (type.equals(VOCAB_PREFIX + ":" + Metric.class.getSimpleName())) {
            return new Metric(null, null, id, null);
        } else if (type.equals(VOCAB_PREFIX + ":" + ResourceType.class.getSimpleName())) {
            return new ResourceType(null, id, (String) null);
        } else if (type.equals(VOCAB_PREFIX + ":" + MetricType.class.getSimpleName())) {
            return new MetricType(null, id);
        }
        throw new IllegalStateException("Unknown entity type: " + type);
    }
}
