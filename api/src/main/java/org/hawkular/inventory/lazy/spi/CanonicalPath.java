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
package org.hawkular.inventory.lazy.spi;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class CanonicalPath {
    private final String tenantId;
    private final String environmentId;
    private final String feedId;
    private final String resourceTypeId;
    private final String metricTypeId;
    private final String metricId;
    private final String resourceId;

    public static Builder builder() {
        return new Builder();
    }

    public CanonicalPath(String tenantId, String environmentId, String feedId,
            String resourceTypeId,
            String metricTypeId, String resourceId, String metricId) {
        this.environmentId = environmentId;
        this.tenantId = tenantId;
        this.feedId = feedId;
        this.resourceTypeId = resourceTypeId;
        this.metricTypeId = metricTypeId;
        this.metricId = metricId;
        this.resourceId = resourceId;
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

    public static final class Builder {
        String tenantId;
        String environmentId;
        String feedId;
        String resourceTypeId;
        String metricTypeId;
        String metricId;
        String resourceId;

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

        public CanonicalPath buid() {
            return new CanonicalPath(tenantId, environmentId, feedId, resourceTypeId, metricTypeId, resourceId,
                    metricId);
        }
    }
}
