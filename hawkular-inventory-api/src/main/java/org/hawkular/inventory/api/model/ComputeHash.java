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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * Contains common utilities for computing the different kinds of hashes.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
final class ComputeHash {
    static final Comparator<Entity<?, ?>> ENTITY_COMPARATOR = (a, b) -> {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;

        if (!a.getClass().equals(b.getClass())) {
            return a.getClass().getName().compareTo(b.getClass().getName());
        } else {
            return a.getId().compareTo(b.getId());
        }
    };

    static final Comparator<Entity.Blueprint> BLUEPRINT_COMPARATOR = (a, b) -> {
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

    private ComputeHash() {

    }

    /**
     * @return a new message digest to use for hash computation.
     */
    static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not instantiate SHA-1 digest algorithm.", e);
        }
    }

    private static <R extends DataRole> DataEntity.Blueprint<R> dummyDataBlueprint(R role) {
        return DataEntity.Blueprint.<R>builder().withRole(role).withValue(StructuredData.get().undefined()).build();
    }

    static Hashes of(InventoryStructure<?> inventory, CanonicalPath rootPath, boolean computeIdentity,
                     boolean computeContent, boolean computeSync) {
        ComputeHash.HashConstructor ctor = new ComputeHash.HashConstructor(new ComputeHash.DigestComputingWriter(
                ComputeHash.newDigest()));

        IntermediateHashResult res =
                ComputeHash.computeHash(rootPath, inventory.getRoot(), ComputeHash.HashableView.of(inventory), ctor,
                        computeIdentity, computeContent, computeSync, rp -> null, rp -> null);

        return new Hashes(res.identityHash, res.contentHash, res.syncHash);
    }

    static IntermediateHashResult treeOf(InventoryStructure<?> inventory, CanonicalPath rootPath,
                                         boolean computeIdentity,
                                         boolean computeContent, boolean computeSync,
                                         Consumer<IntermediateHashContext> onStartChild,
                                         BiConsumer<IntermediateHashContext, IntermediateHashResult> onEndChild,
                                         Function<RelativePath, Hashes> hashLoader) {
        ComputeHash.DigestComputingWriter wrt = new ComputeHash.DigestComputingWriter(ComputeHash.newDigest());

        ComputeHash.HashConstructor ctor = new ComputeHash.HashConstructor(wrt) {
            @Override public void startChild(ComputeHash.IntermediateHashContext context) {
                super.startChild(context);
                onStartChild.accept(context);
            }

            @Override
            public void endChild(ComputeHash.IntermediateHashContext ctx, ComputeHash.IntermediateHashResult result) {
                super.endChild(ctx, result);
                onEndChild.accept(ctx, result);
            }
        };

        return computeHash(rootPath, inventory.getRoot(), ComputeHash.HashableView.of(inventory), ctor, computeIdentity,
                computeContent, computeSync,
                //we don't want the root element in the relative paths of the children so that they are easily
                //appendable to the root.
                (rp) -> rp.slide(1, 0),
                hashLoader
        );

    }

    static IntermediateHashResult computeHash(CanonicalPath entityPath, Blueprint entity, HashableView structure,
                                              HashConstructor bld, boolean compIdentity, boolean compContent,
                                              boolean compSync, Function<RelativePath, RelativePath> pathCompleter,
                                              Function<RelativePath, Hashes> hashLoader) {

        Class<?> entityType = Inventory.types().byBlueprint(entity.getClass()).getElementType();

        boolean syncable = Syncable.class.isAssignableFrom(entityType);
        boolean identityHashable = IdentityHashable.class.isAssignableFrom(entityType);
        boolean contentHashable = ContentHashable.class.isAssignableFrom(entityType);

        compIdentity = compIdentity || compSync;
        compContent = compContent || compSync;

        boolean computeIdentity = compIdentity && identityHashable;
        boolean computeContent = compContent && contentHashable;
        boolean computeSync = compSync && syncable;

        return entity.accept(new ElementBlueprintVisitor.Simple<IntermediateHashResult, IntermediateHashContext>() {
            @Override
            public IntermediateHashResult visitData(DataEntity.Blueprint<?> data, IntermediateHashContext ctx) {
                return wrap(data, ctx, (childContext) -> {
                    try {
                        StringBuilder json = new StringBuilder();
                        data.getValue().writeJSON(json);

                        if (computeIdentity) {
                            appendIdentity(data.getId(), childContext);
                            appendIdentity(json.toString(), childContext);
                        }

                        if (computeContent) {
                            appendContent(json.toString(), childContext);
                            appendCommonContent(data, childContext);
                        }

                        if (computeSync) {
                            appendCommonSync(data, childContext);
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not write out JSON for hash computation purposes.", e);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitMetricType(MetricType.Blueprint mt, IntermediateHashContext ctx) {
                return wrap(mt, ctx, (childContext) -> {
                    if (computeIdentity) {
                        appendIdentity(mt.getId(), childContext);
                        appendIdentity(mt.getMetricDataType().name(), childContext);
                        appendIdentity(mt.getUnit().name(), childContext);
                    }

                    if (computeContent) {
                        appendContent(mt.getMetricDataType().name(), childContext);
                        appendContent(mt.getUnit().name(), childContext);
                        appendContent(Objects.toString(mt.getCollectionInterval(), ""), childContext);
                        appendCommonContent(mt, childContext);
                    }

                    if (computeSync) {
                        appendCommonSync(mt, childContext);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitOperationType(OperationType.Blueprint operationType,
                                                             IntermediateHashContext ctx) {
                return wrap(operationType, ctx, (childContext) -> {
                    if (computeIdentity) {
                        appendEntityIdentity(structure.getReturnType(ctx.root, operationType), childContext);
                        appendEntityIdentity(structure.getParameterTypes(ctx.root, operationType), childContext);
                        appendIdentity(operationType.getId(), childContext);
                    }

                    if (computeContent) {
                        appendCommonContent(operationType, childContext);
                    }

                    if (computeSync) {
                        appendCommonSync(operationType, childContext);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitResourceType(ResourceType.Blueprint type,
                                                            IntermediateHashContext ctx) {
                return wrap(type, ctx, (childContext) -> {
                    if (computeIdentity) {
                        appendEntityIdentity(structure.getConfigurationSchema(type), childContext);
                        appendEntityIdentity(structure.getConnectionConfigurationSchema(type), childContext);
                        structure.getOperationTypes(type).forEach(b -> appendEntityIdentity(b, childContext));
                        appendIdentity(type.getId(), childContext);
                    }

                    if (computeContent) {
                        appendCommonContent(type, childContext);
                    }

                    if (computeSync) {
                        appendCommonSync(type, childContext);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitFeed(Feed.Blueprint feed, IntermediateHashContext ctx) {
                return wrap(feed, ctx, (childContext) -> {
                    if (computeIdentity) {
                        structure.getResourceTypes().forEach(b -> appendEntityIdentity(b, childContext));
                        structure.getMetricTypes().forEach(b -> appendEntityIdentity(b, childContext));
                        structure.getFeedResources().forEach(b -> appendEntityIdentity(b, childContext));
                        structure.getFeedMetrics().forEach(b -> appendEntityIdentity(b, childContext));
                        appendIdentity(feed.getId(), childContext);
                    }

                    if (computeContent) {
                        appendCommonContent(feed, childContext);
                    }

                    if (computeSync) {
                        appendCommonSync(feed, childContext);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitMetric(Metric.Blueprint metric, IntermediateHashContext ctx) {
                return wrap(metric, ctx, (childContext) -> {
                    if (computeIdentity) {
                        appendIdentity(metric.getId(), childContext);
                    }

                    if (computeContent) {
                        RelativePath mtPath = relativize(metric.getMetricTypePath(), SegmentType.mt, childContext);
                        appendContent(mtPath.toString(), childContext);
                        appendContent(Objects.toString(metric.getCollectionInterval(), ""), childContext);
                        appendCommonContent(metric, childContext);
                    }

                    if (computeSync) {
                        appendCommonSync(metric, childContext);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitResource(Resource.Blueprint resource,
                                                        IntermediateHashContext context) {
                return wrap(resource, context, (childContext) -> {
                    if (computeIdentity) {
                        appendEntityIdentity(structure.getConfiguration(context.root, resource), childContext);
                        appendEntityIdentity(structure.getConnectionConfiguration(context.root, resource),
                                childContext);
                        structure.getResources(context.root, resource)
                                .forEach(b -> appendEntityIdentity(b, childContext));
                        structure.getResourceMetrics(context.root, resource)
                                .forEach(b -> appendEntityIdentity(b, childContext));
                        appendIdentity(resource.getId(), childContext);
                    }

                    if (computeContent) {
                        RelativePath rtPath = relativize(resource.getResourceTypePath(), SegmentType.rt, childContext);
                        appendContent(rtPath.toString(), childContext);
                        appendCommonContent(resource, childContext);
                    }

                    if (computeSync) {
                        appendCommonSync(resource, childContext);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitTenant(Tenant.Blueprint tenant, IntermediateHashContext context) {
                return wrap(tenant, context, childContext -> {
                    if (computeContent) {
                        appendCommonContent(tenant, childContext);
                    }
                });
            }

            @Override
            public IntermediateHashResult visitEnvironment(Environment.Blueprint environment,
                                                           IntermediateHashContext context) {
                return wrap(environment, context, childContext -> {
                    if (computeContent) {
                        appendCommonContent(environment, childContext);
                    }
                });
            }

            private RelativePath relativize(String relativePath, SegmentType targetType, IntermediateHashContext ctx) {
                Path targetPath = Path.fromPartiallyUntypedString(relativePath,
                        CanonicalPath.of().tenant(entityPath.ids().getTenantId()).get(), entityPath,
                        targetType);

                if (targetPath.isCanonical()) {
                    targetPath = targetPath.toCanonicalPath().relativeTo(ctx.origin);
                }

                return targetPath.toRelativePath();
            }

            private IntermediateHashResult wrap(Entity.Blueprint root, IntermediateHashContext context,
                                                Consumer<IntermediateHashContext> hashComputation) {
                IntermediateHashContext childCtx = context.progress(root);
                bld.startChild(childCtx);

                Hashes loadedHashes = hashLoader.apply(childCtx.root.slide(1, 0));

                boolean compute = loadedHashes == null
                        || (computeIdentity && loadedHashes.getIdentityHash() == null)
                        || (computeContent && loadedHashes.getContentHash() == null)
                        || (computeSync && loadedHashes.getSyncHash() == null);

                if (compute) {
                    hashComputation.accept(childCtx);
                }

                String identityHash = loadedHashes == null ? null : loadedHashes.getIdentityHash();
                String contentHash =  loadedHashes == null ? null : loadedHashes.getContentHash();
                String syncHash =  loadedHashes == null ? null : loadedHashes.getSyncHash();

                DigestComputingWriter digestor = bld.getDigestor();
                if (computeIdentity && identityHash == null) {
                    digestor.reset();
                    digestor.append(childCtx.identity);
                    digestor.close();
                    identityHash = digestor.digest();
                }

                if (computeContent && contentHash == null) {
                    digestor.reset();
                    digestor.append(childCtx.content);
                    digestor.close();
                    contentHash = digestor.digest();
                }

                if (computeSync && syncHash == null) {
                    digestor.reset();
                    digestor.append(identityHash);
                    digestor.append(contentHash);
                    digestor.append(childCtx.sync);
                    digestor.close();
                    syncHash = digestor.digest();
                }

                IntermediateHashResult ret;
                if (pathCompleter == null) {
                    ret = new IntermediateHashResult(null, identityHash, contentHash, syncHash);
                } else {
                    ret = new IntermediateHashResult(pathCompleter.apply(childCtx.root), identityHash, contentHash,
                            syncHash);
                }

                bld.endChild(childCtx, ret);

                return ret;
            }

            private void appendEntityIdentity(Entity.Blueprint child, IntermediateHashContext ctx) {
                ctx.identity.append(child.accept(this, ctx).identityHash);
            }
        }, new IntermediateHashContext(entityPath == null ? null : entityPath.up(),
                RelativePath.empty().get()));
    }

    static void appendIdentity(String data, IntermediateHashContext ctx) {
        if (data != null) {
            ctx.identity.append(data);
        }
    }

    static void appendContent(String data, IntermediateHashContext ctx) {
        if (data != null) {
            ctx.content.append(data);
        }
    }

    static void appendContent(Map<String, Object> props, IntermediateHashContext ctx) {
        if (props == null) {
            return;
        }

        SortedMap<String, Object> sorted = new TreeMap<>(Comparator.naturalOrder());
        sorted.putAll(props);

        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            ctx.content.append(e.getKey()).append(e.getValue());
        }
    }

    static void appendCommonContent(Entity.Blueprint b, IntermediateHashContext ctx) {
        appendContent(b.getName(), ctx);
        appendContent(b.getProperties(), ctx);
    }

    static void appendSync(String data, IntermediateHashContext ctx) {
        if (data != null) {
            ctx.sync.append(data);
        }
    }

    static void appendCommonSync(Entity.Blueprint bl, IntermediateHashContext ctx) {
//The code below would cause relationships to influence the sync hash... After some consideration, this is not
//advisable because it would imply that sync will also synchronize the relationships going in/out of entities. This
//should not be done, though, because it would erase all the possible custom relationships that were defined out of
//control and unbeknownst to the sync-caller (i.e. the agent).
//        Comparator<Map.Entry<String, List<String>>> sortRelationships = (a, b) -> {
//            int names = a.getKey().compareTo(b.getKey());
//            if (names != 0) {
//                return names;
//            }
//
//            List<String> aPaths = a.getValue();
//            List<String> bPaths = b.getValue();
//
//            int lenDiff = aPaths.size() - bPaths.size();
//            if (lenDiff != 0) {
//                return lenDiff;
//            }
//
//            for (int i = 0; i < aPaths.size(); ++i) {
//                String ap = aPaths.get(i);
//                String bp = bPaths.get(i);
//
//                int comp = ap.compareTo(bp);
//
//                if (comp != 0) {
//                    return comp;
//                }
//            }
//
//            return 0;
//        };
//
//        Function<Map.Entry<String, Set<CanonicalPath>>, Map.Entry<String, List<String>>> sortTargets =
//                e -> new AbstractMap.SimpleEntry<>(e.getKey(),
//                        e.getValue().stream().map(Object::toString).sorted().collect(toList()));
//
//        Consumer<Map.Entry<String, List<String>>> append = e -> {
//            appendSync(e.getKey(), ctx);
//            e.getValue().forEach(p -> appendSync(p, ctx));
//        };
//
//        bl.getIncomingRelationships().entrySet().stream()
//                .map(sortTargets)
//                .sorted(sortRelationships)
//                .forEach(append);
//
//        bl.getOutgoingRelationships().entrySet().stream()
//                .map(sortTargets)
//                .sorted(sortRelationships)
//                .forEach(append);
    }

    interface HashableView {
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
                private DataEntity.Blueprint<?> getData(RelativePath.Extender parentPath, DataRole dataRole) {
                    Blueprint b =
                            structure.get(parentPath.extend(SegmentType.d, dataRole.name()).get());

                    return b == null ? dummyDataBlueprint(dataRole) : (DataEntity.Blueprint<?>) b;
                }

                @Override
                public DataEntity.Blueprint<?> getConfiguration(RelativePath rootPath,
                                                                Resource.Blueprint parentResource) {
                    RelativePath.Extender resourcePath = rootPath.modified()
                            .extend(SegmentType.r, parentResource.getId()).get().slide(1, 0).modified();

                    return getData(resourcePath, configuration);
                }

                @Override public DataEntity.Blueprint<?> getConfigurationSchema(ResourceType.Blueprint rt) {
                    RelativePath.Extender p = rt.equals(structure.getRoot()) ? RelativePath.empty()
                            : RelativePath.empty().extend(SegmentType.rt, rt.getId());

                    return getData(p, configurationSchema);
                }

                @Override
                public DataEntity.Blueprint<?> getConnectionConfiguration(RelativePath root,
                                                                          Resource.Blueprint parentResource) {
                    RelativePath.Extender resourcePath = root.modified().extend(SegmentType.r, parentResource.getId())
                            .get().slide(1, 0).modified();

                    return getData(resourcePath, connectionConfiguration);
                }

                @Override public DataEntity.Blueprint<?> getConnectionConfigurationSchema(ResourceType.Blueprint rt) {
                    RelativePath.Extender p = rt.equals(structure.getRoot()) ? RelativePath.empty()
                            : RelativePath.empty().extend(SegmentType.rt, rt.getId());

                    return getData(p, connectionConfigurationSchema);
                }

                @Override public List<Metric.Blueprint> getFeedMetrics() {
                    try (Stream<Metric.Blueprint> s = structure.getChildren(RelativePath.empty().get(), Metric.class)) {
                        return s.collect(toList());
                    }
                }

                @Override public List<Resource.Blueprint> getFeedResources() {
                    try (Stream<Resource.Blueprint> s = structure.getChildren(RelativePath.empty().get(),
                            Resource.class)) {
                        return s.collect(toList());
                    }
                }

                @Override public List<MetricType.Blueprint> getMetricTypes() {
                    try (Stream<MetricType.Blueprint> s = structure.getChildren(RelativePath.empty().get(),
                            MetricType.class)) {
                        return s.collect(toList());
                    }
                }

                @Override public List<OperationType.Blueprint> getOperationTypes(ResourceType.Blueprint rt) {
                    RelativePath p = rt.equals(structure.getRoot()) ? RelativePath.empty().get()
                            : RelativePath.to().resourceType(rt.getId()).get();

                    try (Stream<OperationType.Blueprint> s = structure.getChildren(p, OperationType.class)) {
                        return s.collect(toList());
                    }
                }

                @Override
                public DataEntity.Blueprint<?> getParameterTypes(RelativePath rootResourceType,
                                                                 OperationType.Blueprint ot) {
                    RelativePath.Extender p = rootResourceType.modified().extend(SegmentType.ot, ot.getId()).get()
                            .slide(1, 0).modified();

                    return getData(p, parameterTypes);
                }

                @Override
                public List<Metric.Blueprint> getResourceMetrics(RelativePath rootPath,
                                                                 Resource.Blueprint parentResource) {
                    RelativePath p = rootPath.modified().extend(SegmentType.r, parentResource.getId()).get()
                            .slide(1, 0);

                    try (Stream<Metric.Blueprint> s = structure.getChildren(p, Metric.class)) {
                        return s.collect(toList());
                    }
                }

                @Override
                public List<Resource.Blueprint> getResources(RelativePath rootPath, Resource.Blueprint parentResource) {
                    RelativePath p = rootPath.modified().extend(SegmentType.r, parentResource.getId()).get()
                            .slide(1, 0);

                    try (Stream<Resource.Blueprint> s = structure.getChildren(p, Resource.class)) {
                        return s.collect(toList());
                    }
                }

                @Override public List<ResourceType.Blueprint> getResourceTypes() {
                    try (Stream<ResourceType.Blueprint> s = structure.getChildren(RelativePath.empty().get(),
                            ResourceType.class)) {
                        return s.collect(toList());
                    }
                }

                @Override public DataEntity.Blueprint<?> getReturnType(RelativePath rootResourceType,
                                                                       OperationType.Blueprint ot) {
                    RelativePath.Extender p = rootResourceType.modified().extend(SegmentType.ot, ot.getId()).get()
                            .slide(1, 0).modified();

                    return getData(p, returnType);
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

    public static final class Tree {
        private Tree() {

        }

    }

    static class DigestComputingWriter implements Appendable, Closeable {
        private final MessageDigest digester;
        private final CharsetEncoder enc = Charset.forName("UTF-8").newEncoder().onMalformedInput(CodingErrorAction
                .REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);

        private final ByteBuffer buffer = ByteBuffer.allocate(512);
        private String digest;

        DigestComputingWriter(MessageDigest digester) {
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
            this.digest = runningDigest();
        }

        /**
         * Computes the digest of the data appended so far. Notice that this is recomputed every time this method
         * is called.
         *
         * @return the freshly computed digest of the data obtained so far
         */
        String runningDigest() {
            byte[] digest = digester.digest();

            StringBuilder bld = new StringBuilder();
            for (byte b : digest) {
                bld.append(Integer.toHexString(Byte.toUnsignedInt(b)));
            }

            return bld.toString();
        }

        /**
         * Obtains the final digest from the entirety of the data. Contrast this with {@link #runningDigest()}.
         * <p>This method does not do any computation and is therefore very quick.
         * <p>Note that this writer MUST BE {@link #close() CLOSED} for this method to return successfully.
         *
         * @return the computed digest of the data
         */
        String digest() {
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

    static class IntermediateHashContext {
        final CanonicalPath origin;
        final RelativePath root;
        final StringBuilder identity = new StringBuilder();
        final StringBuilder content = new StringBuilder();
        final StringBuilder sync = new StringBuilder();

        IntermediateHashContext(CanonicalPath origin, RelativePath root) {
            this.origin = origin;
            this.root = root;
        }

        IntermediateHashContext progress(Entity.Blueprint bl) {
            SegmentType st = Blueprint.getSegmentTypeOf(bl);
            String id = bl.getId();

            return new IntermediateHashContext(origin == null ? null : origin.modified().extend(st, id).get(),
                    root.modified().extend(st, id).get());
        }
    }

    static class IntermediateHashResult {
        final RelativePath path;
        final String identityHash;
        final String contentHash;
        final String syncHash;

        private IntermediateHashResult(RelativePath path, String identityHash, String contentHash, String syncHash) {
            this.path = path;
            this.identityHash = identityHash;
            this.contentHash = contentHash;
            this.syncHash = syncHash;
        }
    }

    static class HashConstructor {
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
}

