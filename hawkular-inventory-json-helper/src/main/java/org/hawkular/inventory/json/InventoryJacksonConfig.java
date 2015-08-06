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
package org.hawkular.inventory.json;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.json.mixins.AbstractElementMixin;
import org.hawkular.inventory.json.mixins.CanonicalPathMixin;
import org.hawkular.inventory.json.mixins.EnvironmentMixin;
import org.hawkular.inventory.json.mixins.FeedMixin;
import org.hawkular.inventory.json.mixins.MetricMixin;
import org.hawkular.inventory.json.mixins.MetricTypeMixin;
import org.hawkular.inventory.json.mixins.RelationshipMixin;
import org.hawkular.inventory.json.mixins.RelativePathMixin;
import org.hawkular.inventory.json.mixins.ResourceMixin;
import org.hawkular.inventory.json.mixins.ResourceTypeMixin;
import org.hawkular.inventory.json.mixins.StructuredDataMixin;
import org.hawkular.inventory.json.mixins.TenantMixin;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A helper class to configure the Jackson's object mapper to correctly serialize and deserialize inventory entities
 * and paths.
 *
 * <p>Beware of the {@link PathDeserializer} - it might not be what you want unless you are able to provide it with
 * necessary contextual information prior to deserialization. If not, you need to reconfigure the object mapper to not
 * use the the {@link PathDeserializer} to deserialize neither {@link CanonicalPath} nor {@link RelativePath} and
 * deserialize the paths manually when the contextual information is available.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class InventoryJacksonConfig {

    private InventoryJacksonConfig() {
    }

    /**
     * Configures the provided object mapper with mixins that define the serialization and deserialization behavior
     * for inventory entities.
     *
     * @param objectMapper the jackson object mapper
     */
    public static void configure(ObjectMapper objectMapper) {
        objectMapper.addMixIn(AbstractElement.class, AbstractElementMixin.class);
        objectMapper.addMixIn(CanonicalPath.class, CanonicalPathMixin.class);
        objectMapper.addMixIn(Environment.class, EnvironmentMixin.class);
        objectMapper.addMixIn(Feed.class, FeedMixin.class);
        objectMapper.addMixIn(Metric.class, MetricMixin.class);
        objectMapper.addMixIn(MetricType.class, MetricTypeMixin.class);
        objectMapper.addMixIn(Relationship.class, RelationshipMixin.class);
        objectMapper.addMixIn(RelativePath.class, RelativePathMixin.class);
        objectMapper.addMixIn(Resource.class, ResourceMixin.class);
        objectMapper.addMixIn(ResourceType.class, ResourceTypeMixin.class);
        objectMapper.addMixIn(Tenant.class, TenantMixin.class);
        objectMapper.addMixIn(StructuredData.class, StructuredDataMixin.class);
    }
}
