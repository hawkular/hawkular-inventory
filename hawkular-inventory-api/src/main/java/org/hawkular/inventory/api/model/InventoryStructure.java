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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResolvableToSingleWithRelationships;
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Pager;

/**
 * Represents the structure of an inventory. It is supposed that the structure is loaded lazily. The structure is
 * represented using entity blueprints instead of entity types themselves so that this structure can be computed
 * offline, without access to all information up to the tenant.
 *
 * @author Lukas Krejci
 * @since 0.11.0
 */
public interface InventoryStructure<Root extends Blueprint> {

    /**
     * Creates a lazily loaded online inventory structure, backed by the provided inventory instance.
     *
     * @param rootEntity the root entity of which to create the structure of
     * @param inventory  the inventory to load the data from
     * @return the structure of given entity and its children
     */
    static <E extends Entity<B, ?>, B extends Blueprint>
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
                        return fromRead(Tenant.class, Tenants.Single.class, (ts) -> ts.environments());
                    }

                    @Override public Stream<BB> visitFeed(Void parameter) {
                        return fromRead(Tenant.class, Tenants.Single.class, (ts) -> ts.feeds());
                    }

                    @Override public Stream<BB> visitMetric(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitEnvironment(Void parameter) {
                                return fromRead(Environment.class, Environments.Single.class, (es) -> es.metrics());
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, (es) -> es.metrics());
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitMetricType(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitTenant(Void parameter) {
                                return fromRead(Tenant.class, Tenants.Single.class, (es) -> es.metricTypes());
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, (es) -> es.metricTypes());
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitResource(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitEnvironment(Void parameter) {
                                return fromRead(Environment.class, Environments.Single.class, (es) -> es.resources());
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, (es) -> es.resources());
                            }

                            @Override public Stream<BB> visitResource(Void parameter) {
                                return fromRead(Resource.class, Resources.Single.class, (es) -> es.resources());
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitResourceType(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitTenant(Void parameter) {
                                return fromRead(Tenant.class, Tenants.Single.class, (es) -> es.resourceTypes());
                            }

                            @Override public Stream<BB> visitFeed(Void parameter) {
                                return fromRead(Feed.class, Feeds.Single.class, (es) -> es.resourceTypes());
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitRelationship(Void parameter) {
                        return Stream.empty();
                    }

                    @Override public Stream<BB> visitData(Void parameter) {
                        return ElementTypeVisitor.accept(parentType, new ElementTypeVisitor.Simple<Stream<BB>, Void>() {
                            @Override public Stream<BB> visitResource(Void parameter) {
                                return fromRead(Resource.class, Resources.Single.class, (rs) -> rs.data());
                            }

                            @Override public Stream<BB> visitResourceType(Void parameter) {
                                return fromRead(ResourceType.class, ResourceTypes.Single.class, (rts) -> rts.data());
                            }

                            @Override public Stream<BB> visitOperationType(Void parameter) {
                                return fromRead(OperationType.class, OperationTypes.Single.class,
                                        OperationTypes.BrowserBase::data);
                            }
                        }, null);
                    }

                    @Override public Stream<BB> visitOperationType(Void parameter) {
                        return fromRead(ResourceType.class, ResourceTypes.Single.class, (rts) -> rts.operationTypes());
                    }

                    @Override public Stream<BB> visitMetadataPack(Void parameter) {
                        return fromRead(Tenant.class, Tenants.Single.class, (ts) -> ts.metadataPacks());
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

            @Override
            public Stream<Relationship> getRelationships(RelativePath sourceEntityPath, String... names) {
                CanonicalPath absoluteSource = rootEntity.getPath().modified().extend(sourceEntityPath.getPath()).get();
                @SuppressWarnings("unchecked")
                ResolvableToSingle<?, ?> access = inventory.inspect(absoluteSource, ResolvableToSingle.class);

                if (access instanceof ResolvableToSingleWithRelationships) {
                    Iterator<org.hawkular.inventory.api.model.Relationship> rels =
                            ((ResolvableToSingleWithRelationships<?, ?>) access)
                                    .relationships(Relationships.Direction.outgoing).getAll()
                                    .entities(Pager.unlimited(Order.unspecified()));

                    Spliterator<org.hawkular.inventory.api.model.Relationship> sit = Spliterators.spliterator(rels,
                            Long.MAX_VALUE, Spliterator.DISTINCT & Spliterator.IMMUTABLE & Spliterator.NONNULL);

                    return StreamSupport.stream(sit, false).map(r -> {
                        RelativePath source = r.getSource().relativeTo(rootEntity.getPath());
                        RelativePath target = r.getTarget().relativeTo(rootEntity.getPath());

                        return new Relationship(r.getName(), target);
                    });
                } else {
                    return Stream.empty();
                }
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

    @SuppressWarnings("unchecked")
    default Stream<Entity.Blueprint> getAllChildren(RelativePath parent) {
        Stream ret = Stream.empty();
        for (Class<?> cls : CanonicalPath.SHORT_TYPE_NAMES.keySet()) {
            if (Entity.class.isAssignableFrom(cls)) {
                ret = Stream.concat(ret, getChildren(parent, (Class<Entity>) cls));
            }
        }

        return ret;
    }

    /**
     * Returns the <b>outgoing</b> relationships of an entity on the provided path with given names. If no names are
     * supplied (either null or an empty array), all relationships are returned.
     *
     * @param sourceEntityPath the path to the source of the relationships, relative to the root entity
     * @param names            the names of the relationships to retrieve or empty for all relationships
     * @return the stream of relationships
     */
    Stream<Relationship> getRelationships(RelativePath sourceEntityPath, String... names);

    /**
     * A specliazed representation of relationship that needs to be used instead of the "real"
     * {@link org.hawkular.inventory.api.model.Relationship} so that relative paths can be used to specify the targets.
     */
    final class Relationship {
        private final String name;
        private final RelativePath target;

        public Relationship(String name, RelativePath target) {
            this.name = name;
            this.target = target;
        }

        public String getName() {
            return name;
        }

        public RelativePath getTarget() {
            return target;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Relationship that = (Relationship) o;

            return name.equals(that.name) && target.equals(that.target);
        }

        @Override public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + target.hashCode();
            return result;
        }

        @Override public String toString() {
            return "Relationship[" + "name='" + name + '\'' +
                    ", target=" + target +
                    ']';
        }
    }

    enum EntityType {
        environment(Environment.class, Environment.Blueprint.class),
        resourceType(ResourceType.class, ResourceType.Blueprint.class),
        metricType(MetricType.class, MetricType.Blueprint.class),
        operationType(OperationType.class, OperationType.Blueprint.class),
        feed(Feed.class, Feed.Blueprint.class),
        metric(Metric.class, Metric.Blueprint.class),
        resource(Resource.class, Resource.Blueprint.class),
        dataEntity(DataEntity.class, DataEntity.Blueprint.class),
        metadataPack(MetadataPack.class, MetadataPack.Blueprint.class);

        final Class<? extends AbstractElement<?, ?>> elementType;
        final Class<? extends Blueprint> blueprintType;

        public static EntityType of(Class<?> type) {
            for (EntityType t : EntityType.values()) {
                if (type.equals(t.elementType)) {
                    return t;
                }
            }

            return null;
        }

        EntityType(Class<? extends Entity<?, ?>> elementType, Class<? extends Blueprint> blueprintType) {
            this.elementType = elementType;
            this.blueprintType = blueprintType;
        }

        public Class<? extends Blueprint> getBlueprintType() {
            return blueprintType;
        }

        public Class<? extends AbstractElement<?, ?>> getElementType() {
            return elementType;
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
        private final Map<RelativePath, Map<String, Set<Relationship>>> relationships;

        private Offline(Root root, Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children,
                        Map<RelativePath, Map<String, Set<Relationship>>> relationships) {
            this.root = root;
            this.children = children;
            this.relationships = relationships;
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
                                    parentChildren = new EntityAndChildren();
                                    entities.put(parentPath, parentChildren);
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
            Map<RelativePath, Map<String, Set<Relationship>>> relationships = new HashMap<>();

            for (Map.Entry<RelativePath, EntityAndChildren> e : entities.entrySet()) {
                //handle entities
                RelativePath entityPath = e.getKey();
                EntityAndChildren entity = e.getValue();

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

                //handle relationships
                Stream<Relationship> otherRels = other.getRelationships(entityPath);
                Map<String, Set<Relationship>> myRels = relationships.get(entityPath);

                otherRels.forEach(r -> {
                    Set<Relationship> relsOfName = myRels.get(r.getName());
                    if (relsOfName == null) {
                        relsOfName = new HashSet<>();
                        myRels.put(r.getName(), relsOfName);
                    }
                    relsOfName.add(r);
                });
            }

            return new Offline<>(root, children, relationships);
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

        @Override
        public Stream<Relationship> getRelationships(RelativePath sourceEntityPath, String... names) {
            Map<String, Set<Relationship>> rels = relationships.getOrDefault(sourceEntityPath, Collections.emptyMap());

            if (names == null || names.length == 0) {
                return rels.values().stream().flatMap(Collection::stream);
            } else {
                return Stream.of(names).flatMap(n -> rels.getOrDefault(n, Collections.emptySet()).stream());
            }
        }

        private static final class EntityAndChildren {
            final Map<Class<?>, Map<String, Entity.Blueprint>> children = new HashMap<>();
            Entity.Blueprint entity;

            EntityAndChildren() {
            }

            EntityAndChildren(Entity.Blueprint entity) {
                this.entity = entity;
            }

            void addChild(Entity.Blueprint child) {
                Map<String, Entity.Blueprint> byId = children.get(child.getClass());
                if (byId == null) {
                    byId = new HashMap<>();
                    children.put(child.getClass(), byId);
                }
                byId.put(child.getId(), child);
            }
        }
    }

    class AbstractBuilder<This extends AbstractBuilder<?>> {
        protected final RelativePath myPath;
        protected final Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children;
        protected final Map<RelativePath, Map<String, Set<Relationship>>> relationships;

        private AbstractBuilder(RelativePath myPath, Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children,
                                Map<RelativePath, Map<String, Set<Relationship>>> relationships) {
            this.myPath = myPath;
            this.children = children;
            this.relationships = relationships;
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

            Map<EntityType, Set<Entity.Blueprint>> cs = children.get(myPath);
            if (cs == null) {
                cs = new EnumMap<>(EntityType.class);
                children.put(myPath, cs);
            }

            Set<Entity.Blueprint> bls = cs.get(EntityType.of(childType));
            if (bls == null) {
                bls = new HashSet<>();
                cs.put(EntityType.of(childType), bls);
            }

            bls.add(child);

            return new ChildBuilder<>(castThis(), childPath, children, relationships);
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
            startChild(child);
            return castThis();
        }

        public This addRelationship(String name, RelativePath target) {
            Map<String, Set<Relationship>> relsByName = relationships.get(myPath);
            if (relsByName == null) {
                relsByName = new HashMap<>();
                relationships.put(myPath, relsByName);
            }

            Set<Relationship> rels = relsByName.get(name);
            if (rels == null) {
                rels = new HashSet<>();
                relsByName.put(name, rels);
            }

            rels.add(new Relationship(name, target));

            return castThis();
        }

        @SuppressWarnings("unchecked")
        protected This castThis() {
            return (This) this;
        }
    }

    class Builder<Root extends Entity.Blueprint> extends AbstractBuilder<Builder<Root>> {
        private final Root root;

        public Builder(Root root) {
            super(RelativePath.empty().get(), new HashMap<>(), new HashMap<>());
            this.root = root;
        }

        public InventoryStructure<Root> build() {
            return new Offline<>(root, children, relationships);
        }
    }

    class ChildBuilder<ParentBuilder extends AbstractBuilder<?>> extends
            AbstractBuilder<ChildBuilder<ParentBuilder>> {
        protected final ParentBuilder parentBuilder;

        private ChildBuilder(ParentBuilder parentBuilder, RelativePath parent,
                             Map<RelativePath, Map<EntityType, Set<Entity.Blueprint>>> children,
                             Map<RelativePath, Map<String, Set<Relationship>>> relationships) {
            super(parent, children, relationships);
            this.parentBuilder = parentBuilder;
        }

        /**
         * Ends the current child and returns the builder of the parent entity.
         * @return the builder of the parent entity
         */
        public ParentBuilder end() {
            return parentBuilder;
        }
    }
}
