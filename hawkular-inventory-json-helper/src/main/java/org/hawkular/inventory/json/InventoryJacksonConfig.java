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
package org.hawkular.inventory.json;

import org.hawkular.inventory.api.FilterFragment;
import org.hawkular.inventory.api.PathFragment;
import org.hawkular.inventory.api.QueryFragment;
import org.hawkular.inventory.api.filters.Contained;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Incorporated;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.PageContext;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.NoopFilter;
import org.hawkular.inventory.base.spi.RecurseFilter;
import org.hawkular.inventory.base.spi.SwitchElementType;
import org.hawkular.inventory.json.mixins.filters.ContainedMixin;
import org.hawkular.inventory.json.mixins.filters.DefinedMixin;
import org.hawkular.inventory.json.mixins.filters.IncorporatedMixin;
import org.hawkular.inventory.json.mixins.filters.NoopFilterMixin;
import org.hawkular.inventory.json.mixins.filters.RecurseFilterMixin;
import org.hawkular.inventory.json.mixins.filters.RelatedMixin;
import org.hawkular.inventory.json.mixins.filters.RelationWithMixin;
import org.hawkular.inventory.json.mixins.filters.SwitchElementTypeMixin;
import org.hawkular.inventory.json.mixins.filters.WithMixin;
import org.hawkular.inventory.json.mixins.model.AbstractElementMixin;
import org.hawkular.inventory.json.mixins.model.CanonicalPathMixin;
import org.hawkular.inventory.json.mixins.model.DataEntityMixin;
import org.hawkular.inventory.json.mixins.model.EntityBlueprintMixin;
import org.hawkular.inventory.json.mixins.model.EnvironmentMixin;
import org.hawkular.inventory.json.mixins.model.FeedMixin;
import org.hawkular.inventory.json.mixins.model.InventoryStructureMixin;
import org.hawkular.inventory.json.mixins.model.MetricMixin;
import org.hawkular.inventory.json.mixins.model.MetricTypeMixin;
import org.hawkular.inventory.json.mixins.model.OperationTypeMixin;
import org.hawkular.inventory.json.mixins.model.RelationshipMixin;
import org.hawkular.inventory.json.mixins.model.RelativePathMixin;
import org.hawkular.inventory.json.mixins.model.ResourceMixin;
import org.hawkular.inventory.json.mixins.model.ResourceTypeMixin;
import org.hawkular.inventory.json.mixins.model.StructuredDataMixin;
import org.hawkular.inventory.json.mixins.model.TenantMixin;
import org.hawkular.inventory.json.mixins.model.TenantlessCanonicalPathMixin;
import org.hawkular.inventory.json.mixins.model.TenantlessRelativePathMixin;
import org.hawkular.inventory.json.mixins.paging.OrderMixin;
import org.hawkular.inventory.json.mixins.paging.PageContextMixin;
import org.hawkular.inventory.json.mixins.paging.PagerMixin;
import org.hawkular.inventory.json.mixins.query.FilterFragmentMixin;
import org.hawkular.inventory.json.mixins.query.PathFragmentMixin;
import org.hawkular.inventory.json.mixins.query.QueryFragmentMixin;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A helper class to configure the Jackson's object mapper to correctly serialize and deserialize inventory entities
 * and paths.
 *
 * <p>By default, the paths are serialized and deserialized with the tenant ID retained. There is an option to not
 * include them in the serialized form by using the {@link TenantlessPathSerializer} and
 * {@link DetypedPathDeserializer} (by reconfiguring the object mapper to use {@link TenantlessCanonicalPathMixin}
 * and {@link TenantlessRelativePathMixin} to serialize the {@link CanonicalPath}
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
        /**
         * Model
         */
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
        objectMapper.addMixIn(InventoryStructure.class, InventoryStructureMixin.class);

        /**
         * Query
         */
        objectMapper.addMixIn(QueryFragment.class, QueryFragmentMixin.class);
        objectMapper.addMixIn(FilterFragment.class, FilterFragmentMixin.class);
        objectMapper.addMixIn(PathFragment.class, PathFragmentMixin.class);

        /**
         * Filter
         */
        objectMapper.addMixIn(Contained.class, ContainedMixin.class);
        objectMapper.addMixIn(Defined.class, DefinedMixin.class);
        objectMapper.addMixIn(Incorporated.class, IncorporatedMixin.class);
        objectMapper.addMixIn(Related.class, RelatedMixin.class);
        objectMapper.addMixIn(RelationWith.class, RelationWithMixin.class);
        objectMapper.addMixIn(RelationWith.Ids.class, RelationWithMixin.IdsMixin.class);
        objectMapper.addMixIn(RelationWith.PropertyValues.class, RelationWithMixin.PropertyValuesMixin.class);
        objectMapper.addMixIn(RelationWith.SourceOrTargetOfType.class,
                RelationWithMixin.SourceOrTargetOfTypeMixin.class);
        objectMapper.addMixIn(RelationWith.SourceOfType.class, RelationWithMixin.SourceOfTypeMixin.class);
        objectMapper.addMixIn(RelationWith.TargetOfType.class, RelationWithMixin.TargetOfTypeMixin.class);
        objectMapper.addMixIn(With.Ids.class, WithMixin.IdsMixin.class);
        objectMapper.addMixIn(With.Types.class, WithMixin.TypesMixin.class);
        objectMapper.addMixIn(With.CanonicalPaths.class, WithMixin.CanonicalPathsMixin.class);
        objectMapper.addMixIn(With.RelativePaths.class, WithMixin.RelativePathsMixin.class);
        objectMapper.addMixIn(With.PropertyValues.class, WithMixin.PropertyValuesMixin.class);
        objectMapper.addMixIn(With.DataValued.class, WithMixin.DataValuedMixin.class);
        objectMapper.addMixIn(With.DataAt.class, WithMixin.DataAtMixin.class);
        objectMapper.addMixIn(With.DataOfTypes.class, WithMixin.DataOfTypesMixin.class);

        objectMapper.addMixIn(NoopFilter.class, NoopFilterMixin.class);
        objectMapper.addMixIn(RecurseFilter.class, RecurseFilterMixin.class);
        objectMapper.addMixIn(SwitchElementType.class, SwitchElementTypeMixin.class);

        /**
         * Paging
         */
        objectMapper.addMixIn(Order.class, OrderMixin.class);
        objectMapper.addMixIn(PageContext.class, PageContextMixin.class);
        objectMapper.addMixIn(Pager.class, PagerMixin.class);
    }
}
