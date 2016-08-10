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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.hawkular.inventory.api.TreeTraversal;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

/**
 * An abstract base class for the various hash trees. This is not instantiable/subclassable by the users on purpose
 * because only concrete subclasses defined in this package make sense for use. Nevertheless this class is public to
 * enable abstracting over the different subclasses.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
public abstract class AbstractHashTree<This extends AbstractHashTree<This, H>, H extends Serializable>
        implements Serializable {
    protected final RelativePath path;
    protected final H hash;
    protected final Map<Path.Segment, This> children;

    //jackson support
    AbstractHashTree() {
        this(null, null, null);
    }

    AbstractHashTree(RelativePath path, H hash, Map<Path.Segment, This> children) {
        this.path = path;
        this.hash = hash;
        this.children = children;
    }

    public Collection<This> getChildren() {
        return children.values();
    }

    public This getChild(Path.Segment path) {
        return children.get(path);
    }

    public H getHash() {
        return hash;
    }

    public RelativePath getPath() {
        return path;
    }

    public TreeTraversal<This> traversal() {
        return new TreeTraversal<>(t -> t.children.values().iterator());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractHashTree<?, ?> tree = (AbstractHashTree<?, ?>) o;

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

    interface TreeConstructor<T extends AbstractHashTree<T, H>, H extends Serializable> {
        T construct(RelativePath path, H hash, Map<Path.Segment, T> children);
    }

    /**
     * An interface that is implemented by all tree hash builders. This is only made public for the cases where the
     * users would like to abstract over different tree implementations.
     *
     * @param <This> the type of the concrete implementation
     * @param <Child> the type of the tree child builder
     * @param <Tree> the type of the tree being built
     * @param <Hash> the type of the hash used in the tree
     */
    public interface Builder<This extends Builder<This, Child, Tree, Hash>,
            Child extends ChildBuilder<Child, This, ?, Tree, Hash>,
            Tree extends AbstractHashTree<Tree, Hash>, Hash extends Serializable> {

        This withPath(RelativePath path);

        This withHash(Hash hash);

        Hash getHash();

        RelativePath getPath();

        boolean hasChildren();

        Child startChild();

        void addChild(Tree childTree);
    }

    /**
     * Interface for top-level hash tree builders. I.e. only these actually have the build method that is used to
     * produce the final tree.
     *
     * @param <This> the type of the concrete implementation
     * @param <Child> the type of the tree child builder
     * @param <Tree> the type of the tree being built
     * @param <Hash> the type of the hash used in the tree
     */
    public interface TopBuilder<This extends TopBuilder<This, Child, Tree, Hash>,
            Child extends ChildBuilder<Child, This, ?, Tree, Hash>,
            Tree extends AbstractHashTree<Tree, Hash>, Hash extends Serializable>
            extends Builder<This, Child, Tree, Hash> {

        Tree build();
    }

    /**
     * A builder used to build sub trees of some parent tree node.
     *
     * @param <This> the type of the concrete implementation of this interface
     * @param <Parent> the type of the builder of the parent tree node
     * @param <Child> the type of the builder used to build the subtrees of the tree node built by this builder
     * @param <Tree> the type of the tree being built
     * @param <Hash> the type of the hash used in the tree
     */
    public interface ChildBuilder<This extends ChildBuilder<This, Parent, Child, Tree, Hash>,
            Parent extends Builder<?, This, Tree, Hash>,
            Child extends ChildBuilder<Child, This, ?, Tree, Hash>,
            Tree extends AbstractHashTree<Tree, Hash>,
            Hash extends Serializable>

            extends Builder<This, Child, Tree, Hash> {


        Parent getParent();

        Parent endChild();
    }

    static class AbstractBuilder<This extends Builder<This, Child, T, H>,
            Child extends ChildBuilder<Child, This, ?, T, H>,
            T extends AbstractHashTree<T, H>, H extends Serializable>
            implements Builder<This, Child, T, H> {
        private final TreeConstructor<T, H> tctor;
        private final BiFunction<TreeConstructor<T, H>, This, Child> cctor;
        private RelativePath path;
        private H hash;
        private Map<Path.Segment, T> children;

        AbstractBuilder(TreeConstructor<T, H> tctor,
                        BiFunction<TreeConstructor<T, H>, This, Child> cctor) {
            this.tctor = tctor;
            this.cctor = cctor;
        }

        public This withPath(RelativePath path) {
            this.path = path;
            return castThis();
        }

        public This withHash(H hash) {
            this.hash = hash;
            return castThis();
        }

        public H getHash() {
            return hash;
        }

        public RelativePath getPath() {
            return path;
        }

        public boolean hasChildren() {
            return children != null && !children.isEmpty();
        }

        public void addChild(T childTree) {
            getChildren().put(childTree.getPath().getSegment(), childTree);
        }

        public Child startChild() {
            return cctor.apply(tctor, castThis());
        }

        protected T build() {
            if ((path == null || hash == null) && (children != null && !children.isEmpty())) {
                throw new IllegalStateException("Cannot construct a tree hash node without a path or " +
                        "hash and with children. While empty tree without a hash or path is OK, having children " +
                        "assumes the parent to be fully established.");
            }

            if (path != null) {
                int myDepth = path.getDepth();

                for (T child : getChildren().values()) {
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

            return tctor.construct(path, hash, Collections.unmodifiableMap(getChildren()));
        }

        private Map<Path.Segment, T> getChildren() {
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

    abstract static class AbstractChildBuilder<
            This extends AbstractChildBuilder<This, Parent, Child, T, H>,
            Parent extends Builder<?, This, T, H>,
            Child extends AbstractChildBuilder<Child, This, ?, T, H>,
            T extends AbstractHashTree<T, H>,
            H extends Serializable>

            extends AbstractBuilder<This, Child, T, H>
            implements ChildBuilder<This, Parent, Child, T, H> {
        private final Parent parent;

        AbstractChildBuilder(TreeConstructor<T, H> tctor, Parent parent,
                             BiFunction<TreeConstructor<T, H>, This, Child> cctor) {
            super(tctor, cctor);
            this.parent = parent;
        }

        public Parent getParent() {
            return parent;
        }

        @SuppressWarnings("unchecked")
        public Parent endChild() {
            T tree = build();
            parent.addChild(tree);
            return parent;
        }
    }
}
