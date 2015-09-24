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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hawkular.inventory.api.configuration.Configuration;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.ElementTypeVisitor;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
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
 * <p>Despite their name, tenants are not completely separated and one can easily create relationships between them or
 * between entities underneath different tenants. This is because there are situations where such relationships might
 * make sense but more importantly because at the API level, inventory does not mandate any security model. It is
 * assumed that the true multi-tenancy in the common sense of the word is implemented by a layer on top of the inventory
 * API that also understands some security model to separate the tenants.
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
 * <p>It is highly recommended to extend the {@link org.hawkular.inventory.base.BaseInventory} and its SPI instead of
 * this interface directly. The base is considered the "reference implementation" and any implementation is required to
 * behave the same.
 *
 * <p>If you for any reason need to implement the full inventory interface, please consider the following:
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
     * This creates a new transaction frame that will be responsible for its own transaction handling.
     * <p>
     * Note that operations done directly on this inventory instance will be done in their own transactions outside
     * of the returned frame. To do operations on inventory within that transaction frame, use {@link
     * TransactionFrame#boundInventory()} method to obtain an inventory instance that will obey the transaction
     * boundaries set by the frame.
     * <p>
     * Note that it is not recommended to operate with more than 1 transaction frame in a single thread of execution.
     * The behavior might differ depending on the storage backend for the inventory. Concurrent usage of more
     * transaction frames is fine though (this is because of the weird "transaction-per-thread" policy in Tinkerpop
     * and yes that means that one of the backend is leaking its requirements into the API, but I can't see a way
     * around it).
     *
     * @return a new transaction frame
     */
    TransactionFrame newTransactionFrame();

    /**
     * Entry point into the inventory. Select one ({@link org.hawkular.inventory.api.Tenants.ReadWrite#get(Object)}) or
     * more ({@link org.hawkular.inventory.api.Tenants.ReadWrite#getAll(org.hawkular.inventory.api.filters.Filter...)})
     * tenants and navigate further to the entities of interest.
     *
     * @return full CRUD and navigation interface to tenants
     */
    Tenants.ReadWrite tenants();

    /**
     * Global access to all relationships. Use this with caution as it may result in scanning across the potentially
     * very large number of relationships present in the inventory.
     *
     * <p>To create relationships, first navigate to one of its endpoint entities and create it from there using the
     * API calls.
     *
     * @return the read access to relationships.
     */
    Relationships.Read relationships();

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
        return inspect(environment.getPath(), Environments.Single.class);
    }

    /**
     * Provides an access interface for inspecting given feed.
     *
     * @param feed the feed to steer to.
     * @return the access interface to the feed
     */
    default Feeds.Single inspect(Feed feed) throws EntityNotFoundException {
        return inspect(feed.getPath(), Feeds.Single.class);
    }

    /**
     * Provides an access interface for inspecting given metric.
     *
     * @param metric the metric to steer to.
     * @return the access interface to the metric
     */
    default Metrics.Single inspect(Metric metric) throws EntityNotFoundException {
        return inspect(metric.getPath(), Metrics.Single.class);
    }

    /**
     * Provides an access interface for inspecting given metric type.
     *
     * @param metricType the metric type to steer to.
     * @return the access interface to the metric type
     */
    default MetricTypes.Single inspect(MetricType metricType) throws EntityNotFoundException {
        return inspect(metricType.getPath(), MetricTypes.Single.class);
    }

    /**
     * Provides an access interface for inspecting given resource.
     *
     * @param resource the resource to steer to.
     * @return the access interface to the resource
     */
    default Resources.Single inspect(Resource resource) throws EntityNotFoundException {
        return inspect(resource.getPath(), Resources.Single.class);
    }

    /**
     * Provides an access interface for inspecting given resource type.
     *
     * @param resourceType the resource type to steer to.
     * @return the access interface to the resource type
     */
    default ResourceTypes.Single inspect(ResourceType resourceType) throws EntityNotFoundException {
        return inspect(resourceType.getPath(), ResourceTypes.Single.class);
    }

    default Relationships.Single inspect(Relationship relationship) {
        return relationships().get(relationship.getId());
    }

    /**
     * A generic version of the {@code inspect} methods that accepts an element and returns the access interface to it.
     *
     * <p>If you don't know the type of element (and therefore cannot deduce access interface type) you can always use
     * {@link org.hawkular.inventory.api.ResolvableToSingle} type which is guaranteed to be the super interface of
     * all access interfaces returned from this method (which makes it almost useless but at least you can get the
     * instance of it).
     *
     * @param entity          the element to inspect
     * @param accessInterface the expected access interface
     * @param <E>             the type of the element
     * @param <Single>        the type of the access interface
     * @return the access interface instance
     * @throws java.lang.ClassCastException if the provided access interface doesn't match the element
     */
    default <E extends AbstractElement<?, U>, U extends AbstractElement.Update, Single extends ResolvableToSingle<E, U>>
    Single inspect(E entity, Class<Single> accessInterface) {
        return entity.accept(new ElementVisitor.Simple<Single, Void>() {
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

            @Override
            public Single visitRelationship(Relationship relationship, Void parameter) {
                return accessInterface.cast(relationships().get(relationship.getId()));
            }
        }, null);
    }

    /**
     * Another generic version of the inspect method, this time using the {@link CanonicalPath} to an element.
     *
     * <p>If you don't know the type of element (and therefore cannot deduce access interface type) you can always use
     * {@link org.hawkular.inventory.api.ResolvableToSingle} type which is guaranteed to be the super interface of
     * all access interfaces returned from this method (which makes it almost useless but at least you can get the
     * instance of it).
     *
     * <p>Note that this does NOT support paths that end inside a structured data, because structured data is not a
     * standalone inventory entity and does not have a separate access interface. For such paths, you need to extract
     * the parent path to the data entity, pass it to this method and then use the
     * {@link Data.Single#data(RelativePath)} or {@link Data.Single#flatData(RelativePath)}
     * methods to get at the target portion of the structured data specified by the path.
     *
     * @param path            the path to the element (entity or relationship)
     * @param accessInterface the expected access interface
     * @param <Single>        the type of the access interface
     * @return the access interface instance
     * @throws java.lang.ClassCastException if the provided access interface doesn't match the element
     */
    default <E extends AbstractElement<?, U>, U extends AbstractElement.Update, Single extends ResolvableToSingle<E, U>>
    Single inspect(CanonicalPath path, Class<Single> accessInterface) {
        return path.accept(new ElementTypeVisitor<Single, Void>() {
            @Override
            public Single visitTenant(Void parameter) {
                return accessInterface.cast(tenants().get(path.ids().getTenantId()));
            }

            @Override
            public Single visitEnvironment(Void parameter) {
                return accessInterface.cast(tenants().get(path.ids().getTenantId()).environments()
                        .get(path.ids().getEnvironmentId()));
            }

            @Override
            public Single visitFeed(Void parameter) {
                return accessInterface.cast(tenants().get(path.ids().getTenantId()).environments()
                        .get(path.ids().getEnvironmentId()).feeds().get(path.ids().getFeedId()));
            }

            @Override
            public Single visitMetric(Void parameter) {
                Environments.Single env = tenants().get(path.ids().getTenantId()).environments()
                        .get(path.ids().getEnvironmentId());

                return accessInterface.cast(path.ids().getFeedId() == null
                        ? env.feedlessMetrics().get(path.ids().getMetricId())
                        : env.feeds().get(path.ids().getFeedId()).metrics().get(path.ids().getMetricId()));
            }

            @Override
            public Single visitMetricType(Void parameter) {
                return accessInterface.cast(tenants().get(path.ids().getTenantId()).feedlessMetricTypes()
                        .get(path.ids().getMetricTypeId()));
            }

            @Override
            public Single visitResource(Void parameter) {
                Environments.Single env = tenants().get(path.ids().getTenantId()).environments()
                        .get(path.ids().getEnvironmentId());

                @SuppressWarnings("ConstantConditions")
                RelativePath parentResource = path.ids().getResourcePath().up();

                Resources.Single access;

                if (path.ids().getFeedId() == null) {
                    if (parentResource.isDefined()) {
                        access = env.feedlessResources().descend(
                                path.ids().getResourcePath().getPath().get(0).getElementId(),
                                allResourceSegments(path, 1, 1)).get(path);
                    } else {
                        access = env.feedlessResources().get(path.getSegment().getElementId());
                    }
                } else {
                    Feeds.Single feed = env.feeds().get(path.ids().getFeedId());

                    if (parentResource.isDefined()) {
                        access = feed.resources().descend(path.ids().getResourcePath().getPath().get(0).getElementId(),
                                allResourceSegments(path, 1, 1)).get(path);
                    } else {
                        access = feed.resources().get(path.getSegment().getElementId());
                    }
                }

                return accessInterface.cast(access);
            }

            @Override
            public Single visitResourceType(Void parameter) {
                Tenants.Single ten = tenants().get(path.ids().getTenantId());
                return accessInterface.cast(path.ids().getFeedId() == null
                        ? ten.feedlessResourceTypes().get(path.ids().getResourceTypeId())
                        : ten.environments().get(path.ids().getEnvironmentId())
                                .feeds().get(path.ids().getFeedId()).resourceTypes()
                                .get(path.ids().getResourceTypeId()));
            }

            @Override
            public Single visitRelationship(Void parameter) {
                return accessInterface.cast(relationships().get(path.ids().getRelationshipId()));
            }

            @Override
            public Single visitData(Void parameter) {
                CanonicalPath.IdExtractor ids = path.ids();
                String rt = ids.getResourceTypeId();
                String ot = ids.getOperationTypeId();

                if (rt != null) {
                    ResourceTypes.Single rts = inspect(path.up(), ResourceTypes.Single.class);

                    return accessInterface.cast(rts.data().get(ids.getDataRole()));
                } else if (ot != null) {
                    OperationTypes.Single ots = inspect(path.up(), OperationTypes.Single.class);
                    return accessInterface.cast(ots.data().get(ids.getDataRole()));
                } else {
                    Resources.Single res = inspect(path.up(), Resources.Single.class);
                    return accessInterface.cast(res.data().get(ids.getDataRole()));
                }
            }

            @Override
            public Single visitOperationType(Void parameter) {
                ResourceTypes.Single rt = inspect(path.up(), ResourceTypes.Single.class);
                return accessInterface.cast(rt.operationTypes().get(path.getSegment().getElementId()));
            }

            @Override
            public Single visitUnknown(Void parameter) {
                return null;
            }

            private CanonicalPath[] allResourceSegments(CanonicalPath path, int leaveOutTop, int leaveOutBottom) {
                List<CanonicalPath> ret = new ArrayList<>();

                Iterator<CanonicalPath> it = path.descendingIterator();
                int leftOutTop = 0;
                while (it.hasNext()) {
                    CanonicalPath p = it.next();
                    if (Resource.class.equals(p.getSegment().getElementType()) && leftOutTop++ >= leaveOutTop) {
                        ret.add(p);
                    }
                }

                if (ret.size() - leaveOutBottom > 0) {
                    int len = ret.size() - leaveOutBottom;
                    return ret.subList(0, len).toArray(new CanonicalPath[len]);
                } else {
                    return new CanonicalPath[0];
                }
            }
        }, null);
    }

    /**
     * This method is mainly useful for testing.
     *
     * @param interest the interest in changes of some inventory entity type
     * @return true if the interest has some observers or not
     */
    boolean hasObservers(Interest<?, ?> interest);

    /**
     * <b>NOTE</b>: The subscribers will receive the notifications even after they failed. I.e. it is the
     * subscribers responsibility to unsubscribe on error
     *
     * @param interest the interest in changes of some inventory entity type
     * @param <C>      the type of object that will be passed to the subscribers of the returned observable
     * @param <E>      the type of the entity the interest is expressed on
     * @return an observable to which the caller can subscribe to receive notifications about inventory
     * mutation
     */
    <C, E> rx.Observable<C> observable(Interest<C, E> interest);

    /**
     * This method returns the {@link java.io.InputStream} with the GraphSON representation of the whole sub-graph
     * of given tenantId. It's basically the graph dump.
     *
     * @param tenantId the tenantId for which we want the GraphSON
     * @return the InputStream with the GraphSON representation
     */
    InputStream getGraphSON(String tenantId);

    <T extends AbstractElement> T getElement(CanonicalPath path);

    <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(CanonicalPath startingPoint,
                                                                  Relationships.Direction direction, Class<T> clazz,
                                                                  String... relationshipNames);

    Configuration getConfiguration();
}
