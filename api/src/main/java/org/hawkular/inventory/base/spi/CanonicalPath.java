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
package org.hawkular.inventory.base.spi;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

/**
 * A simple representation of a "canonical path" to an entity or relationship.
 *
 * <p>Relationships are always directly addressed by their ID, all other fields in the canonical path are ignored for
 * relationships.
 *
 * <p>For entities, a canonical path is the "descent" from tenant through to the entity in question.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class CanonicalPath {
    private final String tenantId;
    private final String environmentId;
    private final String feedId;
    private final String resourceTypeId;
    private final String metricTypeId;
    private final String metricId;
    private final String resourceId;
    private final String relationshipId;

    /**
     * Creates canonical path to the provided element.
     *
     * <p>This can be done simply by examining the object, no backend access is needed for this.
     *
     * @param element the element to provide a path to.
     * @return the canonical path to the element
     */
    public static CanonicalPath of(AbstractElement<?, ?> element) {
        return element.accept(new ElementVisitor<CanonicalPath, Void>() {
            @Override
            public CanonicalPath visitTenant(Tenant tenant, Void parameter) {
                return CanonicalPath.builder().withTenantId(tenant.getId()).build();
            }

            @Override
            public CanonicalPath visitEnvironment(Environment environment, Void parameter) {
                return CanonicalPath.builder().withTenantId(environment.getTenantId())
                        .withEnvironmentId(environment.getId()).build();
            }

            @Override
            public CanonicalPath visitFeed(Feed feed, Void parameter) {
                return CanonicalPath.builder().withTenantId(feed.getTenantId())
                        .withEnvironmentId(feed.getEnvironmentId()).withFeedId(feed.getId()).build();
            }

            @Override
            public CanonicalPath visitMetric(Metric metric, Void parameter) {
                return CanonicalPath.builder().withTenantId(metric.getTenantId())
                        .withEnvironmentId(metric.getEnvironmentId()).withFeedId(metric.getFeedId())
                        .withMetricId(metric.getId()).build();
            }

            @Override
            public CanonicalPath visitMetricType(MetricType type, Void parameter) {
                return CanonicalPath.builder().withTenantId(type.getTenantId())
                        .withMetricTypeId(type.getId()).build();
            }

            @Override
            public CanonicalPath visitResource(Resource resource, Void parameter) {
                return CanonicalPath.builder().withTenantId(resource.getTenantId())
                        .withEnvironmentId(resource.getEnvironmentId()).withFeedId(resource.getFeedId())
                        .withResourceId(resource.getId()).build();
            }

            @Override
            public CanonicalPath visitResourceType(ResourceType type, Void parameter) {
                return CanonicalPath.builder().withTenantId(type.getTenantId())
                        .withResourceTypeId(type.getId()).build();
            }

            @Override
            public CanonicalPath visitRelationship(Relationship relationship, Void parameter) {
                return CanonicalPath.builder().withRelationshipId(relationship.getId()).build();
            }

            @Override
            public CanonicalPath visitUnknown(Object entity, Void parameter) {
                return null;
            }
        }, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public CanonicalPath(String tenantId, String environmentId, String feedId,
            String resourceTypeId,
            String metricTypeId, String resourceId, String metricId, String relationshipId) {
        this.environmentId = environmentId;
        this.tenantId = tenantId;
        this.feedId = feedId;
        this.resourceTypeId = resourceTypeId;
        this.metricTypeId = metricTypeId;
        this.metricId = metricId;
        this.resourceId = resourceId;
        this.relationshipId = relationshipId;
    }

    /**
     * @return true if relationshipId or tenantId is not null, false otherwise
     */
    public boolean isDefined() {
        return relationshipId != null || tenantId != null;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public String getFeedId() {
        return feedId;
    }

    public String getMetricId() {
        return metricId;
    }

    public String getMetricTypeId() {
        return metricTypeId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getResourceTypeId() {
        return resourceTypeId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getRelationshipId() {
        return relationshipId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CanonicalPath)) return false;

        CanonicalPath that = (CanonicalPath) o;

        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (environmentId != null ? !environmentId.equals(that.environmentId) : that.environmentId != null)
            return false;
        if (feedId != null ? !feedId.equals(that.feedId) : that.feedId != null) return false;
        if (resourceTypeId != null ? !resourceTypeId.equals(that.resourceTypeId) : that.resourceTypeId != null)
            return false;
        if (metricTypeId != null ? !metricTypeId.equals(that.metricTypeId) : that.metricTypeId != null) return false;
        if (metricId != null ? !metricId.equals(that.metricId) : that.metricId != null) return false;
        if (resourceId != null ? !resourceId.equals(that.resourceId) : that.resourceId != null) return false;
        return !(relationshipId != null ? !relationshipId.equals(that.relationshipId) : that.relationshipId != null);

    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (environmentId != null ? environmentId.hashCode() : 0);
        result = 31 * result + (feedId != null ? feedId.hashCode() : 0);
        result = 31 * result + (resourceTypeId != null ? resourceTypeId.hashCode() : 0);
        result = 31 * result + (metricTypeId != null ? metricTypeId.hashCode() : 0);
        result = 31 * result + (metricId != null ? metricId.hashCode() : 0);
        result = 31 * result + (resourceId != null ? resourceId.hashCode() : 0);
        result = 31 * result + (relationshipId != null ? relationshipId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CanonicalPath[" + "environmentId='" + environmentId + '\'' + ", feedId='" + feedId + '\'' +
                ", metricId='" + metricId + '\'' + ", metricTypeId='" + metricTypeId + '\'' + ", relationshipId='" +
                relationshipId + '\'' + ", resourceId='" + resourceId + '\'' + ", resourceTypeId='" + resourceTypeId +
                '\'' + ", tenantId='" + tenantId + '\'' + ']';
    }


    public static final class Builder {
        String tenantId;
        String environmentId;
        String feedId;
        String resourceTypeId;
        String metricTypeId;
        String metricId;
        String resourceId;
        String relationshipId;

        private Builder() {

        }

        public Builder withTenantId(String id) {
            this.tenantId = id;
            return this;
        }

        public Builder withEnvironmentId(String id) {
            this.environmentId = id;
            return this;
        }

        public Builder withFeedId(String id) {
            this.feedId = id;
            return this;
        }

        public Builder withResourceTypeId(String id) {
            this.resourceTypeId = id;
            return this;
        }

        public Builder withMetricTypeId(String id) {
            this.metricTypeId = id;
            return this;
        }

        public Builder withMetricId(String id) {
            this.metricId = id;
            return this;
        }

        public Builder withResourceId(String id) {
            this.resourceId = id;
            return this;
        }

        public Builder withRelationshipId(String id) {
            this.relationshipId = id;
            return this;
        }

        public CanonicalPath build() {
            return new CanonicalPath(tenantId, environmentId, feedId, resourceTypeId, metricTypeId, resourceId,
                    metricId, relationshipId);
        }
    }
}
