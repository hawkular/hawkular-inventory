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
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.json.mixins.AbstractElementMixin;
import org.hawkular.inventory.json.mixins.CanonicalPathMixin;
import org.hawkular.inventory.json.mixins.DataEntityMixin;
import org.hawkular.inventory.json.mixins.EntityBlueprintMixin;
import org.hawkular.inventory.json.mixins.EnvironmentMixin;
import org.hawkular.inventory.json.mixins.FeedMixin;
import org.hawkular.inventory.json.mixins.MetricMixin;
import org.hawkular.inventory.json.mixins.MetricTypeMixin;
import org.hawkular.inventory.json.mixins.OperationTypeMixin;
import org.hawkular.inventory.json.mixins.RelationshipMixin;
import org.hawkular.inventory.json.mixins.RelativePathMixin;
import org.hawkular.inventory.json.mixins.ResourceMixin;
import org.hawkular.inventory.json.mixins.ResourceTypeMixin;
import org.hawkular.inventory.json.mixins.StructuredDataMixin;
import org.hawkular.inventory.json.mixins.TenantMixin;
import org.hawkular.inventory.json.mixins.TenantlessCanonicalPathMixin;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A helper class to configure the Jackson's object mapper to correctly serialize and deserialize inventory entities
 * and paths.
 *
 * <p>By default, the paths are serialized and deserialized with the tenant ID retained. There is an option to not
 * include them in the serialized form by using the {@link TenantlessPathSerializer} and
 * {@link DetypedPathDeserializer} (by reconfiguring the object mapper to use {@link TenantlessCanonicalPathMixin}
 * and {@link org.hawkular.inventory.json.mixins.TenantlessRelativePathMixin} to serialize the {@link CanonicalPath}
 * or {@link RelativePath} respectively. But beware of the need to pre-configure the deserializer with contextual
 * information that it will use to inject the tenant id into the deserialized path instances.
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
        objectMapper.addMixIn(DataEntity.class, DataEntityMixin.class);
        objectMapper.addMixIn(OperationType.class, OperationTypeMixin.class);
        objectMapper.addMixIn(Entity.Blueprint.class, EntityBlueprintMixin.class);
    }
}
