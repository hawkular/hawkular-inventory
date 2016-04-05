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
package org.hawkular.inventory.api.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.MetadataPacks;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.paging.Pager;

/**
 * Represents the structure of an inventory. It is supposed that the structure is loaded lazily. The structure is
 * represented using entity blueprints instead of entity types themselves so that this structure can be computed
 * offline, without access to all information up to the tenant.
 *
 * @author Lukas Krejci
 * @since 0.11.0
 */
public interface InventoryStructure<Root extends Entity.Blueprint> {

    /**
     * Creates a lazily loaded online inventory structure, backed by the provided inventory instance.
     *
     * @param rootEntity the root entity of which to create the structure of
     * @param inventory  the inventory to load the data from
     * @return the structure of given entity and its children
     */
    static <E extends Entity<B, ?>, B extends Entity.Blueprint>
    InventoryStructure<B> of(E rootEntity, Inventory inventory) {
        return new InventoryStructure<B>() {
            B root = asBlueprint(rootEntity);

            @Override public B getRoot() {
                return root;
            }

            @Override
            public <EE extends Entity<BB, ?>, BB extends Blueprint> Stream<BB>
            getChildren(RelativePath parent, Class<EE> childType) {
                CanonicalPath absoluteParent = rootEntity.getPath().modified().extend(parent.getPath()).get();
                Class<?> parentType = absoluteParent.getSegment().getElementType();

                return ElementTypeVisitor.accept(childType, new ElementTypeVisitor<Stream<BB>, Void>() {

                    @Override public Stream<BB> visitTenant(Void parameter) {
                        if (absoluteParent.isDefined()) {
                            return Stream.empty();
                        } else {
                            return fromRead(inventory.tenants());
                        }
                    }

                    @Override public Stream<BB> visitEnvironment(Void parameter) {
                        return fromRead(Tenant.class, Tenants.Single.class, Environments.Container::environments);
                    }

                    @Override public Stream<BB> visitFeed(Void parameter) {
                        return fromRead(Tenant.class, Tenants.Single.class, Feeds.Container::feeds);
                    }

                    @Override public Stream<BB> visitMetric(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitEnvironment(Void parameter) {
                                return fromRead(Environment.class, Environments.Single.class,
                                        Metrics.Container::metrics);
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, Metrics.Container::metrics);
                            }

                            @Override public Stream<BB> visitResource(Void parameter) {
                                return fromRead(Resource.class, Resources.Single.class, Metrics.Container::metrics);
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitMetricType(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitTenant(Void parameter) {
                                return fromRead(Tenant.class, Tenants.Single.class, MetricTypes.Container::metricTypes);
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, MetricTypes.Container::metricTypes);
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitResource(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitEnvironment(Void parameter) {
                                return fromRead(Environment.class, Environments.Single.class,
                                        Resources.Container::resources);
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, Resources.Container::resources);
                            }

                            @Override public Stream<BB> visitResource(Void parameter) {
                                return fromRead(Resource.class, Resources.Single.class, Resources.Container::resources);
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitResourceType(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitTenant(Void parameter) {
                                return fromRead(Tenant.class, Tenants.Single.class,
                                        ResourceTypes.Container::resourceTypes);
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, ResourceTypes.Container::resourceTypes);
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitRelationship(Void parameter) {
                        return Stream.empty();
                    }

                    @Override public Stream<BB> visitData(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitResource(Void parameter) {
                                return fromRead(Resource.class, Resources.Single.class, Data.Container::data);
                            }

                            @Override public Stream<BB> visitResourceType(Void parameter) {
                                return fromRead(ResourceType.class, ResourceTypes.Single.class, Data.Container::data);
                            }

                            @Override public Stream<BB> visitOperationType(Void parameter) {
                                return fromRead(OperationType.class, OperationTypes.Single.class,
                                        Data.Container::data);
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitOperationType(Void parameter) {
                        return fromRead(ResourceType.class, ResourceTypes.Single.class,
                                OperationTypes.Container::operationTypes);
                    }

                    @Override public Stream<BB> visitMetadataPack(Void parameter) {
                        return fromRead(Tenant.class, Tenants.Single.class, MetadataPacks.Container::metadataPacks);
                    }

                    @Override public Stream<BB> visitUnknown(Void parameter) {
                        throw new IllegalStateException("Unhandled type of entity in inventory structure: " +
                                parentType);
                    }


                    @SuppressWarnings("unchecked")
                    private <P extends Entity<?, PU>, PA extends ResolvableToSingle<P, PU>, PU extends Entity.Update,
                            X extends Entity<XB, ?>, XB extends Blueprint>
                    Stream<BB> fromRead(Class<P> parentType, Class<PA> parentAccessType,
                                        Function<PA, ResolvingToMultiple<? extends ResolvableToMany<X>>>
                                               childAccessSupplier) {

                        if (!(absoluteParent.isDefined() && parentType.equals(absoluteParent.getSegment()
                                .getElementType()))) {
                            return Stream.empty();
                        } else {
                            PA parentAccess = inventory.inspect(absoluteParent, parentAccessType);
                            return fromRead(childAccessSupplier.apply(parentAccess));
                        }
                    }

                    @SuppressWarnings("unchecked")
                    private <X extends Entity<XB, ?>, XB extends Blueprint>
                    Stream<BB> fromRead(ResolvingToMultiple<? extends ResolvableToMany<X>> read) {
                        Iterator<X> it = read.getAll().entities(Pager.none());
                        Spliterator<X> sit = Spliterators.spliterator(it, Long.MAX_VALUE, Spliterator.DISTINCT &
                                Spliterator.IMMUTABLE & Spliterator.NONNULL);

                        return StreamSupport.stream(sit, false).map(e -> (BB) asBlueprint(e));
                    }
                }, null);
            }

            @Override public Blueprint get(RelativePath path) {
                CanonicalPath pathToElement = rootEntity.getPath().modified().extend(path.getPath()).get();

                @SuppressWarnings("unchecked")
                Entity<?, ?> entity = (Entity<?, ?>) inventory.inspect(pathToElement, ResolvableToSingle.class)
                        .entity();

                return asBlueprint(entity);
            }

            @SuppressWarnings("unchecked")
            private <BB extends Blueprint> BB asBlueprint(Entity<BB, ?> entity) {
                if (entity == null) {
                    return null;
                }
                return entity.accept(new ElementVisitor<BB, Void>() {

                    @Override public BB visitData(DataEntity data, Void parameter) {
                        return (BB) fillCommon(data, new DataEntity.Blueprint.Builder<>()).withRole(data.getRole())
                                .withValue(data.getValue()).build();
                    }

                    @Override public BB visitTenant(Tenant tenant, Void parameter) {
                        return (BB) fillCommon(tenant, new Tenant.Blueprint.Builder()).build();
                    }

                    @Override public BB visitEnvironment(Environment environment, Void parameter) {
                        return (BB) fillCommon(environment, new Environment.Blueprint.Builder()).build();
                    }

                    @Override public BB visitFeed(Feed feed, Void parameter) {
                        return (BB) fillCommon(feed, Feed.Blueprint.builder()).build();
                    }

                    @Override public BB visitMetric(Metric metric, Void parameter) {
                        //we don't want to have tenant ID and all that jazz influencing the hash, so always use
                        //a relative path
                        RelativePath metricTypePath = metric.getType().getPath().relativeTo(metric.getPath());

                        return (BB) fillCommon(metric, Metric.Blueprint.builder())
                                .withInterval(metric.getCollectionInterval())
                                .withMetricTypePath(metricTypePath.toString()).build();
                    }

                    @Override public BB visitMetricType(MetricType type, Void parameter) {
                        return (BB) fillCommon(type, MetricType.Blueprint.builder(type.getType()))
                                .withInterval(type.getCollectionInterval()).withUnit(type.getUnit()).build();
                    }

                    @Override public BB visitOperationType(OperationType operationType, Void parameter) {
                        return (BB) fillCommon(operationType, OperationType.Blueprint.builder()).build();
                    }

                    @Override public BB visitMetadataPack(MetadataPack metadataPack, Void parameter) {
                        MetadataPack.Blueprint.Builder bld = MetadataPack.Blueprint.builder()
                                .withName(metadataPack.getName()).withProperties(metadataPack.getProperties());

                        inventory.inspect(metadataPack).metricTypes().getAll()
                                .entities(Pager.none())
                                .forEachRemaining(e -> bld.withMember(e.getPath()));

                        inventory.inspect(metadataPack).resourceTypes().getAll()
                                .entities(Pager.none())
                                .forEachRemaining(e -> bld.withMember(e.getPath()));

                        return (BB) bld.build();
                    }

                    @Override public BB visitUnknown(Object entity, Void parameter) {
                        throw new IllegalStateException("Unhandled entity type during conversion to blueprint: " +
                                entity.getClass());
                    }

                    @Override public BB visitResource(Resource resource, Void parameter) {
                        //we don't want to have tenant ID and all that jazz influencing the hash, so always use
                        //a relative path
                        RelativePath resourceTypePath = resource.getType().getPath().relativeTo(resource.getPath());

                        return (BB) fillCommon(resource, Resource.Blueprint.builder())
                                .withResourceTypePath(resourceTypePath.toString()).build();
                    }

                    @Override public BB visitResourceType(ResourceType type, Void parameter) {
                        return (BB) fillCommon(type, ResourceType.Blueprint.builder()).build();
                    }

                    @Override public BB visitRelationship(org.hawkular.inventory.api.model.Relationship relationship,
                                                          Void parameter) {
                        throw new IllegalArgumentException("Inventory structure blueprint conversion does not handle " +
                                "relationships.");
                    }

                    private <X extends Entity<? extends XB, ?>, XB extends Entity.Blueprint,
                            XBB extends Entity.Blueprint.Builder<XB, XBB>>
                    XBB fillCommon(X entity, XBB bld) {
                        return bld.withId(entity.getId()).withName(entity.getName())
                                .withProperties(entity.getProperties());
                    }
                }, null);
            }
        };
    }

    /**
     * Shortcut method, exactly identical to calling {@link Offline#of(Entity.Blueprint)}.
     *
     * @param root the root blueprint
     * @param <B> the type of the blueprint
     * @return the builder to build an offline inventory structure
     */
    static <B extends Entity.Blueprint> InventoryStructure.Offline.Builder<B> of(B root) {
        return Offline.of(root);
    }

    /**
     * @return the root entity
     */
    Root getRoot();

    /**
     * Returns the direct children of given type under the supplied path to the parent entity, which is relative to some
     * root entity for which this structure was instantiated.
     *
     * @param parent    the path to the parent entity, relative to the root entity
     * @param childType the type of the child entities to retrieve
     * @param <E>       the type of the child entities
     * @param <B>       the type of the child entity blueprint
     * @return a stream of blueprints corresponding to the child entities.
     */
    <E extends Entity<B, ?>, B extends Blueprint> Stream<B> getChildren(RelativePath parent, Class<E> childType);

    /**
     * Gets a blueprint on the given path.
     *
     * @param path the path under the root of the structure
     * @return the blueprint describing the entity on the given path
     */
    Blueprint get(RelativePath path);

    @SuppressWarnings("unchecked")
    default Stream<Entity.Blueprint> getAllChildren(RelativePath parent) {
        Stream ret = Stream.empty();
        RelativePath.Extender check = RelativePath.empty()
                .extend(Blueprint.getEntityTypeOf(getRoot()),getRoot().getId())
                .extend(parent.getPath());

        for (Class<?> cls : CanonicalPath.SHORT_TYPE_NAMES.keySet()) {
            if (Entity.class.isAssignableFrom(cls) && check.canExtendTo(cls)) {
                ret = Stream.concat(ret, getChildren(parent, (Class<Entity>) cls));
            }
        }

        return ret;
    }

    enum EntityType {
        //the order is significant.. the latter cannot exist without (some of) the prior
        feed(Feed.class, Feed.Blueprint.class),
        resourceType(ResourceType.class, ResourceType.Blueprint.class),
        metricType(MetricType.class, MetricType.Blueprint.class),
        operationType(OperationType.class, OperationType.Blueprint.class),
        metric(Metric.class, Metric.Blueprint.class),
        resource(Resource.class, Resource.Blueprint.class),
        dataEntity(DataEntity.class, DataEntity.Blueprint.class);

        public final Class<? extends Entity<?, ?>> elementType;
        public final Class<? extends Entity.Blueprint> blueprintType;

        public static EntityType of(Class<?> type) {
            for (EntityType t : EntityType.values()) {
                if (type.equals(t.elementType)) {
                    return t;
                }
            }

            throw new IllegalArgumentException("Unknown type of entity: " + type);
        }

        public static EntityType ofBlueprint(Class<?> type) {
            for (EntityType t : EntityType.values()) {
                if (type.equals(t.blueprintType)) {
                    return t;
                }
            }

            return null;
        }

        EntityType(Class<? extends Entity<?, ?>> elementType, Class<? extends Entity.Blueprint> blueprintType) {
            this.elementType = elementType;
            this.blueprintType = blueprintType;
        }
    }

    /**
     * Represents the structure of the inventory off-line, without access to an inventory instance. This implies that
     * an instance holds on to all data required and thus can occupy a lot of memory. On the other hand, it is
     * serializable.
     * <p>
     * This is not directly instantiable but rather can be either copied from another structure (possibly lazily
     * loaded) or built using a {@link Builder}.
     */
    class Offline<Root extends Entity.Blueprint> implements InventoryStructure<Root>, Serializable {

        private final Root root;
        private final Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children;
        private final Map<RelativePath, Entity.Blueprint> entities;

        private Offline(Root root, Map<RelativePath, Entity.Blueprint> entities,
                        Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children) {
            this.root = root;
            this.children = children;
            this.entities = entities;
        }

        public static <R extends Entity.Blueprint> Offline<R> copy(InventoryStructure<R> other) {
            Map<RelativePath, EntityAndChildren> entities = new HashMap<>();

            ElementTypeVisitor<Void, RelativePath.Extender> visitor =
                    new ElementTypeVisitor.Simple<Void, RelativePath.Extender>() {
                        @Override protected Void defaultAction(Class<? extends AbstractElement<?, ?>> elementType,
                                                               RelativePath.Extender parentPath) {

                            @SuppressWarnings("unchecked")
                            Class<Entity<Entity.Blueprint, ?>> childType =
                                    (Class<Entity<Entity.Blueprint,?>>) elementType;

                            impl(childType, parentPath);

                            return null;
                        }

                        private <E extends Entity<B, ?>, B extends Entity.Blueprint>
                        void impl(Class<E> childType, RelativePath.Extender parent) {
                            if (parent.canExtendTo(childType)) {
                                Stream<B> otherChildren = other.getChildren(parent.get(), childType);

                                RelativePath parentPath = parent.get();

                                EntityAndChildren parentChildren = entities.get(parentPath);
                                if (parentChildren == null) {
                                    if (parentPath.isDefined()) {
                                        throw new IllegalStateException("Could not find the tracked children of a" +
                                                " parent " + parent + " during inventory structure copy. This is a " +
                                                "bug.");
                                    } else {
                                        parentChildren = new EntityAndChildren(other.getRoot());
                                        entities.put(parentPath, parentChildren);
                                    }
                                }

                                otherChildren.forEach(c -> {
                                    RelativePath.Extender childPath = parentPath.modified()
                                            .extend(childType, c.getId());

                                    RelativePath cp = childPath.get();

                                    EntityAndChildren childChildren = entities.get(cp);
                                    if (childChildren == null) {
                                        childChildren = new EntityAndChildren(c);
                                        entities.put(cp, childChildren);
                                    }

                                    ElementTypeVisitor.accept(childType, this, childPath);
                                });
                            }
                        }
                    };

            R root = other.getRoot();

            RelativePath empty = RelativePath.empty().get();

            other.getAllChildren(empty).forEach(
                    b -> ElementTypeVisitor.accept(Blueprint.getEntityTypeOf(b), visitor, empty.modified()));

            Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children = new HashMap<>();
            Map<RelativePath, Entity.Blueprint> blueprints = new HashMap<>();

            for (Map.Entry<RelativePath, EntityAndChildren> e : entities.entrySet()) {
                //handle entities
                RelativePath entityPath = e.getKey();
                EntityAndChildren entity = e.getValue();

                blueprints.put(entityPath, entity.entity);

                RelativePath parent = entityPath.up();
                EntityType entityType = EntityType.of(Blueprint.getEntityTypeOf(entity.entity));

                Map<EntityType, Set<Entity.Blueprint>> childrenByType = children.get(parent);
                if (childrenByType == null) {
                    childrenByType = new HashMap<>();
                    children.put(parent, childrenByType);
                }

                Set<Entity.Blueprint> childrenBlueprints = childrenByType.get(entityType);
                if (childrenBlueprints == null) {
                    childrenBlueprints = new HashSet<>();
                    childrenByType.put(entityType, childrenBlueprints);
                }
                childrenBlueprints.add(entity.entity);
            }

            return new Offline<>(root, blueprints, children);
        }


        /**
         * You can use this method if you have an existing inventory structure and want to make modifications to it.
         *
         * @return a builder seeded with this inventory structure
         */
        public InventoryStructure.Offline.Builder<Root> asBuilder() {
            return new InventoryStructure.Offline.Builder<>(root, RelativePath.empty().get(), entities, children);
        }

        public static <R extends Entity.Blueprint> Builder<R> of(R root) {
            return new Builder<>(root);
        }

        @Override public Root getRoot() {
            return root;
        }

        @SuppressWarnings("unchecked") @Override
        public <E extends Entity<B, ?>, B extends Blueprint> Stream<B>
        getChildren(RelativePath parent, Class<E> childType) {
            return (Stream<B>) children.getOrDefault(parent, Collections.emptyMap())
                    .getOrDefault(EntityType.of(childType), Collections.emptySet())
                    .stream();
        }

        @Override public Blueprint get(RelativePath path) {
            return entities.get(path);
        }

        private static final class EntityAndChildren {
            final Map<Class<?>, Map<String, Entity.Blueprint>> children = new HashMap<>();
            Entity.Blueprint entity;

            EntityAndChildren(Entity.Blueprint entity) {
                this.entity = entity;
            }
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Offline<?> offline = (Offline<?>) o;

            if (!root.equals(offline.root)) return false;
            if (!children.equals(offline.children)) return false;
            return entities.equals(offline.entities);

        }

        @Override public int hashCode() {
            int result = root.hashCode();
            result = 31 * result + children.hashCode();
            result = 31 * result + entities.hashCode();
            return result;
        }
    }

    abstract class AbstractBuilder<This extends AbstractBuilder<?>> {
        protected RelativePath myPath;
        protected final Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children;
        protected final Map<RelativePath, Entity.Blueprint> blueprints;

        private AbstractBuilder(RelativePath myPath, Map<RelativePath, Entity.Blueprint> blueprints,
                                Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children) {
            this.myPath = myPath;
            this.children = children;
            this.blueprints = blueprints;
        }

        /**
         * Starts building a new child of the currently built entity.
         *
         * @param child the child entity blueprint
         * @return a new child builder
         * @throws IllegalArgumentException if the provided child cannot be contained in the currently built entity
         * (i.e. a resource type cannot be contained in a resource for example).
         */
        public ChildBuilder<This> startChild(Entity.Blueprint child) {
            RelativePath.Extender extender = myPath.modified();
            Class<? extends AbstractElement<?, ?>> childType = Blueprint.getEntityTypeOf(child);

            if (!extender.canExtendTo(childType)) {
                throw new IllegalArgumentException("Cannot extend path " + myPath + " with child of type " + childType);
            }

            RelativePath childPath = extender.extend(childType, child.getId()).get();

            Set<Entity.Blueprint> bls = getChildrenOfType(EntityType.of(childType));

            bls.add(child);

            blueprints.put(childPath, child);

            return new ChildBuilder<>(castThis(), childPath, blueprints, children);
        }

        /**
         * Returns a child builder of a pre-existing child.
         * @param childPath the path to the child
         * @return the child builder or null
         */
        public ChildBuilder<This> getChild(Path.Segment childPath) {
            Map<EntityType, Set<Entity.Blueprint>> myChildren = children.get(myPath);
            if (myChildren == null) {
                return null;
            }

            EntityType childType = EntityType.of(childPath.getElementType());
            Set<Entity.Blueprint> childrenOfType = myChildren.get(childType);

            return childrenOfType.stream().filter(child -> child.getId().equals(childPath.getElementId()))
                    .findAny().map(child -> {
                        RelativePath cp = myPath.modified().extend(childPath).get();
                        return new ChildBuilder<>(castThis(), cp, blueprints, children);
                    }).orElse(null);
        }

        public This removeChild(Path.Segment childPath) {
            ChildBuilder<This> childBuilder = getChild(childPath);

            if (childBuilder != null) {
                childBuilder.remove();
            }

            return castThis();
        }

        public Set<Path.Segment> getChildrenPaths() {
            Map<EntityType, Set<Entity.Blueprint>> myChildren = children.get(myPath);
            if (myChildren == null) {
                return Collections.emptySet();
            }

            return myChildren.values().stream().flatMap(Collection::stream)
                    .map(b -> new Path.Segment(Blueprint.getEntityTypeOf(b), b.getId())).collect(Collectors.toSet());
        }

        public This removeAllChildren() {
            getChildrenPaths().forEach(this::removeChild);
            children.remove(myPath);
            return castThis();
        }

        /**
         * Replaces the blueprint on this position in the structure with another. The blueprint must have the same type
         * as the original one.
         *
         * @param blueprint the blueprint to replace the current with
         * @return this builder
         */
        public This replace(Entity.Blueprint blueprint) {
            removeAllChildren();

            Entity.Blueprint myBl = blueprints.get(myPath);
            if (!myBl.getClass().equals(blueprint.getClass())) {
                throw new IllegalArgumentException("Blueprint " + blueprint + " not of the same type as " + myBl);
            }

            doReplace(blueprint);

            return castThis();
        }

        protected abstract void doReplace(Entity.Blueprint blueprint);

        protected Set<Entity.Blueprint> getChildrenOfType(EntityType childType) {
            Map<EntityType, Set<Entity.Blueprint>> cs = children.get(myPath);
            if (cs == null) {
                cs = new EnumMap<>(EntityType.class);
                children.put(myPath, cs);
            }

            Set<Entity.Blueprint> bls = cs.get(childType);
            if (bls == null) {
                bls = new HashSet<>();
                cs.put(childType, bls);
            }

            return bls;
        }

        /**
         * Adds a new child to this entity without the possibility to add further grand-children to the child.
         *
         * @param child the child to add to this entity
         * @return this builder
         * @throws IllegalArgumentException if the provided child cannot be contained in the currently built entity
         * (i.e. a resource type cannot be contained in a resource for example).
         */
        public This addChild(Entity.Blueprint child) {
            startChild(child).end();
            return castThis();
        }

        @SuppressWarnings("unchecked")
        protected This castThis() {
            return (This) this;
        }
    }

    class Builder<Root extends Entity.Blueprint> extends AbstractBuilder<Builder<Root>> {
        private final Root root;

        private Builder(Root root, RelativePath myPath,
                       Map<RelativePath, Entity.Blueprint> blueprints,
                       Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children) {
            super(myPath, blueprints, children);
            this.root = root;
            this.blueprints.put(RelativePath.empty().get(), root);
        }

        public Builder(Root root) {
            this(root, RelativePath.empty().get(), new HashMap<>(), new HashMap<>());
        }

        public Offline<Root> build() {
            return new Offline<>(root, blueprints, children);
        }

        @Override protected void doReplace(Entity.Blueprint blueprint) {
            blueprints.put(myPath, blueprint);
            children.remove(myPath);
        }
    }

    class ChildBuilder<ParentBuilder extends AbstractBuilder<?>> extends
            AbstractBuilder<ChildBuilder<ParentBuilder>> {
        protected final ParentBuilder parentBuilder;

        private ChildBuilder(ParentBuilder parentBuilder, RelativePath parent,
                             Map<RelativePath, Entity.Blueprint> blueprints,
                             Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children) {
            super(parent, blueprints, children);
            this.parentBuilder = parentBuilder;
        }

        /**
         * Ends the current child and returns the builder of the parent entity.
         * @return the builder of the parent entity
         */
        public ParentBuilder end() {
            return parentBuilder;
        }

        /**
         * Removes this child from the structure.
         * @return the parent builder
         */
        public ParentBuilder remove() {
            removeAllChildren();
            Set<Entity.Blueprint> siblings = getSiblings();
            Entity.Blueprint myBlueprint = blueprints.remove(myPath);
            siblings.remove(myBlueprint);
            return parentBuilder;
        }

        @Override protected void doReplace(Entity.Blueprint blueprint) {
            Set<Entity.Blueprint> siblings = getSiblings();
            Entity.Blueprint myBlueprint = blueprints.remove(myPath);
            siblings.remove(myBlueprint);
            siblings.add(blueprint);
            children.remove(myPath);
            myPath = parentBuilder.myPath.modified().extend(Blueprint.getEntityTypeOf(blueprint), blueprint.getId())
                    .get();
            blueprints.put(myPath, blueprint);
        }

        private Set<Entity.Blueprint> getSiblings() {
            Entity.Blueprint myBlueprint = blueprints.get(myPath);
            Map<EntityType, Set<Entity.Blueprint>> siblingsByType = children.get(parentBuilder.myPath);
            EntityType myType = EntityType.of(Blueprint.getEntityTypeOf(myBlueprint));
            return siblingsByType.get(myType);
        }
    }
}
