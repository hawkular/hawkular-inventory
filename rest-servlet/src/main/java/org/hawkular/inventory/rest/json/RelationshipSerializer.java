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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.rest.RestEnvironments;
import org.hawkular.inventory.rest.RestMetricTypes;
import org.hawkular.inventory.rest.RestMetrics;
import org.hawkular.inventory.rest.RestRelationships;
import org.hawkular.inventory.rest.RestResourceTypes;
import org.hawkular.inventory.rest.RestResources;
import org.hawkular.inventory.rest.RestTenants;

import javax.ws.rs.core.UriInfo;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author jkremser
 * @since 0.0.2
 *
 * Example:<pre>
 * {
 *   "@context": {
 *     "inv": "http://hawkular.org/inventory/0.1/",
 *     "baseUrl": "http://127.0.0.1/hawkular/inventory/"
 *   },
 *   "@id": "baseUrl:acme/relationships/abc",
 *   "inv:shortId": "abc",
 *   "@type": "inv:Relationship",
 *   "inv:source": {
 *       "@id": "baseUrl:tenants/acme",
 *       "@type": "inv:Tenant",
 *       "inv:shortId": "acme"
 *   }
 *   "inv:label": "contains",
 *   "inv:target": {
 *       "@id": "baseUrl:acme/environments/test",
 *       "@type": "inv:Environment",
 *       "inv:shortId": "test"
 *   }
 *   "inv:properties": {
 *       "key1": "value1",
 *       "key2": 12
 *   }
 * }
 * </pre>
 *
 */
public class RelationshipSerializer implements JsonSerializer<Relationship> {
    public static final String VOCAB_PREFIX = "inv";

    private static final JsonObject CONTEXT = new JsonObject();
    private static final String ONTOLOGY_URL = "http://hawkular.org/inventory/";

    private final UriInfo info;

    public RelationshipSerializer(UriInfo info, String ontologyVersion) {
        this.info = info;
        CONTEXT.addProperty(VOCAB_PREFIX, ONTOLOGY_URL + ontologyVersion);
        CONTEXT.addProperty("baseUrl", info.getBaseUri().toString());
    }

    @Override
    public JsonElement serialize(Relationship relationship, Type type, JsonSerializationContext
            jsonSerializationContext) {
        JsonObject object = new JsonObject();
        object.add("@context", CONTEXT);
        object.addProperty("@id", "baseUrl:" + RestRelationships.getUrl(relationship.getId()));
        object.addProperty(VOCAB_PREFIX + ":shortId", relationship.getId());
        object.addProperty("@type", "inv:Relationship");
        object.add(VOCAB_PREFIX + ":source", serializeIncidenceVertex(relationship.getSource()));
        object.addProperty(VOCAB_PREFIX + ":label", relationship.getName());
        object.add(VOCAB_PREFIX + ":target", serializeIncidenceVertex(relationship.getTarget()));
        object.add(VOCAB_PREFIX + ":properties", serializeProperties(relationship.getProperties(),
                jsonSerializationContext));
        return object;
    }

    private JsonElement serializeProperties(Map<String, Object> properties, JsonSerializationContext
            jsonSerializationContext) {
        JsonObject props = new JsonObject();
        if (properties != null) {
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                props.add(property.getKey(), jsonSerializationContext.serialize(property.getValue()));
            }
        }
        return props;
    }

    /**
     * Example:<pre>
     *   "inv:target": {
     *       "@id": "baseUrl:acme/environments/test",
     *       "@type": "inv:Environment",
     *       "inv:shortId": "test"
     *   }
     * </pre>
     * @param vertex source or target of the edge
     * @return serialized vertex to JSON
     */
    private JsonElement serializeIncidenceVertex(Entity vertex) {
        JsonObject props = new JsonObject();
        props.addProperty("@id", "baseUrl:" + getUri(vertex));
        props.addProperty("@type", VOCAB_PREFIX + ":" + vertex.getClass().getSimpleName());
        props.addProperty(VOCAB_PREFIX + ":shortId", vertex.getId());
        return props;
    }

    private String getUri(Entity vertex) {
        if (vertex.getClass() == Tenant.class) {
            return RestTenants.getUrl((Tenant) vertex);
        } else if (vertex.getClass() == Environment.class) {
            return RestEnvironments.getUrl((Environment) vertex);
        } else if (vertex.getClass() == Resource.class) {
            return RestResources.getUrl((Resource) vertex);
        } else if (vertex.getClass() == Metric.class) {
            return RestMetrics.getUrl((Metric) vertex);
        } else if (vertex.getClass() == ResourceType.class) {
            return RestResourceTypes.getUrl((ResourceType) vertex);
        } else if (vertex.getClass() == MetricType.class) {
            return RestMetricTypes.getUrl((MetricType) vertex);
        }
        throw new IllegalStateException("Unknown entity type: " + vertex.getClass().getName());
    }
}
