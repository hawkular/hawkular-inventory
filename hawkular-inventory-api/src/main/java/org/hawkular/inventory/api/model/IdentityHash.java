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

import static java.util.stream.Collectors.toList;

import static org.hawkular.inventory.paths.DataRole.OperationType.parameterTypes;
import static org.hawkular.inventory.paths.DataRole.OperationType.returnType;
import static org.hawkular.inventory.paths.DataRole.Resource.configuration;
import static org.hawkular.inventory.paths.DataRole.Resource.connectionConfiguration;
import static org.hawkular.inventory.paths.DataRole.ResourceType.configurationSchema;
import static org.hawkular.inventory.paths.DataRole.ResourceType.connectionConfigurationSchema;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.TreeTraversal;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;

/**
 * Produces an identity hash of entities. Identity hash is a hash that uniquely identifies an entity
 * and is produced using its user-defined id and structure. This hash is used to match a client-side state of an
 * entity severside state of it.
 * <p>
 * The identity hash is defined only for the following types of entities:
 * {@link Feed}, {@link ResourceType}, {@link MetricType}, {@link OperationType}, {@link Metric}, {@link Resource} and
 * {@link DataEntity}.
 * <p>
 * The identity hash is an SHA1 hash of a string representation of the entity (in UTF-8 encoding). The string
 * representation is produced as follows:
 * <ol>
 * <li>DataEntity: role + minimizedDataJSON
 * <li>Metric: id
 * <li>Resource: hashOf(configuration) + hashOf(connectionConfiguration) + hashOf(childResource)*
 * + hashOf(childMetric)* + id
 * <li>MetricType: id + type + unit
 * <li>OperationType: hashOf(returnType) + hashOf(parameterTypes) + id
 * <li>ResourceType: hashOf(configurationSchema) + hashOf(connectionConfigurationSchema) + hashOf(childOperationType)*
 * + id
 * <li>Feed: hashOf(childResourceType)* + hashOf(childMetricType)* + hashOf(childResource)* + hashOf(childMetric)* + id
 * </ol>
 * where {@code hashOf()} means the identity hash of the child entity
 *
 * @author Lukas Krejci
 * @since 0.7.0
 */
public final class IdentityHash {

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
        HashConstructor ctor = new HashConstructor(new DigestComputingWriter(newDigest()));

        HashableView metadataView = HashableView.of(metadata);

        SortedSet<Entity.Blueprint> all = new TreeSet<>(BLUEPRINT_COMPARATOR);
        all.addAll(metadata.getMetricTypes());
        all.addAll(metadata.getResourceTypes());

        StringBuilder result = new StringBuilder();

        for (Entity.Blueprint bl : all) {
            IntermediateHashResult res = computeHash(bl, metadataView, ctor, (rp) -> null);
            result.append(res.hash);
        }

        DigestComputingWriter digestor = ctor.getDigestor();
        digestor.reset();
        digestor.append(result);
        digestor.close();
        return digestor.digest();
    }

    public static String of(InventoryStructure<?> inventory) {
        HashConstructor ctor = new HashConstructor(new DigestComputingWriter(newDigest()));

        computeHash(inventory.getRoot(), HashableView.of(inventory), ctor, (rp) -> null);

        return ctor.getDigestor().digest();
    }

    public static String of(Entity<? extends Entity.Blueprint, ?> entity, Inventory inventory) {
        return of(InventoryStructure.of(entity, inventory));
    }

    public static String of(MetadataPack mp, Inventory inventory) {
        List<Entity<? extends Entity.Blueprint, ?>> all = new ArrayList<>();
        all.addAll(inventory.inspect(mp).resourceTypes().getAll().entities());
        all.addAll(inventory.inspect(mp).metricTypes().getAll().entities());

        return of(all, inventory);
    }

    public static Tree treeOf(InventoryStructure<?> inventory) {
        Tree.AbstractBuilder<?>[] tbld = new Tree.AbstractBuilder[1];

        DigestComputingWriter wrt = new DigestComputingWriter(newDigest());

        HashConstructor ctor = new HashConstructor(wrt) {
            @Override public void startChild(IntermediateHashContext context) {
                super.startChild(context);
                if (tbld[0] == null) {
                    tbld[0] = Tree.builder();
                } else {
                    tbld[0] = tbld[0].startChild();
                }
            }

            @Override public void endChild(IntermediateHashContext ctx, IntermediateHashResult result) {
                super.endChild(ctx, result);
                if (tbld[0] instanceof Tree.ChildBuilder) {
                    tbld[0].withHash(result.hash).withPath(result.path);
                    Tree.AbstractBuilder<?> parent = ((Tree.ChildBuilder) tbld[0]).parent;
                    parent.addChild(tbld[0].build());
                    tbld[0] = parent;
                }
            }
        };

        IntermediateHashResult res = computeHash(inventory.getRoot(), HashableView.of(inventory), ctor,
                //we don't want the root element in the relative paths of the children so that they are easily
                //appendable to the root.
                (rp) -> rp.slide(1, 0));

        tbld[0].withPath(res.path).withHash(res.hash);

        return tbld[0].build();
    }

    public static String of(Iterable<? extends Entity<? extends Entity.Blueprint, ?>> entities, Inventory inventory) {
        return of(entities.iterator(), inventory);
    }

    public static String of(Iterator<? extends Entity<? extends Entity.Blueprint, ?>> entities, Inventory inventory) {
        SortedSet<Entity<? extends Entity.Blueprint, ?>> sortedEntities = new TreeSet<>(ENTITY_COMPARATOR);

        entities.forEachRemaining(sortedEntities::add);

        HashConstructor ctor = new HashConstructor(new DigestComputingWriter(newDigest()));

        StringBuilder resultHash = new StringBuilder();

        sortedEntities.forEach((e) -> {
            InventoryStructure<?> structure = InventoryStructure.of(e, inventory);
            HashableView v = HashableView.of(structure);
            IntermediateHashResult res = computeHash(structure.getRoot(), v, ctor, (rp) -> null);
            resultHash.append(res.hash);
        });

        ctor.getDigestor().reset();
        ctor.getDigestor().append(resultHash);
        ctor.getDigestor().close();

        return ctor.getDigestor().digest();
    }

    private static IntermediateHashResult computeHash(Blueprint entity, HashableView structure,
                                                      HashConstructor bld,
                                                      Function<RelativePath, RelativePath> pathCompleter) {

        return entity.accept(new ElementBlueprintVisitor.Simple<IntermediateHashResult, IntermediateHashContext>() {
            @Override
            public IntermediateHashResult visitData(DataEntity.Blueprint<?> data, IntermediateHashContext ctx) {
                return wrap(data, ctx, (childContext) -> {
                    try {
                        childContext.content.append(data.getId());
                        data.getValue().writeJSON(childContext.content);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not write out JSON for hash computation purposes.", e);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitMetricType(MetricType.Blueprint mt, IntermediateHashContext ctx) {
                return wrap(mt, ctx, (childContext) -> {
                    append(mt.getId(), childContext);
                    append(mt.getType().name(), childContext);
                    append(mt.getUnit().name(), childContext);
                });
            }

            @Override
            public IntermediateHashResult visitOperationType(OperationType.Blueprint operationType,
                                                             IntermediateHashContext ctx) {
                return wrap(operationType, ctx, (childContext) -> {
                    append(structure.getReturnType(ctx.root, operationType), childContext);
                    append(structure.getParameterTypes(ctx.root, operationType), childContext);
                    append(operationType.getId(), childContext);
                });
            }

            @Override
            public IntermediateHashResult visitResourceType(ResourceType.Blueprint type,
                                                            IntermediateHashContext ctx) {
                return wrap(type, ctx, (childContext) -> {
                    append(structure.getConfigurationSchema(type), childContext);
                    append(structure.getConnectionConfigurationSchema(type), childContext);
                    structure.getOperationTypes(type).forEach(b -> append(b, childContext));
                    append(type.getId(), childContext);
                });
            }

            @Override
            public IntermediateHashResult visitFeed(Feed.Blueprint feed, IntermediateHashContext ctx) {
                return wrap(feed, ctx, (childContext) -> {
                    structure.getResourceTypes().forEach(b -> append(b, childContext));
                    structure.getMetricTypes().forEach(b -> append(b, childContext));
                    structure.getFeedResources().forEach(b -> append(b, childContext));
                    structure.getFeedMetrics().forEach(b -> append(b, childContext));
                    append(feed.getId(), childContext);
                });
            }

            @Override
            public IntermediateHashResult visitMetric(Metric.Blueprint metric, IntermediateHashContext ctx) {
                return wrap(metric, ctx, (childContext) -> append(metric.getId(), childContext));
            }

            @Override
            public IntermediateHashResult visitResource(Resource.Blueprint resource,
                                                        IntermediateHashContext context) {
                return wrap(resource, context, (childContext) -> {
                    append(structure.getConfiguration(context.root, resource), childContext);
                    append(structure.getConnectionConfiguration(context.root, resource), childContext);
                    structure.getResources(context.root, resource).forEach(b -> append(b, childContext));
                    structure.getResourceMetrics(context.root, resource).forEach(b -> append(b, childContext));
                    append(resource.getId(), childContext);
                });
            }

            private void append(String data, IntermediateHashContext ctx) {
                ctx.content.append(data);
            }

            private void append(Entity.Blueprint child, IntermediateHashContext ctx) {
                ctx.content.append(child.accept(this, ctx).hash);
            }

            private IntermediateHashResult wrap(Entity.Blueprint root, IntermediateHashContext context,
                                                Consumer<IntermediateHashContext> hashComputation) {
                IntermediateHashContext childCtx = context.progress(root);
                bld.startChild(childCtx);
                hashComputation.accept(childCtx);

                DigestComputingWriter digestor = bld.getDigestor();
                digestor.reset();
                digestor.append(childCtx.content);
                digestor.close();

                IntermediateHashResult ret;
                if (pathCompleter == null) {
                    ret = new IntermediateHashResult(null, digestor.digest());
                } else {
                    ret = new IntermediateHashResult(pathCompleter.apply(childCtx.root), digestor.digest());
                }

                bld.endChild(childCtx, ret);

                return ret;
            }
        }, new IntermediateHashContext(RelativePath.empty().get()));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not instantiate SHA-1 digest algorithm.", e);
        }
    }

    private static <R extends DataRole> DataEntity.Blueprint<R> dummyDataBlueprint(R role) {
        return DataEntity.Blueprint.<R>builder().withRole(role).withValue(StructuredData.get().undefined()).build();
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

        static HashableView of(InventoryStructure<?> structure) {
            return new HashableView() {
                @Override
                public DataEntity.Blueprint<?> getConfiguration(RelativePath rootPath,
                                                                Resource.Blueprint parentResource) {
                    RelativePath resourcePath = rootPath.modified().extend(SegmentType.r, parentResource.getId())
                            .get().slide(1, 0);

                    return structure.getChildren(resourcePath, DataEntity.class)
                            .filter(d -> configuration.equals(d.getRole()))
                            .findFirst().orElse(dummyDataBlueprint(configuration));
                }

                @Override public DataEntity.Blueprint<?> getConfigurationSchema(ResourceType.Blueprint rt) {
                    RelativePath p = rt.equals(structure.getRoot()) ? RelativePath.empty().get()
                            : RelativePath.to().resourceType(rt.getId()).get();

                    return structure.getChildren(p, DataEntity.class)
                            .filter(d -> configurationSchema.equals(d.getRole()))
                            .findFirst().orElse(dummyDataBlueprint(configurationSchema));
                }

                @Override
                public DataEntity.Blueprint<?> getConnectionConfiguration(RelativePath root,
                                                                          Resource.Blueprint parentResource) {
                    RelativePath resourcePath = root.modified().extend(SegmentType.r, parentResource.getId())
                            .get().slide(1, 0);

                    return structure.getChildren(resourcePath, DataEntity.class)
                            .filter(d -> connectionConfiguration.equals(d.getRole()))
                            .findFirst().orElse(dummyDataBlueprint(connectionConfiguration));
                }

                @Override public DataEntity.Blueprint<?> getConnectionConfigurationSchema(ResourceType.Blueprint rt) {
                    RelativePath p = rt.equals(structure.getRoot()) ? RelativePath.empty().get()
                            : RelativePath.to().resourceType(rt.getId()).get();

                    return structure.getChildren(p, DataEntity.class)
                            .filter(d -> connectionConfigurationSchema.equals(d.getRole()))
                            .findFirst().orElse(dummyDataBlueprint(connectionConfigurationSchema));
                }

                @Override public List<Metric.Blueprint> getFeedMetrics() {
                    return structure.getChildren(RelativePath.empty().get(), Metric.class).collect(toList());
                }

                @Override public List<Resource.Blueprint> getFeedResources() {
                    return structure.getChildren(RelativePath.empty().get(), Resource.class).collect(toList());
                }

                @Override public List<MetricType.Blueprint> getMetricTypes() {
                    return structure.getChildren(RelativePath.empty().get(), MetricType.class).collect(toList());
                }

                @Override public List<OperationType.Blueprint> getOperationTypes(ResourceType.Blueprint rt) {
                    RelativePath p = rt.equals(structure.getRoot()) ? RelativePath.empty().get()
                            : RelativePath.to().resourceType(rt.getId()).get();

                    return structure.getChildren(p, OperationType.class).collect(toList());
                }

                @Override
                public DataEntity.Blueprint<?> getParameterTypes(RelativePath rootResourceType,
                                                                 OperationType.Blueprint ot) {
                    RelativePath p = rootResourceType.modified().extend(SegmentType.ot, ot.getId()).get()
                            .slide(1, 0);

                    return structure.getChildren(p, DataEntity.class)
                            .filter(d -> parameterTypes.equals(d.getRole()))
                            .findFirst().orElse(dummyDataBlueprint(parameterTypes));
                }

                @Override
                public List<Metric.Blueprint> getResourceMetrics(RelativePath rootPath,
                                                                 Resource.Blueprint parentResource) {
                    RelativePath p = rootPath.modified().extend(SegmentType.r, parentResource.getId()).get()
                            .slide(1, 0);

                    return structure.getChildren(p, Metric.class).collect(toList());
                }

                @Override
                public List<Resource.Blueprint> getResources(RelativePath rootPath, Resource.Blueprint parentResource) {
                    RelativePath p = rootPath.modified().extend(SegmentType.r, parentResource.getId()).get()
                            .slide(1, 0);

                    return structure.getChildren(p, Resource.class).collect(toList());
                }

                @Override public List<ResourceType.Blueprint> getResourceTypes() {
                    return structure.getChildren(RelativePath.empty().get(), ResourceType.class).collect(toList());
                }

                @Override public DataEntity.Blueprint<?> getReturnType(RelativePath rootResourceType,
                                                                       OperationType.Blueprint ot) {
                    RelativePath p = rootResourceType.modified().extend(SegmentType.ot, ot.getId()).get()
                            .slide(1, 0);

                    return structure.getChildren(p, DataEntity.class)
                            .filter(d -> returnType.equals(d.getRole()))
                            .findFirst().orElse(dummyDataBlueprint(returnType));
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
            return dummyDataBlueprint(returnType);
        }

        default DataEntity.Blueprint<?> getParameterTypes(RelativePath rootResourceType, OperationType.Blueprint ot) {
            return dummyDataBlueprint(parameterTypes);
        }

        default DataEntity.Blueprint<?> getConfigurationSchema(ResourceType.Blueprint rt) {
            return dummyDataBlueprint(configurationSchema);
        }

        default DataEntity.Blueprint<?> getConnectionConfigurationSchema(ResourceType.Blueprint rt) {
            return dummyDataBlueprint(connectionConfigurationSchema);
        }

        default List<Resource.Blueprint> getResources(RelativePath rootPath, Resource.Blueprint parentResource) {
            return Collections.emptyList();
        }

        default List<Resource.Blueprint> getFeedResources() {
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
            return dummyDataBlueprint(connectionConfiguration);
        }
    }

    private static class HashConstructor {
        private final DigestComputingWriter digestor;

        HashConstructor(DigestComputingWriter digestor) {
            this.digestor = digestor;
        }

        public DigestComputingWriter getDigestor() {
            return digestor;
        }

        public void startChild(IntermediateHashContext context) {
//            System.out.println("start: " + context.root);
        }

        public void endChild(IntermediateHashContext ctx, IntermediateHashResult result) {
//            System.out.println("Intermediate result: path: " + result.path + ", hash: " + result.hash + ", content: "
//                    + ctx.content);
        }
    }

    private static class IntermediateHashContext {
        final RelativePath root;
        final StringBuilder content = new StringBuilder();

        public IntermediateHashContext progress(Entity.Blueprint bl) {
            return new IntermediateHashContext(root.modified().extend(Blueprint.getSegmentTypeOf(bl),
                    bl.getId()).get());
        }

        public IntermediateHashContext(RelativePath root) {
            this.root = root;
        }
    }

    private static class IntermediateHashResult {
        private final RelativePath path;
        private final String hash;

        private IntermediateHashResult(RelativePath path, String hash) {
            this.path = path;
            this.hash = hash;
        }
    }

    private static class DigestComputingWriter implements Appendable, Closeable {
        private final MessageDigest digester;
        private final CharsetEncoder enc = Charset.forName("UTF-8").newEncoder().onMalformedInput(CodingErrorAction
                .REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);

        private final ByteBuffer buffer = ByteBuffer.allocate(512);
        private String digest;

        private DigestComputingWriter(MessageDigest digester) {
            this.digester = digester;
        }

        @Override
        public DigestComputingWriter append(CharSequence csq) {
            consumeBuffer(CharBuffer.wrap(csq));
            return this;
        }

        @Override
        public DigestComputingWriter append(CharSequence csq, int start, int end) {
            consumeBuffer(CharBuffer.wrap(csq, start, end));
            return this;
        }

        @Override
        public DigestComputingWriter append(char c) {
            consumeBuffer(CharBuffer.wrap(new char[]{c}));
            return this;
        }

        @Override
        public void close() {
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

        public void reset() {
            digester.reset();
            digest = null;
        }

        private void consumeBuffer(CharBuffer chars) {
            CoderResult res;
            do {
                res = enc.encode(chars, buffer, true);
                buffer.flip();
                digester.update(buffer);
                buffer.clear();
            } while (res == CoderResult.OVERFLOW);
        }
    }

    @ApiModel("IdentityHashTree")
    public static final class Tree implements Serializable {
        private final RelativePath path;
        private final String hash;
        private final Map<Path.Segment, Tree> children;

        private Tree(RelativePath path, String hash, Map<Path.Segment, Tree> children) {
            this.path = path;
            this.hash = hash;
            this.children = children;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Collection<Tree> getChildren() {
            return children.values();
        }

        public Tree getChild(Path.Segment path) {
            return children.get(path);
        }

        public String getHash() {
            return hash;
        }

        public RelativePath getPath() {
            return path;
        }

        public TreeTraversal<Tree> traversal() {
            return new TreeTraversal<>(t -> t.children.values().iterator());
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
                    ",children=" + children.values() +
                    ']';
        }

        public abstract static class AbstractBuilder<This extends AbstractBuilder<?>> {
            private RelativePath path;
            private String hash;
            private Map<Path.Segment, Tree> children;

            public This withPath(RelativePath path) {
                this.path = path;
                return castThis();
            }

            public This withHash(String hash) {
                this.hash = hash;
                return castThis();
            }

            public String getHash() {
                return hash;
            }

            public RelativePath getPath() {
                return path;
            }

            public boolean hasChildren() {
                return children != null && !children.isEmpty();
            }

            protected void addChild(Tree childTree) {
                getChildren().put(childTree.getPath().getSegment(), childTree);
            }

            public ChildBuilder<This> startChild() {
                ChildBuilder<This> childBuilder = new ChildBuilder<>();
                childBuilder.parent = castThis();
                return childBuilder;
            }

            protected Tree build() {
                if ((path == null || hash == null) && (children != null && !children.isEmpty())) {
                    throw new IllegalStateException("Cannot construct and IndentityHash.Tree node without a path or " +
                            "hash and with children. While empty tree without a hash or path is OK, having children" +
                            "assumes the parent to be fully established.");
                }

                if (path != null) {
                    int myDepth = path.getDepth();

                    for (Tree child : getChildren().values()) {
                        int childDepth = child.getPath().getDepth();
                        if (!path.isParentOf(child.getPath()) || childDepth != myDepth + 1) {
                            throw new IllegalStateException(
                                    "When building a tree node with path " + path + " an attempt " +
                                            "to add a child on path " + child.getPath() +
                                            " was made. The child's path must extend the parent's path by exactly" +
                                            " one segment, which is not true in this case.");
                        }
                    }
                }

                return new Tree(path, hash, Collections.unmodifiableMap(getChildren()));
            }

            private Map<Path.Segment, Tree> getChildren() {
                if (children == null) {
                    children = new HashMap<>();
                }

                return children;
            }

            @SuppressWarnings("unchecked")
            private This castThis() {
                return (This) this;
            }
        }

        public static final class Builder extends AbstractBuilder<Builder> {
            private Builder() {

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

            public Parent endChild() {
                Tree tree = build();
                parent.addChild(tree);
                return parent;
            }
        }
    }
}
