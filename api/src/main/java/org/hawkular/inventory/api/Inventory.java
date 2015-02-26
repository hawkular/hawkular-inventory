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
     * Query for the provided tenant and return an access interface for inspecting it.
     *
     * @param tenant the tenant to steer to.
     * @return the access interface to the tenant
     * @throws EntityNotFoundException if the tenant is not found
     */
    default Tenants.Single inspect(Tenant tenant) throws EntityNotFoundException {
        return tenants().get(tenant.getId());
    }

    /**
     * Query for the provided environment and return an access interface for inspecting it.
     *
     * @param environment the environment to steer to.
     * @return the access interface to the environment
     * @throws EntityNotFoundException if the environment is not found
     */
    default Environments.Single inspect(Environment environment) throws EntityNotFoundException {
        return tenants().get(environment.getTenantId()).environments().get(environment.getId());
    }

    /**
     * Query for the provided feed and return an access interface for inspecting it.
     *
     * @param feed the feed to steer to.
     * @return the access interface to the feed
     * @throws EntityNotFoundException if the feed is not found
     */
    default Feeds.Single inspect(Feed feed) throws EntityNotFoundException {
        return tenants().get(feed.getTenantId()).environments().get(feed.getEnvironmentId()).feeds().get(feed.getId());
    }

    /**
     * Query for the provided metric and return an access interface for inspecting it.
     *
     * @param metric the metric to steer to.
     * @return the access interface to the metric
     * @throws EntityNotFoundException if the metric is not found
     */
    default Metrics.Single inspect(Metric metric) throws EntityNotFoundException {
        return tenants().get(metric.getTenantId()).environments().get(metric.getEnvironmentId()).metrics()
                .get(metric.getId());
    }

    /**
     * Query for the provided metric and return an access interface for inspecting it type.
     *
     * @param metricType the metric type to steer to.
     * @return the access interface to the metric type
     * @throws EntityNotFoundException if the metric type is not found
     */
    default MetricTypes.Single inspect(MetricType metricType) throws EntityNotFoundException {
        return tenants().get(metricType.getId()).metricTypes().get(metricType.getId());
    }

    /**
     * Query for the provided resource and return an access interface for inspecting it.
     *
     * @param resource the resource to steer to.
     * @return the access interface to the resource
     * @throws EntityNotFoundException if the resource is not found
     */
    default Resources.Single inspect(Resource resource) throws EntityNotFoundException {
        return tenants().get(resource.getTenantId()).environments().get(resource.getEnvironmentId()).resources()
                .get(resource.getId());
    }

    /**
     * Query for the provided resource and return an access interface for inspecting it type.
     *
     * @param resourceType the resource type to steer to.
     * @return the access interface to the resource type
     * @throws EntityNotFoundException if the resource type is not found
     */
    default ResourceTypes.Single inspect(ResourceType resourceType) throws EntityNotFoundException {
        return tenants().get(resourceType.getTenantId()).resourceTypes().get(resourceType.getId());
    }
}
