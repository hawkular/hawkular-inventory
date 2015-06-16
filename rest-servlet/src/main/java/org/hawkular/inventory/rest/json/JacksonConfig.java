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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import org.hawkular.inventory.api.model.Relationship;

/**
 * Created by jkremser on 6/11/15.
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonConfig implements ContextResolver<ObjectMapper> {


    @Override
    public ObjectMapper getContext(Class<?> aClass) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule relationshipModule = new SimpleModule("RelationshipModule",
                                      new Version(0, 1, 0, null, "org.hawkular.inventory", "inventory-rest-api"));
        relationshipModule.addSerializer(Relationship.class, new RelationshipJacksonSerializer());
        relationshipModule.addDeserializer(Relationship.class, new RelationshipJacksonDeserializer());
        mapper.registerModule(relationshipModule);
        return mapper;
    }
}
