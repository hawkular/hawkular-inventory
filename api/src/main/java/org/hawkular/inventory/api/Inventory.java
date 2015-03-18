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

import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.EntityVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

/**
 * Inventory stores "resources" which are groupings of measurements and other data. Inventory also stores metadata about
 * the measurements and resources to give them meaning.
 *
 * <p>The resources are organized by tenant (your company) and environments (i.e. testing, development, staging,
 * production, ...).
 *
 * <p>Resources are hierarchical - meaning that one can be a parent of others, recursively. One can also say that a
 * resource can contain other resources. Resources can have other kinds of relationships that are not necessarily
 * tree-like.
 *
 * <p>Resources can have a "resource type" (but they don't have to) which prescribes what kind of data a resource
 * contains. Most prominently a resource can have a list of metrics and a resource type can define what those metrics
 * should be by specifying the set of "metric types".
 *
 * <p>This interface offers a fluent API to compose a "traversal" over the graph of entities stored in the inventory in
 * a strongly typed fashion.
 *
 * <p>The inventory implementations are not required to be thread-safe. Instances should therefore be accessed only by a
 * single thread or serially.
 *
 * <p>Note to implementers:
 *
 * <p>The interfaces composing the inventory API are of 2 kinds:
 * <ol>
 *     <li>CRUD interfaces that provide manipulation of the entities as well as the retrieval of the actual entity
 *     instances (various {@code Read}, {@code ReadWrite} or {@code ReadRelate} interfaces, e.g.
 *     {@link org.hawkular.inventory.api.Environments.ReadWrite}),
 *     <li>browse interfaces that offer further navigation methods to "hop" onto other entities somehow related to the
 *     one(s) in the current position in the inventory traversal. These interfaces are further divided into 2 groups:
 *      <ul>
 *          <li><b>{@code Single}</b> interfaces that provide methods for navigating from a single entity.
 *          These interfaces generally contain methods that enable modification of the entity or its relationships.
 *          See {@link org.hawkular.inventory.api.Environments.Single} for an example.
 *          <li><b>{@code Multiple}</b> interfaces that provide methods for navigating from multiple entities at once.
 *          These interfaces strictly offer only read-only access to the entities, because the semantics of what should
 *          be done when modifying or relating multiple entities at once is not uniformly defined on all types of
 *          entities and therefore would make the API more confusing than necessary. See
 *          {@link org.hawkular.inventory.api.Environments.Multiple} for example.
 *      </ul>
 * </ol>
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface Inventory extends AutoCloseable {

    /**
     * Initializes the inventory from the provided configuration object.
     * @param configuration the configuration to use.
     */
    void initialize(Configuration configuration);

    /**
     * Entry point into the inventory. Select one ({@link org.hawkular.inventory.api.Tenants.ReadWrite#get(String)}) or
     * more ({@link org.hawkular.inventory.api.Tenants.ReadWrite#getAll(org.hawkular.inventory.api.filters.Filter...)})
     * tenants and navigate further to the entities of interest.
     *
     * @return full CRUD and navigation interface to tenants
     */
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

    /**
     * A generic version of the {@code inspect} methods that accepts an entity and returns the access interface to it.
     *
     * <p>If you don't know the type of entity (and therefore cannot deduce access interface type) you can always use
     * {@link org.hawkular.inventory.api.ResolvableToSingle} type which is guaranteed to be the super interface of
     * all access interfaces returned from this method (which makes it almost useless but at least you can get the
     * instance of it).
     *
     * @param entity the entity to inspect
     * @param accessInterface the expected access interface
     * @param <E> the type of the entity
     * @param <Single> the type of the access interface
     * @return the access interface instance
     *
     * @throws java.lang.ClassCastException if the provided access interface doesn't match the entity
     */
    default <E extends Entity, Single extends ResolvableToSingle<E>> Single inspect(E entity,
                                                                                    Class<Single> accessInterface) {
        return entity.accept(new EntityVisitor<Single, Void>() {
            @Override
            public Single visitTenant(Tenant tenant, Void ignored) {
                return accessInterface.cast(inspect(tenant));
            }

            @Override
            public Single visitEnvironment(Environment environment, Void ignored) {
                return accessInterface.cast(inspect(environment));
            }

            @Override
            public Single visitFeed(Feed feed, Void ignored) {
                return accessInterface.cast(inspect(feed));
            }

            @Override
            public Single visitMetric(Metric metric, Void ignored) {
                return accessInterface.cast(inspect(metric));
            }

            @Override
            public Single visitMetricType(MetricType definition, Void ignored) {
                return accessInterface.cast(inspect(definition));
            }

            @Override
            public Single visitResource(Resource resource, Void ignored) {
                return accessInterface.cast(inspect(resource));
            }

            @Override
            public Single visitResourceType(ResourceType type, Void ignored) {
                return accessInterface.cast(inspect(type));
            }
        }, null);
    }
}
