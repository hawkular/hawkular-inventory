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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

/**
 * The inventory implementations are not required to be thread-safe. Instances should therefore be accessed only by a
 * single thread or serially.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface Inventory extends AutoCloseable {

    Tenants.ReadWrite tenants();

    /**
     * Quickly navigate to the provided tenant.
     *
     * @param tenant the tenant to steer to.
     * @return the access interface to the tenant
     * @throws EntityNotFoundException if the tenant is not found
     */
    default Tenants.Single steerTo(Tenant tenant) throws EntityNotFoundException {
        return tenants().get(tenant.getId());
    }

    /**
     * Quickly navigate to the provided environment.
     *
     * @param environment the environment to steer to.
     * @return the access interface to the environment
     * @throws EntityNotFoundException if the environment is not found
     */
    default Environments.Single steerTo(Environment environment) throws EntityNotFoundException {
        return tenants().get(environment.getTenantId()).environments().get(environment.getId());
    }

    /**
     * Quickly navigate to the provided feed.
     *
     * @param feed the feed to steer to.
     * @return the access interface to the feed
     * @throws EntityNotFoundException if the feed is not found
     */
    default Feeds.Single steerTo(Feed feed) throws EntityNotFoundException {
        return tenants().get(feed.getTenantId()).environments().get(feed.getEnvironmentId()).feeds().get(feed.getId());
    }

    /**
     * Quickly navigate to the provided metric.
     *
     * @param metric the metric to steer to.
     * @return the access interface to the metric
     * @throws EntityNotFoundException if the metric is not found
     */
    default Metrics.Single steerTo(Metric metric) throws EntityNotFoundException {
        return tenants().get(metric.getTenantId()).environments().get(metric.getEnvironmentId()).metrics()
                .get(metric.getId());
    }

    /**
     * Quickly navigate to the provided metric type.
     *
     * @param metricType the metric type to steer to.
     * @return the access interface to the metric type
     * @throws EntityNotFoundException if the metric type is not found
     */
    default MetricTypes.Single steerTo(MetricType metricType) throws EntityNotFoundException {
        return tenants().get(metricType.getId()).metricTypes().get(metricType.getId());
    }

    /**
     * Quickly navigate to the provided resource.
     *
     * @param resource the resource to steer to.
     * @return the access interface to the resource
     * @throws EntityNotFoundException if the resource is not found
     */
    default Resources.Single steerTo(Resource resource) throws EntityNotFoundException {
        return tenants().get(resource.getTenantId()).environments().get(resource.getEnvironmentId()).resources()
                .get(resource.getId());
    }

    /**
     * Quickly navigate to the provided resource type.
     *
     * @param resourceType the resource type to steer to.
     * @return the access interface to the resource type
     * @throws EntityNotFoundException if the resource type is not found
     */
    default ResourceTypes.Single steerTo(ResourceType resourceType) throws EntityNotFoundException {
        return tenants().get(resourceType.getTenantId()).resourceTypes().get(resourceType.getId());
    }
}
