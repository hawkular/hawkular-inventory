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

import java.util.Collection;

/**
 * Provides an inventory api.
 * TODO factor in the environment
 * @author Heiko Rupp
 */
public interface Inventory {


    /** Add a resource for a tenant */
    String addResource(String tenant, Resource resource) throws Exception;

    /** Retrieve a collection of resources for a given type. If type is null, all resources are returned. */
    Collection<Resource> getResourcesForType(String tenant, ResourceType type) throws Exception;

    /** Get a resource by its Id */
    Resource getResource(String tenant, String uid) throws Exception;

    /** Remove a resource with a certain id */
    boolean deleteResource(String tenant, String uid) throws Exception;

    /** Adds metrics to a resource */
    boolean addMetricToResource(String tenant, String resourceId, String metric_name) throws Exception;
    boolean addMetricsToResource(String tenant, String resourceId, Collection<MetricDefinition> definitions)
            throws Exception;

    /** Retrieve all metrics for a resource */
    Collection<MetricDefinition> listMetricsForResource(String tenant, String resourceId) throws Exception;

    /** Updates a single metric */
    boolean updateMetric(String tenant, String resourceId, MetricDefinition metric) throws Exception;

    /** Retrieve one metric by its id */
    MetricDefinition getMetric(String tenant, String resourceId, String metricId) throws Exception;
}
