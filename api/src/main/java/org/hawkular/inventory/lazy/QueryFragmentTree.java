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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.filters.Filter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a tree of filters.
 * The filters that are used to represent an inventory query (so that new query instances can be generated at will)
 * form a tree because of the fact that environments can both contain resources or metrics directly or they can contain
 * feeds that in turn contain the metrics and resources.
 *
 * <p>The API contains a method to traverse both of these kinds of "placement" of metrics or resources
 * simultaneously. This is nice from a user perspective but creates problems in the impl, because using the
 * relationships of the resources or metrics, one can construct queries, the would be able to branch multiple times
 * and hence form a tree.
 *
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class QueryFragmentTree {
    private QueryFragment[] fragments;
    private List<QueryFragmentTree> subTrees = new ArrayList<>();

    /**
     * Converts the list of applicators to the list of filters.
     *
     * @param fragments the list of applicators
     * @return the list of corresponding filters
     */
    private static Filter[] filters(QueryFragment... fragments) {
        Filter[] ret = new Filter[fragments.length];

        for (int i = 0; i < fragments.length; ++i) {
            ret[i] = fragments[i].getFilter();
        }

        return ret;
    }

    /**
     * Converts the provided filter tree to a list of paths (array of filters). Each of the paths in the result
     * represent 1 branch from the tree from root to some leaf.
     *
     * @param query the tree of filters
     * @return the list of paths
     */
    public static Filter[][] filters(QueryFragmentTree query) {
        List<List<Filter>> paths = new ArrayList<>();

        Deque<Filter[]> path = new ArrayDeque<>();
        Deque<Iterator<QueryFragmentTree>> traversalPosition = new ArrayDeque<>();

        path.add(filters(query.fragments));
        traversalPosition.push(query.subTrees.iterator());

        while (!traversalPosition.isEmpty()) {
            Iterator<QueryFragmentTree> currentPos = traversalPosition.peek();
            if (currentPos.hasNext()) {
                QueryFragmentTree child = currentPos.next();
                if (child.subTrees.isEmpty()) {
                    //we have a leaf here
                    List<Filter> pathToLeaf = new ArrayList<>();
                    for (Filter[] fs : path) {
                        Collections.addAll(pathToLeaf, fs);
                    }
                    Collections.addAll(pathToLeaf, filters(child.fragments));
                    paths.add(pathToLeaf);
                } else {
                    path.add(filters(child.fragments));
                    traversalPosition.push(child.subTrees.iterator());
                }
            } else {
                traversalPosition.pop();
                path.removeLast();
            }
        }

        Filter[][] ret = new Filter[paths.size()][];
        Arrays.setAll(ret, (i) -> paths.get(i).toArray(new Filter[paths.get(i).size()]));
        return ret;
    }

    public static QueryFragmentTree empty() {
        return new Builder().build();
    }

    private QueryFragmentTree() {
    }

    public QueryFragment[] getFragments() {
        return fragments;
    }

    public List<QueryFragmentTree> getSubTrees() {
        return subTrees;
    }

    public Builder asBuilder() {
        Builder b = new Builder();
        b.fragments = new ArrayList<>(Arrays.asList(fragments));
        for (QueryFragmentTree subTree : subTrees) {
            Builder childBuilder = subTree.asBuilder();
            childBuilder.parent = b;
            b.children.add(childBuilder);
        }

        return b;
    }

    public SymmetricExtender extend() {
        return new SymmetricExtender(asBuilder());
    }

    public static final class Builder {
        private List<QueryFragment> fragments = new ArrayList<>();
        private QueryFragmentTree tree = new QueryFragmentTree();
        private Builder parent;
        private List<Builder> children = new ArrayList<>();
        private boolean done;

        /**
         * Creates a new branch in the tree and returns a builder of that branch.
         */
        public Builder branch() {
            Builder child = new Builder();
            child.parent = this;
            children.add(child);

            return child;
        }

        /**
         * Concludes the work on a branch and returns a builder of the parent "node", if any.
         */
        public Builder done() {
            if (done) {
                return parent;
            }

            this.tree.fragments = fragments.toArray(new QueryFragment[fragments.size()]);
            if (parent != null) {
                parent.tree.subTrees.add(this.tree);
                parent.children.remove(this);
            }

            //done will remove the child from the children, so we'd get concurrent modification exceptions
            //avoid that stupidly by working on a copy of children
            new ArrayList<>(children).forEach(QueryFragmentTree.Builder::done);

            done = true;

            return parent;
        }

        /**
         * Sets the filters to be used on the current node in the tree.
         *
         * @param filters the list of filters to apply to the query at this position in the tree.
         */
        public Builder with(QueryFragment... filters) {
            Collections.addAll(this.fragments, filters);
            return this;
        }

        public Builder with(QueryFragmentTree other) {
            with(other.fragments);
            for (QueryFragmentTree sub : other.getSubTrees()) {
                branch();
                with(sub);
                done();
            }

            return this;
        }

        /**
         * Builds the <b>whole</b> tree regardless of where in the tree the current builder "operates".
         *
         * @return the fully built tree
         */
        public QueryFragmentTree build() {
            QueryFragmentTree.Builder root = this;
            while (true) {
                if (root.parent == null) {
                    break;
                }
                root = root.parent;
            }

            root.done();
            return root.tree;
        }
    }

    /**
     * Constructs a query fragment tree by extending all the leaves with a uniform set of filters at a time.
     */
    public static final class SymmetricExtender {
        private QueryFragmentTree.Builder filters;

        private SymmetricExtender(QueryFragmentTree.Builder filters) {
            this.filters = filters;
        }

        public SymmetricExtender with(QueryFragmentTree other) {
            onLeaves(this.filters, (builder) -> {
                builder.with(other);
            });
            return this;
        }

        public SymmetricExtender with(Filter[][] filters, Function<Filter[], QueryFragment[]> queryFragmentSupplier) {
            onLeaves(this.filters, (builder) -> {
                for (Filter[] fs : filters) {
                    builder.branch().with(queryFragmentSupplier.apply(fs));
                }
            });
            return this;
        }

        public SymmetricExtender with(Filter[] filters, Function<Filter[], QueryFragment[]> queryFragmentSupplier) {
            onLeaves(this.filters, (t) -> t.with(queryFragmentSupplier.apply(filters)));
            return this;
        }

        public SymmetricExtender withFilters(Filter... filters) {
            return with(filters, FilterFragment::from);
        }

        public SymmetricExtender withFilters(Filter[][] filters) {
            return with(filters, FilterFragment::from);
        }

        public SymmetricExtender withPath(Filter... path) {
            return with(path, PathFragment::from);
        }

        public SymmetricExtender withPath(Filter[][] path) {
            return with(path, PathFragment::from);
        }

        public QueryFragmentTree get() {
            return filters.build();
        }

        private void onLeaves(QueryFragmentTree.Builder root, Consumer<QueryFragmentTree.Builder> leafMutator) {
            if (root.children.isEmpty()) {
                leafMutator.accept(root);
            } else {
                for (QueryFragmentTree.Builder c : root.children) {
                    onLeaves(c, leafMutator);
                }
            }
        }
    }
}
