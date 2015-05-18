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

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.ElementTypeVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.EntityVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
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
 * <li>CRUD interfaces that provide manipulation of the entities as well as the retrieval of the actual entity
 * instances (various {@code Read}, {@code ReadWrite} or {@code ReadRelate} interfaces, e.g.
 * {@link org.hawkular.inventory.api.Environments.ReadWrite}),
 * <li>browse interfaces that offer further navigation methods to "hop" onto other entities somehow related to the
 * one(s) in the current position in the inventory traversal. These interfaces are further divided into 2 groups:
 * <ul>
 * <li><b>{@code Single}</b> interfaces that provide methods for navigating from a single entity.
 * These interfaces generally contain methods that enable modification of the entity or its relationships.
 * See {@link org.hawkular.inventory.api.Environments.Single} for an example.
 * <li><b>{@code Multiple}</b> interfaces that provide methods for navigating from multiple entities at once.
 * These interfaces strictly offer only read-only access to the entities, because the semantics of what should
 * be done when modifying or relating multiple entities at once is not uniformly defined on all types of
 * entities and therefore would make the API more confusing than necessary. See
 * {@link org.hawkular.inventory.api.Environments.Multiple} for example.
 * </ul>
 * </ol>
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface Inventory extends AutoCloseable {

    /**
     * Initializes the inventory from the provided configuration object.
     *
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
     * Provides an access interface for inspecting given tenant.
     *
     * @param tenant the tenant to steer to.
     * @return the access interface to the tenant
     */
    default Tenants.Single inspect(Tenant tenant) throws EntityNotFoundException {
        return tenants().get(tenant.getId());
    }

    /**
     * Provides an access interface for inspecting given environment.
     *
     * @param environment the environment to steer to.
     * @return the access interface to the environment
     */
    default Environments.Single inspect(Environment environment) throws EntityNotFoundException {
        return tenants().get(environment.getTenantId()).environments().get(environment.getId());
    }

    /**
     * Provides an access interface for inspecting given feed.
     *
     * @param feed the feed to steer to.
     * @return the access interface to the feed
     */
    default Feeds.Single inspect(Feed feed) throws EntityNotFoundException {
        return tenants().get(feed.getTenantId()).environments().get(feed.getEnvironmentId()).feeds().get(feed.getId());
    }

    /**
     * Provides an access interface for inspecting given metric.
     *
     * @param metric the metric to steer to.
     * @return the access interface to the metric
     */
    default Metrics.Single inspect(Metric metric) throws EntityNotFoundException {
        Environments.Single env = tenants().get(metric.getTenantId()).environments().get(metric.getEnvironmentId());

        if (metric.getFeedId() == null) {
            return env.feedlessMetrics().get(metric.getId());
        } else {
            return env.feeds().get(metric.getFeedId()).metrics().get(metric.getId());
        }
    }

    /**
     * Provides an access interface for inspecting given metric type.
     *
     * @param metricType the metric type to steer to.
     * @return the access interface to the metric type
     */
    default MetricTypes.Single inspect(MetricType metricType) throws EntityNotFoundException {
        return tenants().get(metricType.getId()).metricTypes().get(metricType.getId());
    }

    /**
     * Provides an access interface for inspecting given resource.
     *
     * @param resource the resource to steer to.
     * @return the access interface to the resource
     */
    default Resources.Single inspect(Resource resource) throws EntityNotFoundException {
        Environments.Single env = tenants().get(resource.getTenantId()).environments().get(resource.getEnvironmentId());

        if (resource.getFeedId() == null) {
            return env.feedlessResources().get(resource.getId());
        } else {
            return env.feeds().get(resource.getFeedId()).resources().get(resource.getId());
        }
    }

    /**
     * Provides an access interface for inspecting given resource type.
     *
     * @param resourceType the resource type to steer to.
     * @return the access interface to the resource type
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
     * @param entity          the entity to inspect
     * @param accessInterface the expected access interface
     * @param <E>             the type of the entity
     * @param <Single>        the type of the access interface
     * @return the access interface instance
     * @throws java.lang.ClassCastException if the provided access interface doesn't match the entity
     */
    default <E extends Entity<?, ?>, Single extends ResolvableToSingleWithRelationships<E>> Single inspect(E entity,
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

    /**
     * This method enables lookup of an element (relationship or entity) based on its provided type and the set of ids
     * that identify it.
     *
     * <p>The ids are provided in the "hierarchical" order, starting at tenants. For example, to inspect a resource type
     * one can issue:
     *
     * <pre>{@code
     *     ResourceTypes.Single access = inventory.inspect(ResourceType.class, ResourceTypes.Single, "myTenantId",
     *     "myResourceTypeId");
     * }</pre>
     *
     * for inspecting a resource that lives under a feed, one can issue:
     *
     * <pre>{@code
     *     Resources.Single access = inventory.inspect(Resource.class, Resources.Single, "myTenantId",
     *         "myEnvironmentId", "myFeedId", "myResourceId");
     * }</pre>
     *
     * @param elementType     the type of the element to access
     * @param accessInterface the access interface type to the elements of the given type
     * @param ids             the set of ids using which one can navigate to the element
     * @param <E>             the type of the element, either {@link Relationship} or any of the final {@link Entity}
     *                        subclasses
     * @param <Single>        the type of the access interface
     * @return the access interface to the entity on given path no matter if an entity truly exists on that path.
     * @throws IndexOutOfBoundsException if the length of the {@code ids} array does not correspond to the length of the
     *                                   path to elements of given type.
     */
    default <E extends AbstractElement<?, ?>, Single extends ResolvableToSingle<E>> Single inspect(Class<E> elementType,
            Class<Single> accessInterface, String... ids) {
        return ElementTypeVisitor.accept(new ElementTypeVisitor<Single, Void>() {
            @Override
            public Single visitTenant(Void parameter) {
                return accessInterface.cast(tenants().get(ids[0]));
            }

            @Override
            public Single visitEnvironment(Void parameter) {
                return accessInterface.cast(tenants().get(ids[0]).environments().get(ids[1]));
            }

            @Override
            public Single visitResourceType(Void parameter) {
                return accessInterface.cast(tenants().get(ids[0]).resourceTypes().get(ids[1]));
            }

            @Override
            public Single visitMetricType(Void parameter) {
                return accessInterface.cast(tenants().get(ids[0]).metricTypes().get(ids[1]));
            }

            @Override
            public Single visitFeed(Void parameter) {
                return accessInterface.cast(tenants().get(ids[0]).environments().get(ids[1]).feeds().get(ids[2]));
            }

            @Override
            public Single visitResource(Void parameter) {
                if (ids.length == 3) {
                    return accessInterface.cast(tenants().get(ids[0]).environments().get(ids[1]).feedlessResources()
                            .get(ids[3]));
                } else {
                    return accessInterface.cast(tenants().get(ids[0]).environments().get(ids[1]).feeds().get(ids[3])
                            .resources().get(ids[4]));
                }
            }

            @Override
            public Single visitMetric(Void parameter) {
                if (ids.length == 3) {
                    return accessInterface.cast(tenants().get(ids[0]).environments().get(ids[1]).feedlessMetrics()
                            .get(ids[3]));
                } else {
                    return accessInterface.cast(tenants().get(ids[0]).environments().get(ids[1]).feeds().get(ids[3])
                            .metrics().get(ids[4]));
                }
            }

            @Override
            public Single visitRelationship(Void parameter) {
                //TODO this needs support for retrieving relationships by their ID
                throw new UnsupportedOperationException();
            }
        }, elementType, null);
    }
}
