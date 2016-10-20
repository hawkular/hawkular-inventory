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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

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

    private IdentityHash() {

    }

    public static String of(MetadataPack.Members metadata) {
        ComputeHash.HashConstructor ctor = new ComputeHash.HashConstructor(new ComputeHash.DigestComputingWriter(
                ComputeHash.newDigest()));

        ComputeHash.HashableView metadataView = ComputeHash.HashableView.of(metadata);

        SortedSet<Entity.Blueprint> all = new TreeSet<>(ComputeHash.BLUEPRINT_COMPARATOR);
        all.addAll(metadata.getMetricTypes());
        all.addAll(metadata.getResourceTypes());

        StringBuilder result = new StringBuilder();

        for (Entity.Blueprint bl : all) {
            //identity hash not dependent on relative paths (if any) in the data, so null for entityPath is ok.
            ComputeHash.IntermediateHashResult res = ComputeHash.computeHash(null, bl, metadataView, ctor, true, false,
                    false, rp -> null, rp -> null);
            result.append(res.identityHash);
        }

        ComputeHash.DigestComputingWriter digestor = ctor.getDigestor();
        digestor.reset();
        digestor.append(result);
        digestor.close();
        return digestor.digest();
    }

    public static String of(InventoryStructure<?> inventory) {
        //identity hash is not dependent on the root path - I.e. no relative paths in the inventory structure are used
        //to compute the identity hash...
        return ComputeHash.of(inventory, null, true, false, false).getIdentityHash();
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
        @SuppressWarnings("unchecked")
        Tree.AbstractBuilder<?>[] tbld =
                new Tree.AbstractBuilder[1];

        Consumer<ComputeHash.IntermediateHashContext> startChild = context -> {
            if (tbld[0] == null) {
                tbld[0] = Tree.builder();
            } else {
                tbld[0] = tbld[0].startChild();
            }
        };

        BiConsumer<ComputeHash.IntermediateHashContext, ComputeHash.IntermediateHashResult> endChild = (ctx, result) -> {
            if (tbld[0] instanceof Tree.ChildBuilder) {
                tbld[0].withHash(result.identityHash).withPath(result.path);
                @SuppressWarnings("unchecked")
                Tree.AbstractBuilder<?> parent =
                        ((Tree.ChildBuilder<?>) tbld[0]).getParent();
                parent.addChild(((Tree.ChildBuilder<?>) tbld[0]).build());
                tbld[0] = parent;
            }
        };

        //identity hash is not computed using any relative paths, so we can pass null to the root path of the
        //computation.
        ComputeHash.IntermediateHashResult res = ComputeHash.treeOf(inventory, null, true, false, false, startChild,
                endChild, p -> null);

        tbld[0].withPath(res.path).withHash(res.identityHash);

        return ((Tree.Builder)tbld[0]).build();
    }

    public static String of(Iterable<? extends Entity<? extends Entity.Blueprint, ?>> entities, Inventory inventory) {
        return of(entities.iterator(), inventory);
    }

    public static String of(Iterator<? extends Entity<? extends Entity.Blueprint, ?>> entities, Inventory inventory) {
        SortedSet<Entity<? extends Entity.Blueprint, ?>> sortedEntities = new TreeSet<>(ComputeHash.ENTITY_COMPARATOR);

        entities.forEachRemaining(sortedEntities::add);

        ComputeHash.HashConstructor ctor = new ComputeHash.HashConstructor(new ComputeHash.DigestComputingWriter(
                ComputeHash.newDigest()));

        StringBuilder resultHash = new StringBuilder();

        sortedEntities.forEach((e) -> {
            InventoryStructure<?> structure = InventoryStructure.of(e, inventory);
            ComputeHash.HashableView v = ComputeHash.HashableView.of(structure);
            ComputeHash.IntermediateHashResult res = ComputeHash
                    .computeHash(e.getPath(), structure.getRoot(), v, ctor, true, false,
                            false, rp -> null, rp -> null);
            resultHash.append(res.identityHash);
        });

        ctor.getDigestor().reset();
        ctor.getDigestor().append(resultHash);
        ctor.getDigestor().close();

        return ctor.getDigestor().digest();
    }

    @ApiModel("IdentityHashTree")
    public static final class Tree extends AbstractHashTree<Tree, String> implements Serializable {
        //jackson support
        private Tree() {
            this(null, null, null);
        }

        private Tree(RelativePath path, String hash, Map<Path.Segment, Tree> children) {
            super(path, hash, children);
        }

        public static Builder builder() {
            return new Builder();
        }

        public interface AbstractBuilder<This extends AbstractHashTree.Builder<This, ChildBuilder<This>, Tree, String>
                & AbstractBuilder<This>>
                extends AbstractHashTree.Builder<This, ChildBuilder<This>, Tree, String> {
        }

        public static final class Builder
                extends AbstractHashTree.AbstractBuilder<Builder, ChildBuilder<Builder>, Tree, String>
            implements AbstractBuilder<Builder>, TopBuilder<Builder, ChildBuilder<Builder>, Tree, String> {

            private Builder() {
                super(Tree::new, ChildBuilder<Builder>::new);
            }

            @Override
            public Tree build() {
                return super.build();
            }
        }

        public static final class ChildBuilder<
                Parent extends AbstractHashTree.Builder<Parent, ChildBuilder<Parent>, Tree, String>
                        & AbstractBuilder<Parent>>
                extends AbstractChildBuilder<ChildBuilder<Parent>, Parent, ChildBuilder<ChildBuilder<Parent>>,
                Tree, String>
                implements AbstractBuilder<ChildBuilder<Parent>>,
                AbstractHashTree.ChildBuilder<ChildBuilder<Parent>, Parent, ChildBuilder<ChildBuilder<Parent>>, Tree,
                                        String> {

            ChildBuilder(TreeConstructor<Tree, String> tctor, Parent p) {
                super(tctor, p, ChildBuilder<ChildBuilder<Parent>>::new);
            }
        }
    }
}
