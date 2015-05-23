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

import org.hawkular.inventory.api.filters.Filter;
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
 * <p>Despite their name, tenants are not completely separated and one can easily create relationships between them or
 * between entities underneath different tenants. This is because there are situations where such relationships might
 * make sense but more importantly because at the API level, inventory does not mandate any security model. It is
 * assumed that the true multi-tenancy in the common sense of the word is implemented by a layer on top of the inventory
 * API that also understands some security model to separate the tenants. To help with multi-tenancy support, the API
 * provides "mixins" to modify the default behavior of the inventory, see the
 * {@link Mixin} class and the {@link #augment(Inventory)} method.
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
 * @since 0.0.1
 */
public interface Inventory extends AutoCloseable {

    /**
     * Returns an object with which one can modify the behavior of or add features to the provided inventory.
     *
     * @param inventory the inventory to augment
     * @return a mixin object to modify the inventory with
     */
    static Mixin augment(Inventory inventory) {
        return new Mixin(inventory);
    }

    static Mixin.ObservableMixin augment(Mixin.Observable inventory) {
        return new Mixin.ObservableMixin(inventory);
    }

    static Mixin.AutoTenantMixin augment(Mixin.AutoTenant inventory) {
        return new Mixin.AutoTenantMixin(inventory);
    }

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
     * @param entity the entity to inspect
     * @param accessInterface the expected access interface
     * @param <E> the type of the entity
     * @param <Single> the type of the access interface
     * @return the access interface instance
     *
     * @throws java.lang.ClassCastException if the provided access interface doesn't match the entity
     */
    default <E extends Entity<?, ?>, Single extends ResolvableToSingle<E>> Single inspect(E entity,
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
     * A class for producing mixins of inventory and the {@link Mixin.Observable} or {@link Mixin.AutoTenant}
     * interfaces.
     *
     * <p>Given the old-ish type system of Java lacking Self type or union types, this is quite cumbersome and will
     * result in a combinatorial explosion of backing classes if more mixins are added. I am not sure there is a way to
     * work around that. Maybe dynamic proxies could be used but are not worth it at this stage.
     *
     * @since 0.0.2
     */
    final class Mixin {
        private final Inventory inventory;

        private Mixin(Inventory inventory) {
            this.inventory = inventory;
        }

        public AutoTenantMixin autoTenant() {
            return new AutoTenantMixin(inventory);
        }

        public ObservableMixin observable() {
            return new ObservableMixin(inventory);
        }

        public static final class ObservableMixin {
            private final Observable inventory;

            private ObservableMixin(Inventory inventory) {
                this.inventory = new ObservableInventory(inventory);
            }

            private ObservableMixin(Observable inventory) {
                this.inventory = inventory;
            }

            public ObservableAndAutoTenantMixin autoTenant() {
                return new ObservableAndAutoTenantMixin(inventory);
            }

            public Observable get() {
                return inventory;
            }
        }

        public static final class AutoTenantMixin {
            private final AutoTenant inventory;

            private AutoTenantMixin(Inventory inventory) {
                this.inventory = new AutoTenantInventory(inventory);
            }

            private AutoTenantMixin(AutoTenant inventory) {
                this.inventory = inventory;
            }
            public ObservableAndAutoTenantMixin observable() {
                return new ObservableAndAutoTenantMixin(inventory);
            }

            public AutoTenant get() {
                return inventory;
            }
        }

        public static final class ObservableAndAutoTenantMixin {
            private final AutoTenantAndObservable inventory;

            private ObservableAndAutoTenantMixin(Observable inventory) {
                this.inventory = new AutoTenantObservableInventory(inventory);
            }

            private ObservableAndAutoTenantMixin(AutoTenant inventory) {
                this.inventory = new ObservableAutoTenantInventory(inventory);
            }

            public AutoTenantAndObservable get() {
                return inventory;
            }
        }

        /**
         * The observable mixin interface. Augments the inventory so that mutation events on it can be observed.
         */
        public interface Observable extends Inventory {
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
        }

        /**
         * A mixin interface for automatic creation on tenants. While this mixin doesn't provide any new methods,
         * it changes the behavior of the inventory by using the {@link AutoTenantInventory} as the backing class for
         * the mixin implementation. Using this mixin, the
         * {@link #tenants()}.{@link ReadInterface#getAll(Filter...) getAll(Filter...)} always returns an empty set and
         * {@link #tenants()}.{@link ReadInterface#get(String) get(String)} returns an existing tenant or creates a new
         * with the provided id.
         */
        public interface AutoTenant extends Inventory {
        }

        /**
         * Poor man's intersection type ;)
         */
        public interface AutoTenantAndObservable extends AutoTenant, Observable {
        }

        /**
         * An implementation of AutoTenant and Observable interfaces in that order.
         */
        private static final class AutoTenantObservableInventory implements AutoTenantAndObservable {

            private final Observable inventory;
            private final AutoTenantInventory autoTenant;

            public AutoTenantObservableInventory(Observable inventory) {
                this.inventory = inventory;
                this.autoTenant = new AutoTenantInventory(inventory);
            }

            @Override
            public void initialize(Configuration configuration) {
                autoTenant.initialize(configuration);
            }

            @Override
            public Tenants.ReadWrite tenants() {
                return autoTenant.tenants();
            }

            @Override
            public void close() throws Exception {
                autoTenant.close();
            }

            @Override
            public boolean hasObservers(Interest<?, ?> interest) {
                return inventory.hasObservers(interest);
            }

            @Override
            public <C, E> rx.Observable<C> observable(Interest<C, E> interest) {
                return inventory.observable(interest);
            }
        }

        /**
         * An implementation of all Inventory, Observable and AutoTenant interfaces in that order.
         */
        private static final class ObservableAutoTenantInventory implements AutoTenantAndObservable {

            private final ObservableInventory inventory;

            public ObservableAutoTenantInventory(AutoTenant inventory) {
                this.inventory = new ObservableInventory(inventory);
            }

            public boolean hasObservers(Interest<?, ?> interest) {
                return inventory.hasObservers(interest);
            }

            @Override
            public void close() throws Exception {
                inventory.close();
            }

            @Override
            public void initialize(Configuration configuration) {
                inventory.initialize(configuration);
            }

            @Override
            public <C, E> rx.Observable<C> observable(Interest<C, E> interest) {
                return inventory.observable(interest);
            }

            @Override
            public ObservableTenants.ReadWrite tenants() {
                return inventory.tenants();
            }
        }
    }
}
