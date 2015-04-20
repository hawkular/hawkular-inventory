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
import org.hawkular.inventory.api.model.Relationship;

import java.lang.reflect.Type;

/**
 * @author jkremser
 *
 * See the JSON-LD example in {@link org.hawkular.inventory.rest.json.RelationshipSerializer}
 */
public class RelationshipDeserializer implements JsonDeserializer<Relationship> {
    @Override
    public Relationship deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext
            jsonDeserializationContext) throws JsonParseException {
        JsonObject json = jsonElement.getAsJsonObject();
        String id = json.getAsJsonPrimitive("inv:shortId").getAsString();
        String name = json.getAsJsonPrimitive("inv:label").getAsString();
        Entity source = null;
        Entity target = null;

        Relationship retValue = new Relationship(id, name, source, target);
        return retValue;
    }
}
