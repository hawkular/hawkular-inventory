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
import java.util.List;
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
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.ElementTypeVisitor;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;

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
            B root = Inventory.asBlueprint(rootEntity);

            @Override public B getRoot() {
                return root;
            }

            @Override
            public <EE extends Entity<? extends BB, ?>, BB extends Blueprint> Stream<BB>
            getChildren(RelativePath parent, Class<EE> childType) {
                CanonicalPath absoluteParent = rootEntity.getPath().modified().extend(parent.getPath()).get();
                SegmentType parentType = absoluteParent.getSegment().getElementType();

                SegmentType childSegment = SegmentType.fromElementType(childType);

                return ElementTypeVisitor.accept(childSegment, new ElementTypeVisitor<Stream<BB>, Void>() {

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

                        Class<?> parentSegmentType = AbstractElement.toElementClass(absoluteParent.getSegment()
                                .getElementType());

                        if (!(absoluteParent.isDefined() && parentType.equals(parentSegmentType))) {
                            return Stream.empty();
                        } else {
                            PA parentAccess = inventory.inspect(absoluteParent, parentAccessType);
                            return fromRead(childAccessSupplier.apply(parentAccess));
                        }
                    }

                    @SuppressWarnings("unchecked")
                    private <X extends Entity<XB, ?>, XB extends Blueprint>
                    Stream<BB> fromRead(ResolvingToMultiple<? extends ResolvableToMany<X>> read) {
                        Page<X> it = read.getAll().entities(Pager.none());
                        Spliterator<X> sit = Spliterators.spliterator(it, Long.MAX_VALUE, Spliterator.DISTINCT &
                                Spliterator.IMMUTABLE & Spliterator.NONNULL);

                        return StreamSupport.stream(sit, false).map(e -> (BB) Inventory.asBlueprint(e))
                                .onClose(it::close);
                    }
                }, null);
            }

            @Override public Blueprint get(RelativePath path) {
                CanonicalPath pathToElement = rootEntity.getPath().modified().extend(path.getPath()).get();

                @SuppressWarnings("unchecked")
                Entity<?, ?> entity = (Entity<?, ?>) inventory.inspect(pathToElement, ResolvableToSingle.class)
                        .entity();

                return Inventory.asBlueprint(entity);
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
     * <p><b>WARNING</b>: the returned stream MUST BE closed after processing.
     *
     * @param parent    the path to the parent entity, relative to the root entity
     * @param childType the type of the child entities to retrieve
     * @param <E>       the type of the child entities
     * @param <B>       the type of the child entity blueprint
     * @return a stream of blueprints corresponding to the child entities.
     */
    <E extends Entity<? extends B, ?>, B extends Blueprint> Stream<B> getChildren(RelativePath parent,
                                                                                  Class<E> childType);

    /**
     * Gets a blueprint on the given path.
     *
     * @param path the path under the root of the structure
     * @return the blueprint describing the entity on the given path
     */
    Blueprint get(RelativePath path);

    /**
     * <b>WARNING</b>: the returned stream MUST BE closed after processing.
     *
     * @param parent
     * @return the stream of all children of the parent
     */
    @SuppressWarnings("unchecked")
    default Stream<Entity.Blueprint> getAllChildren(RelativePath parent) {
        Stream ret = Stream.empty();
        RelativePath.Extender check = RelativePath.empty()
                .extend(Blueprint.getSegmentTypeOf(getRoot()),getRoot().getId())
                .extend(parent.getPath());

        for (SegmentType st : SegmentType.values()) {
            if (st != SegmentType.rl && EntityType.supports(st) && check.canExtendTo(st)) {
                //streams are a pain..
                //we need to ensure that the children are evaluated 1 by one - 1 that no 2 queries run
                //in parallel... in here we do not know if we are loading directly from a backend store
                //or from memory.
                //We cannot guarantee the transactional behavior of a backend - even if it supports a transaction frame
                //it still may choose to use multiple transactions during the lifetime of a frame.
                //Streams are evaluated lazily and concat'ed stream calls close on the underlying streams (i.e. commit()
                //of the txs that we might be using) during its close method. As such, if we just concat'ed our calls
                //to get children, we might try to nest the transactions - which is not supported by Titan at least.
                //To overcome this, eagerly evaluate each of our children and compose the resulting stream from the
                //loaded results.
                List<?> res;
                try (Stream<?> next = getChildren(parent, Entity.entityTypeFromSegmentType(st))) {
                    res = next.collect(Collectors.toList());
                }

                ret = Stream.concat(ret, res.stream());
            }
        }

        return ret;
    }

    /**
     * This enum lists all the entity types that can be part of a inventory structure. This is a subset of all entity
     * types because not all entities are {@link IdentityHashable}.
     */
    enum EntityType {
        //the order is significant.. the latter cannot exist without (some of) the prior
        feed(Feed.class, Feed.Blueprint.class, SegmentType.f),
        resourceType(ResourceType.class, ResourceType.Blueprint.class, SegmentType.rt),
        metricType(MetricType.class, MetricType.Blueprint.class, SegmentType.mt),
        operationType(OperationType.class, OperationType.Blueprint.class, SegmentType.ot),
        metric(Metric.class, Metric.Blueprint.class, SegmentType.m),
        resource(Resource.class, Resource.Blueprint.class, SegmentType.r),
        dataEntity(DataEntity.class, DataEntity.Blueprint.class, SegmentType.d);

        public final Class<? extends Entity<?, ?>> elementType;
        public final Class<? extends Entity.Blueprint> blueprintType;
        public final SegmentType segmentType;

        public static EntityType of(Class<?> type) {
            for (EntityType t : EntityType.values()) {
                if (type.equals(t.elementType)) {
                    return t;
                }
            }

            throw new IllegalArgumentException("Unsupported type of entity: " + type);
        }

        public static EntityType of(SegmentType seg) {
            for (EntityType t : EntityType.values()) {
                if (seg == t.segmentType) {
                    return t;
                }
            }

            throw new IllegalArgumentException("Unsupported type of path segment: " + seg);
        }

        public static boolean supports(SegmentType seg) {
            for (EntityType t : EntityType.values()) {
                if (seg == t.segmentType) {
                    return true;
                }
            }

            return false;
        }

        public static EntityType ofBlueprint(Class<?> type) {
            for (EntityType t : EntityType.values()) {
                if (type.equals(t.blueprintType)) {
                    return t;
                }
            }

            return null;
        }

        EntityType(Class<? extends Entity<?, ?>> elementType, Class<? extends Entity.Blueprint> blueprintType,
                   SegmentType segmentType) {
            this.elementType = elementType;
            this.blueprintType = blueprintType;
            this.segmentType = segmentType;
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
    @ApiModel("InventoryStructure")
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
                        @Override protected Void defaultAction(SegmentType elementType,
                                                               RelativePath.Extender parentPath) {

                            @SuppressWarnings("unchecked")
                            Class<Entity<Entity.Blueprint, ?>> childType =
                                    (Class) Entity.typeFromSegmentType(elementType);

                            impl(childType, parentPath);

                            return null;
                        }

                        private <E extends Entity<B, ?>, B extends Entity.Blueprint>
                        void impl(Class<E> childType, RelativePath.Extender parent) {
                            SegmentType childSeg = Entity.segmentTypeFromType(childType);
                            if (parent.canExtendTo(childSeg)) {
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

                                //we cannot recursively call ourselves while evaluating the stream, because that
                                //would result in nested transactions, which are not supported...
                                List<B> otherChildren;
                                try (Stream<B> s = other.getChildren(parent.get(), childType)) {
                                    otherChildren = s.collect(Collectors.toList());
                                }

                                otherChildren.forEach(c -> {
                                    RelativePath.Extender childPath = parentPath.modified()
                                            .extend(childSeg, c.getId());

                                    RelativePath cp = childPath.get();

                                    EntityAndChildren childChildren = entities.get(cp);
                                    if (childChildren == null) {
                                        childChildren = new EntityAndChildren(c);
                                        entities.put(cp, childChildren);
                                    }

                                    for (SegmentType childChildSeg : SegmentType.values()) {
                                        if (childPath.canExtendTo(childChildSeg)
                                                && EntityType.supports(childChildSeg)) {
                                            ElementTypeVisitor.accept(childChildSeg, this, childPath);
                                        }
                                    }
                                });
                            }
                        }
                    };

            R root = other.getRoot();

            RelativePath empty = RelativePath.empty().get();

            //this is important. We need to eagerly collect the children because we don't know if the other structure
            //is online or offline. The backends usually don't support nested transactions and if the other is online
            //inventory structure, then getAllChildren() opens a transaction. If we then processed the items in all
            //children one by one from the stream, the visitor would then call to fetch other children recursively,
            //potentially spawning other transactions. Backends that do not support nested txs would then freak out.
            //The solution therefore is to fetch children eagerly (closing the stream) and then process them 1 by 1.
            List<Entity.Blueprint> acs;
            try (Stream<Entity.Blueprint> s = other.getAllChildren(empty)) {
                acs = s.collect(Collectors.toList());
            }
            acs.forEach(b -> ElementTypeVisitor.accept(Blueprint.getSegmentTypeOf(b), visitor,
                    empty.modified()));

            Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children = new HashMap<>();
            Map<RelativePath, Entity.Blueprint> blueprints = new HashMap<>();

            for (Map.Entry<RelativePath, EntityAndChildren> e : entities.entrySet()) {
                //handle entities
                RelativePath entityPath = e.getKey();
                EntityAndChildren entity = e.getValue();

                blueprints.put(entityPath, entity.entity);

                RelativePath parent = entityPath.up();
                if (parent.equals(entityPath)) {
                    //if we can no longer go up, don't add this entity as a child of its parent. It would add itself
                    //as its own child...
                    continue;
                }
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
        public InventoryStructure.Builder<Root> asBuilder() {
            return new InventoryStructure.Builder<>(root, RelativePath.empty().get(), entities, children);
        }

        public static <R extends Entity.Blueprint> Builder<R> of(R root) {
            return new Builder<>(root);
        }

        @Override public Root getRoot() {
            return root;
        }

        @SuppressWarnings("unchecked") @Override
        public <E extends Entity<? extends B, ?>, B extends Blueprint> Stream<B>
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

            SegmentType childSeg = Blueprint.getSegmentTypeOf(child);

            if (!extender.canExtendTo(childSeg)) {
                throw new IllegalArgumentException("Cannot extend path " + myPath + " with child of type " + childType);
            }

            RelativePath childPath = extender.extend(childSeg, child.getId()).get();

            Set<Entity.Blueprint> bls = getChildrenOfType(EntityType.of(childType));

            bls.add(child);

            blueprints.put(childPath, child);

            return new ChildBuilder<>(castThis(), childPath, blueprints, children);
        }

        public RelativePath getPath() {
            return myPath;
        }

        public Entity.Blueprint getBlueprint() {
            return blueprints.get(myPath);
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
                        RelativePath rp = myPath.modified().extend(childPath).get();
                        return new ChildBuilder<>(castThis(), rp, blueprints, children);
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
                    .map(b -> new Path.Segment(Blueprint.getSegmentTypeOf(b), b.getId())).collect(Collectors.toSet());
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

        public This addChild(InventoryStructure<?> structure, boolean overwrite) {
            return addChild(Offline.copy(structure).asBuilder(), overwrite);
        }

        public This addChild(AbstractBuilder<?> structure, boolean overwrite) {
            RelativePath structureRoot = structure.getPath();
            for (Map.Entry<RelativePath, Entity.Blueprint> e : structure.blueprints.entrySet()) {
                RelativePath structurePath = e.getKey();

                //only copy subpaths of the structure
                if (!structureRoot.equals(structurePath) && !structureRoot.isParentOf(structurePath)) {
                    continue;
                }

                RelativePath strippedStructurePath = structurePath.slide(structureRoot.getDepth(), 0);
                RelativePath newStructurePath = myPath.modified().extend(strippedStructurePath.getPath()).get();

                //add the blueprint to the list of my blueprints
                if (overwrite || !blueprints.containsKey(newStructurePath)) {
                    blueprints.put(newStructurePath, e.getValue());
                }

                //need to make sure that the current structure is listed in my children list
                RelativePath parentPath = newStructurePath.up();
                Map<EntityType, Set<Entity.Blueprint>> parentChildren = children.get(parentPath);
                if (parentChildren == null) {
                    parentChildren = new HashMap<>();
                    this.children.put(parentPath, parentChildren);
                }

                EntityType structureType = EntityType.of(newStructurePath.getSegment().getElementType());

                Set<Entity.Blueprint> childrenOfType = parentChildren.get(structureType);
                if (childrenOfType == null) {
                    childrenOfType = new HashSet<>();
                    parentChildren.put(structureType, childrenOfType);
                }

                childrenOfType.add(e.getValue());

                //and now, quickly make sure that all the structure's relevant children are added to my children list
                Map<EntityType, Set<Entity.Blueprint>> structureChildren = structure.children.get(structurePath);
                if (structureChildren == null || structureChildren.isEmpty()) {
                    continue;
                }

                Map<EntityType, Set<Entity.Blueprint>> currentChildren = children.get(newStructurePath);
                if (currentChildren == null) {
                    currentChildren = new EnumMap<>(EntityType.class);
                    children.put(newStructurePath, currentChildren);
                }

                for (Map.Entry<EntityType, Set<Entity.Blueprint>> ee : structureChildren.entrySet()) {
                    EntityType entityType = ee.getKey();
                    Set<Entity.Blueprint> structureBlueprints = ee.getValue();

                    Set<Entity.Blueprint> currentBlueprints = currentChildren.get(entityType);
                    if (currentBlueprints == null) {
                        currentBlueprints = new HashSet<>();
                        currentChildren.put(entityType, currentBlueprints);
                    }

                    for (Entity.Blueprint structureBlueprint : structureBlueprints) {
                        if (overwrite || !currentBlueprints.contains(structureBlueprint)) {
                            currentBlueprints.add(structureBlueprint);
                        }
                    }
                }
            }
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
            myPath = parentBuilder.myPath.modified().extend(Blueprint.getSegmentTypeOf(blueprint), blueprint.getId())
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
