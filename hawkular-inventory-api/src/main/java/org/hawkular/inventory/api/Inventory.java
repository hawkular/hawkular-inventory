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
package org.hawkular.inventory.api;

import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.PageContext;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.ElementTypeVisitor;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

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
public interface Inventory extends AutoCloseable, Tenants.Container<Tenants.ReadWrite> {

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
     * Note also that identity-hashable entities created within the frame will not have their identity hashes computed
     * (and other entities their identity hashes updated) until the transaction frame commits the transaction. Therefore
     * if within the transaction frame you obtain an entity you created beforehand in the same frame, its identity hash
     * will be null. If you depend on the identity hashes having their full value, you can either use
     * {@link org.hawkular.inventory.api.model.IdentityHash#of(Entity, Inventory)} to obtain the new value (at an
     * expense of additional backend queries and computations) or actually commit the transaction.
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

    default Data.Single inspect(DataEntity data) throws EntityNotFoundException {
        return inspect(data.getPath(), Data.Single.class);
    }

    default OperationTypes.Single inspect(OperationType operationType) throws EntityNotFoundException {
        return inspect(operationType.getPath(), OperationTypes.Single.class);
    }

    default MetadataPacks.Single inspect(MetadataPack metadataPack) throws EntityNotFoundException {
        return inspect(metadataPack.getPath(), MetadataPacks.Single.class);
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
        return entity.accept(new ElementVisitor<Single, Void>() {
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
            public Single visitData(DataEntity data, Void parameter) {
                return accessInterface.cast(inspect(data));
            }

            @Override
            public Single visitOperationType(OperationType operationType, Void parameter) {
                return accessInterface.cast(inspect(operationType));
            }

            @Override
            public Single visitRelationship(Relationship relationship, Void parameter) {
                return accessInterface.cast(relationships().get(relationship.getId()));
            }

            @Override public Single visitMetadataPack(MetadataPack metadataPack, Void parameter) {
                return accessInterface.cast(inspect(metadataPack));
            }

            @Override public Single visitUnknown(Object entity, Void parameter) {
                return null;
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
        CanonicalPath.IdExtractor ids = path.ids();
        return path.accept(new ElementTypeVisitor<Single, Void>() {
            @Override
            public Single visitTenant(Void parameter) {
                return accessInterface.cast(tenants().get(ids.getTenantId()));
            }

            @Override
            public Single visitEnvironment(Void parameter) {
                return accessInterface.cast(tenants().get(ids.getTenantId()).environments()
                        .get(ids.getEnvironmentId()));
            }

            @Override
            public Single visitFeed(Void parameter) {
                return accessInterface.cast(tenants().get(ids.getTenantId()).feeds()
                        .get(ids.getFeedId()));
            }

            @Override
            public Single visitMetric(Void parameter) {
                Tenants.Single tenant = tenants().get(ids.getTenantId());
                RelativePath resourcePath = ids.getResourcePath();
                String feedId = ids.getFeedId();
                String environmentId = ids.getEnvironmentId();
                String metricId = ids.getMetricId();

                ResolvableToSingle<?, ?> iface;
                if (resourcePath == null) {
                    if (feedId == null) {
                        iface = tenant.environments().get(environmentId).metrics().get(metricId);
                    } else {
                        iface = tenant.feeds().get(feedId).metrics().get(metricId);
                    }
                } else {
                    if (feedId == null) {
                        iface = tenant.environments().get(environmentId).resources()
                                .descendContained(ids.getResourcePath()).metrics().get(metricId);
                    } else {
                        iface = tenant.feeds().get(feedId).resources().descendContained(ids.getResourcePath())
                                .metrics().get(metricId);
                    }
                }
                return accessInterface.cast(iface);

            }

            @Override
            public Single visitMetricType(Void parameter) {
                Tenants.Single ten = tenants().get(ids.getTenantId());
                return accessInterface.cast(ids.getFeedId() == null
                        ? ten.metricTypes().get(ids.getMetricTypeId())
                        : ten.feeds().get(ids.getFeedId()).metricTypes()
                        .get(ids.getMetricTypeId()));
            }

            @Override
            public Single visitResource(Void parameter) {
                Tenants.Single tenant = tenants().get(ids.getTenantId());

                Resources.Single access;

                if (ids.getFeedId() == null) {
                    access = tenant.environments().get(ids.getEnvironmentId()).resources()
                            .descendContained(ids.getResourcePath());
                } else {
                    access = tenant.feeds().get(ids.getFeedId()).resources().descendContained(ids.getResourcePath());
                }

                return accessInterface.cast(access);
            }

            @Override
            public Single visitResourceType(Void parameter) {
                Tenants.Single ten = tenants().get(ids.getTenantId());
                return accessInterface.cast(ids.getFeedId() == null
                        ? ten.resourceTypes().get(ids.getResourceTypeId())
                        : ten.feeds().get(ids.getFeedId()).resourceTypes()
                        .get(ids.getResourceTypeId()));
            }

            @Override
            public Single visitRelationship(Void parameter) {
                return accessInterface.cast(relationships().get(ids.getRelationshipId()));
            }

            @Override
            public Single visitData(Void parameter) {
                String rt = ids.getResourceTypeId();
                String ot = ids.getOperationTypeId();

                if (rt != null && ot == null) {
                    ResourceTypes.Single rts = inspect(path.up(), ResourceTypes.Single.class);

                    DataRole.ResourceType role = DataRole.ResourceType.valueOf(ids.getDataRole());
                    return accessInterface.cast(rts.data().get(role));
                } else if (ot != null) {
                    OperationTypes.Single ots = inspect(path.up(), OperationTypes.Single.class);
                    DataRole.OperationType role = DataRole.OperationType.valueOf(ids.getDataRole());
                    return accessInterface.cast(ots.data().get(role));
                } else {
                    Resources.Single res = inspect(path.up(), Resources.Single.class);
                    DataRole.Resource role = DataRole.Resource.valueOf(ids.getDataRole());
                    return accessInterface.cast(res.data().get(role));
                }
            }

            @Override
            public Single visitOperationType(Void parameter) {
                ResourceTypes.Single rt = inspect(path.up(), ResourceTypes.Single.class);
                return accessInterface.cast(rt.operationTypes().get(path.getSegment().getElementId()));
            }

            @Override
            public Single visitMetadataPack(Void parameter) {
                return accessInterface.cast(tenants().get(path.up().getSegment().getElementId())
                        .metadataPacks().get(path.getSegment().getElementId()));
            }

            @Override
            public Single visitUnknown(Void parameter) {
                return null;
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

    <T extends AbstractElement<?, ?>> T getElement(CanonicalPath path);

    <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(CanonicalPath startingPoint,
                                                                  Relationships.Direction direction, Class<T> clazz,
                                                                  String... relationshipNames);

    Configuration getConfiguration();

    default <T extends AbstractElement> Page<T> execute(Query query, Class<T> requestedEntity, Pager pager) {
        return new Page<>(Collections.emptyIterator(), new PageContext(0, 0, Order.unspecified()), 0);
    }

    /**
     * Converts the provided entity to a blueprint that would create the same entity.
     *
     * @param <B> the type of the blueprint
     * @param entity the entity to convert to blueprint
     * @return the blueprint of the entity
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <B extends Blueprint> B asBlueprint(Entity<B, ?> entity) {
        if (entity == null) {
            return null;
        }

        return entity.accept(new ElementVisitor<B, Void>() {

            @Override public B visitData(DataEntity data, Void parameter) {
                return (B) fillCommon(data, new DataEntity.Blueprint.Builder<>()).withRole(data.getRole())
                        .withValue(data.getValue()).build();
            }

            @Override public B visitTenant(Tenant tenant, Void parameter) {
                return (B) fillCommon(tenant, new Tenant.Blueprint.Builder()).build();
            }

            @Override public B visitEnvironment(Environment environment, Void parameter) {
                return (B) fillCommon(environment, new Environment.Blueprint.Builder()).build();
            }

            @Override public B visitFeed(Feed feed, Void parameter) {
                return (B) fillCommon(feed, Feed.Blueprint.builder()).build();
            }

            @Override public B visitMetric(Metric metric, Void parameter) {
                //we don't want to have tenant ID and all that jazz influencing the hash, so always use
                //a relative path
                RelativePath metricTypePath = metric.getType().getPath().relativeTo(metric.getPath());

                return (B) fillCommon(metric, Metric.Blueprint.builder())
                        .withInterval(metric.getCollectionInterval())
                        .withMetricTypePath(metricTypePath.toString()).build();
            }

            @Override public B visitMetricType(MetricType type, Void parameter) {
                return (B) fillCommon(type, MetricType.Blueprint.builder(type.getMetricDataType()))
                        .withInterval(type.getCollectionInterval()).withUnit(type.getUnit()).build();
            }

            @Override public B visitOperationType(OperationType operationType, Void parameter) {
                return (B) fillCommon(operationType, OperationType.Blueprint.builder()).build();
            }

            @Override public B visitMetadataPack(MetadataPack metadataPack, Void parameter) {
                throw new IllegalStateException("Computing a blueprint of a metadatapack is not supported.");
            }

            @Override public B visitUnknown(Object entity1, Void parameter) {
                throw new IllegalStateException("Unhandled entity type during conversion to blueprint: " +
                        entity1.getClass());
            }

            @Override public B visitResource(Resource resource, Void parameter) {
                //we don't want to have tenant ID and all that jazz influencing the hash, so always use
                //a relative path
                RelativePath resourceTypePath = resource.getType().getPath().relativeTo(resource.getPath());

                return (B) fillCommon(resource, Resource.Blueprint.builder())
                        .withResourceTypePath(resourceTypePath.toString()).build();
            }

            @Override public B visitResourceType(ResourceType type, Void parameter) {
                return (B) fillCommon(type, ResourceType.Blueprint.builder()).build();
            }

            @Override public B visitRelationship(Relationship relationship,
                                                 Void parameter) {
                throw new IllegalArgumentException("Inventory structure blueprint conversion does not handle " +
                        "relationships.");
            }

            private <X extends Entity<? extends XB, ?>, XB extends Entity.Blueprint,
                    XBB extends Entity.Blueprint.Builder<XB, XBB>>
            XBB fillCommon(X e, XBB bld) {
                return bld.withId(e.getId()).withName(e.getName())
                        .withProperties(e.getProperties());
            }
        }, null);
    }

    /**
     * @return a registry of various types associated with entities
     */
    static Types types() {
        return Types.INSTANCE;
    }

    /**
     * A registry of various types used with entities. You can look up an by things like segment type, entity type,
     * blueprint type, etc. and then obtain the rest of the types for the corresponding entity type.
     */
    @SuppressWarnings("unchecked")
    final class Types {
        private static final Types INSTANCE = new Types();
        private static final EnumMap<SegmentType, ElementTypes<?, ?, ?>> elementTypes;
        static {
            elementTypes = new EnumMap<>(SegmentType.class);

            elementTypes.put(SegmentType.d,
                    new ElementTypes<>(Data.Single.class, Data.Multiple.class, (Class) DataEntity.Blueprint.class,
                            DataEntity.Update.class, DataEntity.class, SegmentType.d));
            elementTypes.put(SegmentType.e,
                    new ElementTypes<>(Environments.Single.class, Environments.Multiple.class,
                            Environment.Blueprint.class, Environment.Update.class, Environment.class, SegmentType.e));
            elementTypes.put(SegmentType.f,
                    new ElementTypes<>(Feeds.Single.class, Feeds.Multiple.class, Feed.Blueprint.class, Feed.Update.class,
                            Feed.class, SegmentType.f));
            elementTypes.put(SegmentType.m,
                    new ElementTypes<>(Metrics.Single.class, Metrics.Multiple.class, Metric.Blueprint.class,
                            Metric.Update.class, Metric.class, SegmentType.m));
            elementTypes.put(SegmentType.mp,
                    new ElementTypes<>(MetadataPacks.Single.class, MetadataPacks.Multiple.class,
                            MetadataPack.Blueprint.class, MetadataPack.Update.class, MetadataPack.class,
                            SegmentType.mp));
            elementTypes.put(SegmentType.mt,
                    new ElementTypes<>(MetricTypes.Single.class, MetricTypes.Multiple.class, MetricType.Blueprint.class,
                            MetricType.Update.class, MetricType.class, SegmentType.mt));
            elementTypes.put(SegmentType.ot,
                    new ElementTypes<>(OperationTypes.Single.class, OperationTypes.Multiple.class,
                            OperationType.Blueprint.class, OperationType.Update.class, OperationType.class,
                            SegmentType.ot));
            elementTypes.put(SegmentType.r,
                    new ElementTypes<>(Resources.Single.class, Resources.Multiple.class, Resource.Blueprint.class,
                            Resource.Update.class, Resource.class, SegmentType.r));
            elementTypes.put(SegmentType.rl,
                    new ElementTypes<>(Relationships.Single.class, Relationships.Multiple.class,
                            Relationship.Blueprint.class, Relationship.Update.class, Relationship.class,
                            SegmentType.rl));
            elementTypes.put(SegmentType.rt,
                    new ElementTypes<>(ResourceTypes.Single.class, ResourceTypes.Multiple.class,
                            ResourceType.Blueprint.class, ResourceType.Update.class, ResourceType.class,
                            SegmentType.rt));
            elementTypes.put(SegmentType.t,
                    new ElementTypes<>(Tenants.Single.class, Tenants.Multiple. class, Tenant.Blueprint.class,
                            Tenant.Update.class, Tenant.class, SegmentType.t));
        }

        private Types() {

        }

        /**
         * @return element types that represent entities (i.e. everything but a relationship)
         */
        public Set<ElementTypes<? extends Entity<?, ?>, ?, ?>> entityTypes() {
            return elementTypes.entrySet().stream()
                    .filter(e -> {
                        SegmentType st = e.getKey();
                        return st != SegmentType.rl;
                    })
                    .map(e -> (ElementTypes<? extends Entity<?, ?>, ?, ?>) e.getValue())
                    .collect(Collectors.toSet());
        }

        public ElementTypes<?, ?, ?> byPath(Path path) {
            return bySegment(path.getSegment().getElementType());
        }

        public ElementTypes<?, ?, ?> bySegment(SegmentType segmentType) {
            ElementTypes ret = elementTypes.get(segmentType);
            if (ret == null) {
                throw new IllegalArgumentException(
                        "Unsupported segment type: " + segmentType);
            }

            return ret;
        }

        public <B extends Blueprint> ElementTypes<? extends AbstractElement<B, ?>, B, ?>
        byBlueprint(Class<B> blueprintType) {
            for(SegmentType st : SegmentType.values()) {
                ElementTypes ret = elementTypes.get(st);
                if (ret.getBlueprintType().equals(blueprintType)) {
                    return ret;
                }
            }

            throw new IllegalArgumentException("Unknown blueprint type: " + blueprintType);
        }

        public <U extends AbstractElement.Update> ElementTypes<?, ?, U> byUpdate(Class<U> updateType) {
            for(SegmentType st : SegmentType.values()) {
                ElementTypes ret = elementTypes.get(st);
                if (ret.getUpdateType().equals(updateType)) {
                    return ret;
                }
            }

            throw new IllegalArgumentException("Unknown update type: " + updateType);
        }

        public <E extends AbstractElement<B, U>, B extends Blueprint, U extends AbstractElement.Update>
        ElementTypes<E, B, U> byElement(Class<E> elementType) {
            for(SegmentType st : SegmentType.values()) {
                ElementTypes ret = elementTypes.get(st);
                if (ret.getElementType().equals(elementType)) {
                    return ret;
                }
            }

            throw new IllegalArgumentException("Unknown element type: " + elementType);
        }

        public <E extends AbstractElement<B, U>, B extends Blueprint, U extends AbstractElement.Update>
        ElementTypes<E, B, U> bySingle(Class<? extends ResolvableToSingle<E, U>> singleAccessorType) {
            for(SegmentType st : SegmentType.values()) {
                ElementTypes ret = elementTypes.get(st);
                if (ret.getSingleAccessorType().equals(singleAccessorType)) {
                    return ret;
                }
            }

            throw new IllegalArgumentException("Unknown single accessor type: " + singleAccessorType);
        }

        public <E extends AbstractElement<B, U>, B extends Blueprint, U extends AbstractElement.Update>
        ElementTypes<E, B, U> byMultiple(Class<? extends ResolvableToMany<E>> multipleAccessorType) {
            for(SegmentType st : SegmentType.values()) {
                ElementTypes ret = elementTypes.get(st);
                if (ret.getMultipleAccessorType().equals(multipleAccessorType)) {
                    return ret;
                }
            }

            throw new IllegalArgumentException("Unknown multiple accessor type: " + multipleAccessorType);
        }
    }

    final class ElementTypes<E extends AbstractElement<B, U>, B extends Blueprint, U extends AbstractElement.Update> {
        private final Class<? extends ResolvableToSingle<E, U>> singleAccessorType;
        private final Class<? extends ResolvableToMany<E>> manyAccessorType;
        private final Class<B> blueprintType;
        private final Class<U> updateType;
        private final Class<E> elementType;
        private final SegmentType segmentType;

        private ElementTypes(Class<? extends ResolvableToSingle<E, U>> singleAccessorType,
                             Class<? extends ResolvableToMany<E>> manyAccessorType,
                             Class<B> blueprintType,
                             Class<U> updateType,
                             Class<E> elementType, SegmentType segmentType) {
            this.singleAccessorType = singleAccessorType;
            this.manyAccessorType = manyAccessorType;
            this.blueprintType = blueprintType;
            this.updateType = updateType;
            this.elementType = elementType;
            this.segmentType = segmentType;
        }

        public Class<B> getBlueprintType() {
            return blueprintType;
        }

        public Class<E> getElementType() {
            return elementType;
        }

        public SegmentType getSegmentType() {
            return segmentType;
        }

        public Class<U> getUpdateType() {
            return updateType;
        }

        public Class<? extends ResolvableToMany<E>> getMultipleAccessorType() {
            return manyAccessorType;
        }

        public Class<? extends ResolvableToSingle<E, U>> getSingleAccessorType() {
            return singleAccessorType;
        }
    }
}
