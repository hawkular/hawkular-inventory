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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.api.model.ComputeHash.IntermediateHashContext;
import org.hawkular.inventory.api.model.ComputeHash.IntermediateHashResult;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

/**
 * A container for all type of hashes computed on an entity. Depending on what kind of hashes have been computed, some
 * properties of this class might be null. The caller is responsible to keep track of what's been computed and hence
 * what hashes are held in instances of this class.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
public final class Hashes implements Serializable {

    private final String identityHash;
    private final String contentHash;
    private final String syncHash;

    public static Hashes of(InventoryStructure<?> root, CanonicalPath rootPath) {
        return ComputeHash.of(root, rootPath, true, true, true);
    }

    public static Hashes of(Entity<?, ?> entity) {
        String contentHash = null;
        String identityHash = null;
        String syncHash = null;

        if (entity instanceof ContentHashable) {
            contentHash = ((ContentHashable) entity).getContentHash();
        }

        if (entity instanceof IdentityHashable) {
            identityHash = ((IdentityHashable) entity).getIdentityHash();
        }

        if (entity instanceof Syncable) {
            syncHash = ((Syncable) entity).getSyncHash();
        }

        return new Hashes(identityHash, contentHash, syncHash);
    }

    public static Tree treeOf(InventoryStructure<?> root, CanonicalPath rootPath,
                              Function<RelativePath, Hashes> hashLoader) {
        Tree.AbstractBuilder<?>[] tbld =
                new Tree.AbstractBuilder[1];

        Consumer<IntermediateHashContext> startChild = context -> {
            if (tbld[0] == null) {
                tbld[0] = Tree.builder();
            } else {
                tbld[0] = tbld[0].startChild();
            }
        };

        BiConsumer<IntermediateHashContext, IntermediateHashResult> endChild = (ctx, result) -> {
            if (tbld[0] instanceof Tree.ChildBuilder) {
                tbld[0].withHash(new Hashes(result)).withPath(result.path);
                @SuppressWarnings("unchecked")
                Tree.AbstractBuilder<?> parent =
                        ((Tree.ChildBuilder<?>) tbld[0]).getParent();
                parent.addChild(((Tree.ChildBuilder<?>) tbld[0]).build());
                tbld[0] = parent;
            }
        };

        IntermediateHashResult res = ComputeHash.treeOf(root, rootPath, true, true, true, startChild, endChild,
                hashLoader);

        tbld[0].withPath(res.path).withHash(new Hashes(res));

        return ((Tree.Builder)tbld[0]).build();
    }

    public static Tree treeOf(InventoryStructure<?> root, CanonicalPath rootPath) {
        return treeOf(root, rootPath, rp -> null);
    }

    public Hashes(String identityHash, String contentHash, String syncHash) {
        this.identityHash = identityHash;
        this.contentHash = contentHash;
        this.syncHash = syncHash;
    }

    Hashes(IntermediateHashResult res) {
        this(res.identityHash, res.contentHash, res.syncHash);
    }

    public String getIdentityHash() {
        return identityHash;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getSyncHash() {
        return syncHash;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Hashes hashes = (Hashes) o;

        if (identityHash != null ? !identityHash.equals(hashes.identityHash) : hashes.identityHash != null) {
            return false;
        } else if (contentHash != null ? !contentHash.equals(hashes.contentHash) : hashes.contentHash != null) {
            return false;
        } else {
            return syncHash != null ? syncHash.equals(hashes.syncHash) : hashes.syncHash == null;
        }
    }

    @Override public int hashCode() {
        int result = identityHash != null ? identityHash.hashCode() : 0;
        result = 31 * result + (contentHash != null ? contentHash.hashCode() : 0);
        result = 31 * result + (syncHash != null ? syncHash.hashCode() : 0);
        return result;
    }

    public static final class Tree extends AbstractHashTree<Tree, Hashes> {
        //jackson support
        private Tree() {
            this(null, null, null);
        }

        private Tree(RelativePath path, Hashes hash, Map<Path.Segment, Tree> children) {
            super(path, hash == null ? new Hashes(null, null, null) : hash, children);
        }

        public static Builder builder() {
            return new Builder();
        }

        public interface AbstractBuilder<This extends AbstractHashTree.Builder<This, ChildBuilder<This>, Tree, Hashes>
                & AbstractBuilder<This>>
                extends AbstractHashTree.Builder<This, ChildBuilder<This>, Tree, Hashes> {
        }

        public static final class Builder
                extends AbstractHashTree.AbstractBuilder<Builder, ChildBuilder<Builder>, Tree, Hashes>
                implements AbstractBuilder<Builder>, TopBuilder<Builder, ChildBuilder<Builder>, Tree, Hashes> {

            private Builder() {
                super(Tree::new, ChildBuilder<Builder>::new);
            }

            @Override
            public Tree build() {
                return super.build();
            }
        }

        public static final class ChildBuilder<
                Parent extends AbstractHashTree.Builder<Parent, ChildBuilder<Parent>, Tree, Hashes>
                        & AbstractBuilder<Parent>>
                extends AbstractChildBuilder<ChildBuilder<Parent>, Parent, ChildBuilder<ChildBuilder<Parent>>,
                Tree, Hashes>
                implements AbstractBuilder<ChildBuilder<Parent>>,
                AbstractHashTree.ChildBuilder<ChildBuilder<Parent>, Parent, ChildBuilder<ChildBuilder<Parent>>, Tree,
                                        Hashes> {

            ChildBuilder(TreeConstructor<Tree, Hashes> tctor, Parent p) {
                super(tctor, p, ChildBuilder<ChildBuilder<Parent>>::new);
            }
        }
    }
}
