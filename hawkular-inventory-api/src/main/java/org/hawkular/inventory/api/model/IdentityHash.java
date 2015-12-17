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
package org.hawkular.inventory.api.model;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

import static org.hawkular.inventory.api.OperationTypes.DataRole.parameterTypes;
import static org.hawkular.inventory.api.OperationTypes.DataRole.returnType;
import static org.hawkular.inventory.api.ResourceTypes.DataRole.configurationSchema;
import static org.hawkular.inventory.api.ResourceTypes.DataRole.connectionConfigurationSchema;
import static org.hawkular.inventory.api.Resources.DataRole.configuration;
import static org.hawkular.inventory.api.Resources.DataRole.connectionConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.swing.text.BadLocationException;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;

/**
 * Produces an identity hash of entities. Identity hash is a hash that uniquely identifies an entity
 * and is produced using its user-defined id and structure. This hash is used to match a client-side state of an
 * entity (MetadataPack, ResourceType, MetricType) with the severside state of it.
 * <p>
 * The identity hash is defined only for the following types of entities:
 * {@link ResourceType}, {@link MetricType}, {@link OperationType}, {@link Metric} and {@link Resource}.
 * <p>
 * The identity hash is an SHA1 hash of a string representation of the entity (in UTF-8 encoding). The string
 * representation is produced as follows:
 * <ol>
 * <li>MetricType: id + type + unit
 * <li>OperationType: id + minimized(returnTypeJSON) + minimized(parameterTypesJSON)
 * <li>ResourceType: id + minimized(configurationSchemaJSON) + minimized(connectionConfigurationSchemaJSON)
 * + operationTypeString*<br/>
 * the operation types are sorted alphabetically by their ids
 * </ol>
 * where {@code minimized()} means that the JSON is stripped of any superfluous whitespace and {@code *} means
 * repetition for necessary number of times.
 *
 * @author Lukas Krejci
 * @since 0.7.0
 */
public final class IdentityHash {

    private static final RelativePath EMPTY_PATH = RelativePath.empty().get();

    private static final Comparator<Entity<?, ?>> ENTITY_COMPARATOR = (a, b) -> {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;

        if (!a.getClass().equals(b.getClass())) {
            return a.getClass().getName().compareTo(b.getClass().getName());
        } else {
            return a.getId().compareTo(b.getId());
        }
    };

    private static final Comparator<Entity.Blueprint> BLUEPRINT_COMPARATOR = (a, b) -> {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;

        Class<? extends Entity<Entity.Blueprint, ?>> ac = Blueprint.getEntityTypeOf(a);
        Class<? extends Entity<Entity.Blueprint, ?>> bc = Blueprint.getEntityTypeOf(b);

        if (!ac.equals(bc)) {
            return ac.getName().compareTo(bc.getName());
        } else {
            return a.getId().compareTo(b.getId());
        }
    };

    private IdentityHash() {

    }

    public static String of(MetadataPack.Members metadata) {
        DigestComputingWriter wrt = new DigestComputingWriter(newDigest());

        HashableView metadataView = HashableView.of(metadata);

        SortedSet<Entity.Blueprint> all = new TreeSet<>(BLUEPRINT_COMPARATOR);
        all.addAll(metadata.getMetricTypes());
        all.addAll(metadata.getResourceTypes());

        for (Entity.Blueprint bl : all) {
            appendStringRepresentation(bl, metadataView, wrt);
        }

        return computeDigest(wrt);
    }

    public static String of(Entity<?, ?> entity, Inventory inventory) {
        DigestComputingWriter wrt = new DigestComputingWriter(newDigest());

        appendStringRepresentation(asBlueprint(entity), HashableView.of(entity, inventory), wrt);

        return computeDigest(wrt);
    }

    public static Tree treeOf(Entity<?, ?> entity, Inventory inventory) {
        HashableView view = HashableView.of(entity, inventory);
        // TODO implement
        return null;
    }

    public static String of(Iterable<? extends Entity<?, ?>> entities, Inventory inventory) {
        return of(entities.iterator(), inventory);
    }

    public static String of(Iterator<? extends Entity<?, ?>> entities, Inventory inventory) {
        SortedSet<Entity<?, ?>> sortedEntities = new TreeSet<>(ENTITY_COMPARATOR);

        entities.forEachRemaining(sortedEntities::add);

        DigestComputingWriter wrt = new DigestComputingWriter(newDigest());

        sortedEntities.forEach((e) -> {
            HashableView v = HashableView.of(e, inventory);
            appendStringRepresentation(asBlueprint(e), v, wrt);
        });

        return computeDigest(wrt);
    }

    private static void appendStringRepresentation(Blueprint entity, HashableView structure,
                                                   Appendable bld) {

        ArrayDeque<Entity.Blueprint> visitedPath = new ArrayDeque<>();

        Supplier<RelativePath> pathFromRoot = () -> {
            RelativePath.Extender ret = RelativePath.empty();

            Iterator<Entity.Blueprint> it = visitedPath.iterator();
            if (it.hasNext()) {
                //leave out the first element on the path stack - it corresponts to the root entity and we're
                //composing a path from it downwards
                it.next();
            }

            while (it.hasNext()) {
                Entity.Blueprint bl = it.next();
                ret.extend(Blueprint.getEntityTypeOf(bl), bl.getId());
            }

            return ret.get();
        };

        entity.accept(new ElementBlueprintVisitor.Simple<Void, Void>() {
            @Override
            public Void visitData(DataEntity.Blueprint<?> data, Void parameter) {
                return wrap(() -> data.getValue().writeJSON(bld));
            }

            @Override
            public Void visitMetricType(MetricType.Blueprint mt, Void parameter) {
                return wrap(() -> bld.append(mt.getId()).append(mt.getType().name()).append(mt.getUnit().name()));
            }

            @Override
            public Void visitOperationType(OperationType.Blueprint operationType, Void parameter) {
                return wrap(() -> {
                    RelativePath rootPath = pathFromRoot.get();

                    DataEntity.Blueprint<?> returnType = structure.getReturnType(rootPath, operationType);
                    DataEntity.Blueprint<?> parameterTypes = structure.getParameterTypes(rootPath, operationType);

                    bld.append(operationType.getId());

                    visitedPath.push(operationType);

                    returnType.accept(this, null);
                    parameterTypes.accept(this, null);

                    visitedPath.pop();
                });
            }

            @Override
            public Void visitResourceType(ResourceType.Blueprint type, Void parameter) {
                return wrap(() -> {
                    DataEntity.Blueprint<?> configSchema = structure.getConfigurationSchema(type);
                    DataEntity.Blueprint<?> connSchema = structure.getConnectionConfigurationSchema(type);
                    List<OperationType.Blueprint> ots = structure.getOperationTypes(type);

                    bld.append(type.getId());

                    visitedPath.push(type);

                    configSchema.accept(this, null);
                    connSchema.accept(this, null);

                    ots.forEach((ot) -> ot.accept(this, null));

                    visitedPath.pop();
                });
            }

            @Override
            public Void visitFeed(Feed.Blueprint feed, Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public Void visitMetric(Metric.Blueprint metric, Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public Void visitResource(Resource.Blueprint resource, Void parameter) {
                //TODO implement
                return null;
            }
        }, null);
    }

    private static Void wrap(FailingPayload payload) {
        try {
            payload.run();
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Identity hash computation failed.", e);
        }
    }

    private static String computeDigest(DigestComputingWriter bld) {
        try {
            bld.close();
            return bld.digest();
        } catch (IOException e) {
            throw new IllegalStateException("Could not produce and SHA-1 hash.", e);
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not instantiate SHA-1 digest algorithm.", e);
        }
    }

    private static <R extends DataEntity.Role> DataEntity.Blueprint<R> dummyDataBlueprint(R role) {
        return DataEntity.Blueprint.<R>builder().withRole(role).withValue(StructuredData.get().undefined()).build();
    }

    @SuppressWarnings("unchecked")
    private static <B extends Blueprint> B asBlueprint(Entity<B, ?> entity) {
        if (entity == null) {
            return null;
        }
        return entity.accept(new ElementVisitor.Simple<B, Void>() {
            @Override public B visitData(DataEntity data, Void parameter) {
                return (B) fillCommon(data, new DataEntity.Blueprint.Builder<>()).withRole(data.getRole())
                        .withValue(data.getValue()).build();
            }

            @Override public B visitFeed(Feed feed, Void parameter) {
                return (B) fillCommon(feed, Feed.Blueprint.builder()).build();
            }

            @Override public B visitMetric(Metric metric, Void parameter) {
                //we don't want to have tenant ID and all that jazz influencing the hash, so always use a relative path
                RelativePath metricTypePath = metric.getType().getPath().relativeTo(metric.getPath());

                return (B) fillCommon(metric, Metric.Blueprint.builder()).withInterval(metric.getCollectionInterval())
                    .withMetricTypePath(metricTypePath.toString()).build();
            }

            @Override public B visitMetricType(MetricType type, Void parameter) {
                return (B) fillCommon(type, MetricType.Blueprint.builder(type.getType()))
                        .withInterval(type.getCollectionInterval()).withUnit(type.getUnit()).build();
            }

            @Override public B visitOperationType(OperationType operationType, Void parameter) {
                return (B) fillCommon(operationType, OperationType.Blueprint.builder()).build();
            }

            @Override public B visitResource(Resource resource, Void parameter) {
                //we don't want to have tenant ID and all that jazz influencing the hash, so always use a relative path
                RelativePath resourceTypePath = resource.getType().getPath().relativeTo(resource.getPath());

                return (B) fillCommon(resource, Resource.Blueprint.builder())
                        .withResourceTypePath(resourceTypePath.toString()).build();
            }

            @Override public B visitResourceType(ResourceType type, Void parameter) {
                return (B) fillCommon(type, ResourceType.Blueprint.builder()).build();
            }

            private <E extends Entity<? extends Bl, ?>, Bl extends Entity.Blueprint,
                    BB extends Entity.Blueprint.Builder<Bl, BB>>
            BB fillCommon(E entity, BB bld) {
                return bld.withId(entity.getId()).withName(entity.getName()).withProperties(entity.getProperties());
            }
        }, null);
    }

    interface FailingPayload {
        void run() throws Exception;
    }

    private static final class DigestComputingWriter implements Appendable, Closeable {
        private final MessageDigest digester;
        private final CharsetEncoder enc = Charset.forName("UTF-8").newEncoder().onMalformedInput(CodingErrorAction
                .REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);

        private final ByteBuffer buffer = ByteBuffer.allocate(512);
        private String digest;

        //TODO remove this once done debugging
        private StringBuilder ________tmpView = new StringBuilder();

        private DigestComputingWriter(MessageDigest digester) {
            this.digester = digester;
        }

        @Override
        public DigestComputingWriter append(CharSequence csq) throws IOException {
            consumeBuffer(CharBuffer.wrap(csq));
            return this;
        }

        @Override
        public DigestComputingWriter append(CharSequence csq, int start, int end) throws IOException {
            consumeBuffer(CharBuffer.wrap(csq, start, end));
            return this;
        }

        @Override
        public DigestComputingWriter append(char c) throws IOException {
            consumeBuffer(CharBuffer.wrap(new char[]{c}));
            return this;
        }

        @Override
        public void close() throws IOException {
            byte[] digest = digester.digest();

            StringBuilder bld = new StringBuilder();
            for (byte b : digest) {
                bld.append(Integer.toHexString(Byte.toUnsignedInt(b)));
            }

            this.digest = bld.toString();
        }

        public String digest() {
            if (digest == null) {
                throw new IllegalStateException("digest computing writer not closed.");
            }

            return digest;
        }

        private void consumeBuffer(CharBuffer chars) {
            ________tmpView.append(chars);
            CoderResult res;
            do {
                res = enc.encode(chars, buffer, true);
                buffer.flip();
                digester.update(buffer);
                buffer.clear();
            } while (res == CoderResult.OVERFLOW);
        }
    }

    public static final class Tree {
        private final CanonicalPath path;
        private final String hash;
        private final Set<Tree> children;

        private Tree(CanonicalPath path, String hash, Set<Tree> children) {
            this.path = path;
            this.hash = hash;
            this.children = children;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Set<Tree> getChildren() {
            return children;
        }

        public String getHash() {
            return hash;
        }

        public CanonicalPath getPath() {
            return path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Tree tree = (Tree) o;

            return hash.equals(tree.hash);
        }

        @Override
        public int hashCode() {
            return hash.hashCode();
        }

        @Override
        public String toString() {
            return "Tree[" + "path=" + path +
                    ", hash='" + hash + '\'' +
                    ",children=" + children +
                    ']';
        }


        private abstract static class AbstractBuilder<This extends AbstractBuilder> {
            private CanonicalPath path;
            private String hash;
            private Set<Tree> children;

            public This withCanonicalPath(CanonicalPath path) {
                this.path = path;
                return castThis();
            }

            public This withHash(String hash) {
                this.hash = hash;
                return castThis();
            }

            protected void addChild(Tree childTree) {
                if (children == null) {
                    children = new HashSet<>();
                }
                children.add(childTree);
            }

            protected Tree build() {
                return new Tree(path, hash, Collections.unmodifiableSet(new HashSet<>(children)));
            }

            @SuppressWarnings("unchecked")
            private This castThis() {
                return (This) this;
            }
        }

        public static final class Builder extends AbstractBuilder<Builder> {

            private Builder() {

            }

            public ChildBuilder<Builder> startChild() {
                ChildBuilder<Builder> childBuilder = new ChildBuilder<>();
                childBuilder.parent = this;
                return childBuilder;
            }

            public Tree build() {
                return super.build();
            }
        }

        public static final class ChildBuilder<Parent extends AbstractBuilder<?>>
                extends AbstractBuilder<ChildBuilder<Parent>> {
            private Parent parent;

            private ChildBuilder() {

            }

            public ChildBuilder<ChildBuilder<Parent>> startChild() {
                ChildBuilder<ChildBuilder<Parent>> childBuilder = new ChildBuilder<>();
                childBuilder.parent = this;
                return childBuilder;
            }

            public Parent endChild() {
                Tree tree = build();
                parent.addChild(tree);
                return parent;
            }
        }
    }

    private interface HashableView {
        static HashableView of(MetadataPack.Members members) {
            return new HashableView() {
                @Override public List<ResourceType.Blueprint> getResourceTypes() {
                    return members.getResourceTypes();
                }

                @Override public List<MetricType.Blueprint> getMetricTypes() {
                    return members.getMetricTypes();
                }

                @Override public List<OperationType.Blueprint> getOperationTypes(ResourceType.Blueprint resourceType) {
                    List<OperationType.Blueprint> ret = new ArrayList<>(members.getOperationTypes(resourceType));
                    Collections.sort(ret, BLUEPRINT_COMPARATOR);

                    return ret;
                }

                @Override public DataEntity.Blueprint<?> getReturnType(RelativePath resourceType,
                                                                       OperationType.Blueprint operationType) {
                    return members.getReturnType(operationType);
                }

                @Override public DataEntity.Blueprint<?> getParameterTypes(RelativePath resourceType,
                                                                           OperationType.Blueprint operationType) {
                    return members.getParameterTypes(operationType);
                }

                @Override public DataEntity.Blueprint<?> getConfigurationSchema(ResourceType.Blueprint rt) {
                    return members.getConfigurationSchema(rt);
                }

                @Override public DataEntity.Blueprint<?> getConnectionConfigurationSchema(ResourceType.Blueprint rt) {
                    return members.getConnectionConfigurationSchema(rt);
                }
            };
        }

        static HashableView of(Entity<?, ?> entity, Inventory inventory) {
            Iterator<Entity<?, ?>> it = inventory.getTransitiveClosureOver(entity.getPath(),
                    Relationships.Direction.outgoing, asGenericClass(Entity.class),
                    Relationships.WellKnown.contains.name());

            class EntityAndChildren {
                Entity<?, ?> entity;
                final Map<Class<?>, Map<String, Entity<?, ?>>> children = new HashMap<>();

                EntityAndChildren() {
                }

                EntityAndChildren(Entity<?, ?> entity) {
                    this.entity = entity;
                }

                void addChild(Entity<?, ?> child) {
                    Map<String, Entity<?, ?>> byId = children.get(child.getClass());
                    if (byId == null) {
                        byId = new HashMap<>();
                        children.put(child.getClass(), byId);
                    }
                    byId.put(child.getId(), child);
                }
            }

            Map<RelativePath, EntityAndChildren> childrenByPath = new HashMap<>();
            childrenByPath.put(EMPTY_PATH, new EntityAndChildren(entity));
            StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(it, Spliterator.DISTINCT & Spliterator.IMMUTABLE
                            & Spliterator.NONNULL), false).forEach(e -> {
                RelativePath ep = e.getPath().relativeTo(entity.getPath());
                RelativePath pp = ep.up();

                EntityAndChildren ec = childrenByPath.get(ep);
                if (ec == null) {
                    ec = new EntityAndChildren(e);
                    childrenByPath.put(ep, ec);
                } else {
                    if (ec.entity != null) {
                        throw new IllegalStateException("A single entity occured twice in a transitive closure over " +
                                "contains. This is a bug. The offending entity path is: " + e.getPath());
                    }

                    ec.entity = e;
                }

                EntityAndChildren pc = childrenByPath.get(pp);
                if (pc == null) {
                    pc = new EntityAndChildren();
                    childrenByPath.put(pp, pc);
                }
                pc.addChild(e);
            });

            return new HashableView() {
                @Override public DataEntity.Blueprint<?> getReturnType(RelativePath rootResourceType,
                                                                       OperationType.Blueprint ot) {
                    DataEntity found = getSingle(DataEntity.class, rootResourceType.modified().extend(OperationType
                            .class, ot.getId()).extend(DataEntity.class, returnType.name()).get());

                    return found == null ? dummyDataBlueprint(returnType) : asBlueprint(found);
                }

                @Override public List<ResourceType.Blueprint> getResourceTypes() {
                    return diveToMany(ResourceType.class, EMPTY_PATH);
                }

                @Override
                public List<Resource.Blueprint> getResources(RelativePath rootPath, Resource.Blueprint parentResource) {
                    RelativePath parentPath = rootPath.modified().extend(Resource.class, parentResource.getId()).get();
                    return diveToMany(Resource.class, parentPath);
                }

                @Override
                public List<Metric.Blueprint> getResourceMetrics(RelativePath rootPath,
                                                                 Resource.Blueprint parentResource) {
                    RelativePath parentPath = rootPath.modified().extend(Resource.class, parentResource.getId()).get();
                    return diveToMany(Metric.class, parentPath);
                }

                @Override
                public DataEntity.Blueprint<?> getParameterTypes(RelativePath rootResourceType,
                                                                 OperationType.Blueprint ot) {
                    DataEntity found = getSingle(DataEntity.class, rootResourceType.modified().extend(OperationType
                            .class, ot.getId()).extend(DataEntity.class, parameterTypes.name()).get());

                    return found == null ? dummyDataBlueprint(parameterTypes) : asBlueprint(found);
                }

                @Override public List<OperationType.Blueprint> getOperationTypes(ResourceType.Blueprint rt) {
                    RelativePath p = relativeToRootEntity(rt);
                    return diveToMany(OperationType.class, p);
                }

                @Override public List<MetricType.Blueprint> getMetricTypes() {
                    return diveToMany(MetricType.class, EMPTY_PATH);
                }

                @Override public List<Metric.Blueprint> getFeedMetrics() {
                    return diveToMany(Metric.class, EMPTY_PATH);
                }

                @Override public DataEntity.Blueprint<?> getConnectionConfigurationSchema(ResourceType.Blueprint bl) {
                    RelativePath root = relativeToRootEntity(bl);
                    if (root == null) {
                        return dummyDataBlueprint(connectionConfigurationSchema);
                    }

                    DataEntity found = getSingle(DataEntity.class, root.modified()
                            .extend(DataEntity.class, connectionConfigurationSchema.name()).get());

                    return found == null ? dummyDataBlueprint(returnType) : asBlueprint(found);
                }

                @Override
                public DataEntity.Blueprint<?> getConnectionConfiguration(RelativePath root,
                                                                          Resource.Blueprint resource) {
                    RelativePath path = root.modified().extend(Resource.class, resource.getId())
                            .extend(DataEntity.class, connectionConfiguration.name()).get();

                    DataEntity found = getSingle(DataEntity.class, path);

                    return found == null ? dummyDataBlueprint(connectionConfiguration) : asBlueprint(found);
                }

                @Override public DataEntity.Blueprint<?> getConfigurationSchema(ResourceType.Blueprint bl) {
                    RelativePath root = relativeToRootEntity(bl);
                    if (root == null) {
                        return dummyDataBlueprint(configurationSchema);
                    }

                    DataEntity found = getSingle(DataEntity.class, root.modified()
                            .extend(DataEntity.class, configurationSchema.name()).get());

                    return found == null ? dummyDataBlueprint(returnType) : asBlueprint(found);
                }

                @Override
                public DataEntity.Blueprint<?> getConfiguration(RelativePath root,
                                                                Resource.Blueprint resource) {
                    RelativePath path = root.modified().extend(Resource.class, resource.getId())
                            .extend(DataEntity.class, configuration.name()).get();

                    DataEntity found = getSingle(DataEntity.class, path);

                    return found == null ? dummyDataBlueprint(connectionConfiguration) : asBlueprint(found);
                }

                private RelativePath relativeToRootEntity(Entity.Blueprint bl) {
                    return entity.accept(new ElementVisitor.Simple<RelativePath, Void>() {
                        ElementVisitor.Simple<RelativePath, Void> me = this;
                        @Override protected RelativePath defaultAction() {
                            return bl.getId().equals(entity.getId()) ? EMPTY_PATH : null;
                        }

                        @Override public RelativePath visitFeed(Feed feed, Void parameter) {
                            return bl.accept(new ElementBlueprintVisitor.Simple<RelativePath, Void>() {
                                @Override public RelativePath visitFeed(Feed.Blueprint feed, Void parameter) {
                                    return me.defaultAction();
                                }

                                @Override
                                public RelativePath visitResourceType(ResourceType.Blueprint type, Void parameter) {
                                    return RelativePath.to().resourceType(type.getId()).get();
                                }

                                @Override
                                public RelativePath visitMetricType(MetricType.Blueprint type, Void parameter) {
                                    return RelativePath.to().metricType(type.getId()).get();
                                }

                                @Override
                                public RelativePath visitResource(Resource.Blueprint resource, Void parameter) {
                                    return RelativePath.to().resource(resource.getId()).get();
                                }

                                @Override public RelativePath visitMetric(Metric.Blueprint metric, Void parameter) {
                                    return RelativePath.to().metric(metric.getId()).get();
                                }
                            }, null);
                        }

                        @Override public RelativePath visitResource(Resource resource, Void parameter) {
                            return bl.accept(new ElementBlueprintVisitor.Simple<RelativePath, Void>() {
                                @Override public RelativePath visitMetric(Metric.Blueprint metric, Void parameter) {
                                    return RelativePath.to().metric(metric.getId()).get();
                                }

                                @Override
                                public RelativePath visitResource(Resource.Blueprint resource, Void parameter) {
                                    return me.defaultAction();
                                }
                            }, null);
                        }

                        @Override public RelativePath visitResourceType(ResourceType type, Void parameter) {
                            return bl.accept(new ElementBlueprintVisitor.Simple<RelativePath, Void>() {
                                @Override
                                public RelativePath visitResourceType(ResourceType.Blueprint type, Void parameter) {
                                    return me.defaultAction();
                                }

                                @Override public RelativePath visitOperationType(OperationType.Blueprint operationType,
                                                                                 Void parameter) {
                                    return RelativePath.to().operationType(operationType.getId()).get();
                                }
                            }, null);
                        }
                    }, null);
                }

                private <E extends Entity<?, ?>> E getSingle(Class<E> type, RelativePath entityPath) {
                    EntityAndChildren ret = childrenByPath.get(entityPath);
                    return ret == null ? null : type.cast(ret.entity);
                }

                private <B extends Entity.Blueprint, E extends Entity<B, ?>>
                List<B> diveToMany(Class<E> type, RelativePath root) {
                    if (root == null) {
                        return Collections.emptyList();
                    }

                    EntityAndChildren ec = childrenByPath.get(root);
                    if (ec == null) {
                        return Collections.emptyList();
                    } else {
                        return ec.children.getOrDefault(type, emptyMap())
                                .values().stream()
                                .map(e -> asBlueprint(type.cast(e)))
                                .collect(toList());
                    }
                }
            };
        }

        default List<ResourceType.Blueprint> getResourceTypes() {
            return Collections.emptyList();
        }

        default List<MetricType.Blueprint> getMetricTypes() {
            return Collections.emptyList();
        }

        default List<OperationType.Blueprint> getOperationTypes(ResourceType.Blueprint rt) {
            return Collections.emptyList();
        }

        default DataEntity.Blueprint<?> getReturnType(RelativePath rootResourceType, OperationType.Blueprint ot) {
            return dummyDataBlueprint(OperationTypes.DataRole.returnType);
        }

        default DataEntity.Blueprint<?> getParameterTypes(RelativePath rootResourceType, OperationType.Blueprint ot) {
            return dummyDataBlueprint(OperationTypes.DataRole.parameterTypes);
        }

        default DataEntity.Blueprint<?> getConfigurationSchema(ResourceType.Blueprint rt) {
            return dummyDataBlueprint(ResourceTypes.DataRole.configurationSchema);
        }

        default DataEntity.Blueprint<?> getConnectionConfigurationSchema(ResourceType.Blueprint rt) {
            return dummyDataBlueprint(ResourceTypes.DataRole.connectionConfigurationSchema);
        }

        default List<Resource.Blueprint> getResources(RelativePath rootPath, Resource.Blueprint parentResource) {
            return Collections.emptyList();
        }

        default List<Metric.Blueprint> getFeedMetrics() {
            return Collections.emptyList();
        }

        default List<Metric.Blueprint> getResourceMetrics(RelativePath rootPath, Resource.Blueprint parentResource) {
            return Collections.emptyList();
        }

        default DataEntity.Blueprint<?> getConfiguration(RelativePath rootPath, Resource.Blueprint parentResource) {
            return dummyDataBlueprint(configuration);
        }

        default DataEntity.Blueprint<?> getConnectionConfiguration(RelativePath root, Resource.Blueprint
                parentResource) {
            return dummyDataBlueprint(Resources.DataRole.connectionConfiguration);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> asGenericClass(Class<?> cls) {
        return (Class<T>) (Class) cls;
    }
}
