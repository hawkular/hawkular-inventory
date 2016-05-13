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
package org.hawkular.inventory.rest.json;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.rest.cdi.TenantAware;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * @author Jirka Kremser
 * @since 0.1.0
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonConfig implements ContextResolver<ObjectMapper> {

    private ObjectMapper deprecatedObjectMapper;
    private ObjectMapper defaultObjectMapper;
    private EmbeddedObjectMapper embeddedRelationshipsMapper;

    public JacksonConfig() {
        this.deprecatedObjectMapper = new ObjectMapper();
        this.defaultObjectMapper = new ObjectMapper();
        this.embeddedRelationshipsMapper = new EmbeddedObjectMapper();
        initializeObjectMapper(this.deprecatedObjectMapper);
        initializeObjectMapper(this.defaultObjectMapper);
        initializeObjectMapper(this.embeddedRelationshipsMapper);

        SimpleModule relationshipModule = new SimpleModule("RelationshipModule",
                                                           new Version(0, 1, 0, null, "org.hawkular.inventory",
                                                                       "inventory-rest-api"));
        relationshipModule.addSerializer(Relationship.class, new RelationshipJacksonSerializer());
        relationshipModule.addDeserializer(Relationship.class, new RelationshipJacksonDeserializer());

        this.deprecatedObjectMapper.registerModule(relationshipModule);

        this.defaultObjectMapper.addMixIn(CanonicalPath.class, PathSerializationMixin.class);
        this.defaultObjectMapper.addMixIn(RelativePath.class, PathSerializationMixin.class);
        this.defaultObjectMapper.addMixIn(Path.class, PathSerializationMixin.class);
    }

    public static void initializeObjectMapper(ObjectMapper mapper) {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        InventoryJacksonConfig.configure(mapper);
        //need to reconfigure for path serialization
        mapper.addMixIn(CanonicalPath.class, DeprecatedPathSerializationMixin.class);
        mapper.addMixIn(RelativePath.class, DeprecatedPathSerializationMixin.class);
        mapper.addMixIn(Path.class, DeprecatedPathSerializationMixin.class);
    }

    @Override
    public ObjectMapper getContext(Class<?> clazz) {
        if (EmbeddedObjectMapper.class.equals(clazz)) {
            return embeddedRelationshipsMapper;
        } else if (TenantAware.class.equals(clazz)) {
            return defaultObjectMapper;
        } else {
            return deprecatedObjectMapper;
        }
    }
}
