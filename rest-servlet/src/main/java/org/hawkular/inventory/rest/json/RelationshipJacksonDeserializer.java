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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.base.spi.CanonicalPath;
import org.hawkular.inventory.rest.Security;

/**
 *
 * This class should contain the inverse function for
 * {@link RelationshipJacksonSerializer#serialize(org.hawkular.inventory.api.model.Relationship,
 * com.fasterxml.jackson.core.JsonGenerator, com.fasterxml.jackson.databind.SerializerProvider)}
 *
 * @author Jirka Kremser
 * @since 0.1.0
 */
public class RelationshipJacksonDeserializer extends JsonDeserializer<Relationship> {

    @Override
    public Relationship deserialize(JsonParser jp, DeserializationContext deserializationContext) throws
            IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        String id = node.get("id").asText();
        String name = node.get("name").asText();
        String sourcePath = node.get("source").asText();
        String targetPath = node.get("target").asText();
        CanonicalPath sourceCPath = Security.getCanonicalPath(sourcePath);
        CanonicalPath targetCPath = Security.getCanonicalPath(targetPath);

        return new Relationship(id, name, sourceCPath.toEntity(), targetCPath.toEntity());
    }
}
