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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.rest.Security;

/**
 * @author Jirka Kremser
 * @since 0.1.0
 */
public class RelationshipJacksonSerializer extends JsonSerializer<Relationship> {
    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_SOURCE = "source";
    public static final String FIELD_TARGET = "target";

    /**
     * <pre>compact:
     * {
     *   "id": "1337",
     *   "source": "/tenant",
     *   "name": "contains",
     *   "target": "/environments/test"
     * }</pre>
     *
     * <pre>embedded:
     * {
     *   "@context": "http://hawkular.org/inventory/0.1.0/relationship.jsonld",
     *   "id": "1337",
     *   "name": "contains",
     *   "source": {
     *      id: "/tenant/28026b36-8fe4-4332-84c8-524e173a68bf",
     *      shortId: "28026b36-8fe4-4332-84c8-524e173a68bf",
     *      type: "Tenant"
     *   },
     *   "target": {
     *      id: "/environments/test",
     *      shortId: "test",
     *      type: "Environment"
     *   }
     * }</pre>
     */
    @Override
    public void serialize(Relationship relationship, JsonGenerator jsonGenerator, SerializerProvider
            serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeFieldName(FIELD_ID);
        jsonGenerator.writeString(relationship.getId());

        jsonGenerator.writeFieldName(FIELD_NAME);
        jsonGenerator.writeString(relationship.getName());

        jsonGenerator.writeFieldName(FIELD_SOURCE);
        jsonGenerator.writeString(Security.getStableId(relationship.getSource()));

        jsonGenerator.writeFieldName(FIELD_TARGET);
        jsonGenerator.writeString(Security.getStableId(relationship.getTarget()));

        jsonGenerator.writeEndObject();
    }
}
