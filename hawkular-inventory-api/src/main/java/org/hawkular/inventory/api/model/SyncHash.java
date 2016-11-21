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

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

/**
 * Utility class for computing sync hashes or tree hashes of entities.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
public final class SyncHash {

    private SyncHash() {

    }

    /**
     * The root path is required so that paths in the blueprints can be correctly relativized.
     *
     * @param structure the structure to compute the root hash of.
     * @param rootPath the canonical path of the root of the inventory structure
     * @return the sync hash of the root of the structure
     */
    public static String of(InventoryStructure<?> structure, CanonicalPath rootPath) {
        return ComputeHash.of(structure, rootPath, true, true, true).getSyncHash();
    }

    /**
     * Computes the complete sync tree hash of the provided inventory structure. As with
     * {@link #of(InventoryStructure, CanonicalPath)}, the root path is required for relativizing the paths in the
     * blueprints in the structure.
     *
     * @param structure the inventory structure to compute the tree hash of
     * @param rootPath the canonical path of the root of the inventory structure
     * @param hashLoader a function that returns null if the hash of some structure node should be recomputed or returns
     *                   its hashes if no need to recompute it
     * @return the sync tree hash of the provided inventory structure
     */
    public static Tree treeOf(InventoryStructure<?> structure, CanonicalPath rootPath,
                              Function<RelativePath, Hashes> hashLoader) {
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
                tbld[0].withHash(result.syncHash).withPath(result.path);
                @SuppressWarnings("unchecked")
                Tree.AbstractBuilder<?> parent = ((Tree.ChildBuilder<?>) tbld[0]).getParent();
                parent.addChild(((Tree.ChildBuilder<?>) tbld[0]).build());
                tbld[0] = parent;
            }
        };

        ComputeHash.IntermediateHashResult res = ComputeHash
                .treeOf(structure, rootPath, true, true, true, startChild, endChild, hashLoader);

        tbld[0].withPath(res.path).withHash(res.syncHash);

        return ((Tree.Builder)tbld[0]).build();
    }

    public static Tree treeOf(InventoryStructure<?> structure, CanonicalPath rootPath) {
        return treeOf(structure, rootPath, p -> null);
    }

    public static final class Tree extends AbstractHashTree<Tree, String> {
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
