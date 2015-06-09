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
package org.hawkular.inventory.base;

import org.hawkular.inventory.api.filters.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
public final class Query {
    private QueryFragment[] fragments;
    private List<Query> subTrees = new ArrayList<>();

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
    public static Filter[][] filters(Query query) {
        List<List<Filter>> paths = new ArrayList<>();
        List<Filter[]> workingPath = new ArrayList<>();

        addPathsToLeaves(query, workingPath, paths);

        Filter[][] ret = new Filter[paths.size()][];
        Arrays.setAll(ret, (i) -> paths.get(i).toArray(new Filter[paths.get(i).size()]));
        return ret;
    }

    private static void addPathsToLeaves(Query tree, List<Filter[]> workingPath,
            List<List<Filter>> results) {

        if (tree.getSubTrees().isEmpty()) {
            //this is the leaf
            List<Filter> pathToLeaf = new ArrayList<>();
            for (Filter[] fs : workingPath) {
                Collections.addAll(pathToLeaf, fs);
            }

            Collections.addAll(pathToLeaf, filters(tree.getFragments()));

            results.add(pathToLeaf);
        } else {
            workingPath.add(filters(tree.getFragments()));
            for (Query subTree : tree.getSubTrees()) {
                addPathsToLeaves(subTree, workingPath, results);
            }
            workingPath.remove(workingPath.size() - 1);
        }
    }

    public static Query empty() {
        return new Builder().build();
    }

    public static SymmetricExtender filter() {
        return empty().extend().filter();
    }

    public static SymmetricExtender path() {
        return empty().extend().path();
    }

    private Query() {
    }

    public QueryFragment[] getFragments() {
        return fragments;
    }

    public List<Query> getSubTrees() {
        return subTrees;
    }

    public Builder asBuilder() {
        Builder b = new Builder();
        b.fragments = new ArrayList<>(Arrays.asList(fragments));
        for (Query subTree : subTrees) {
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
        private Query tree = new Query();
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
            new ArrayList<>(children).forEach(Query.Builder::done);

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

        public Builder with(Function<Filter, QueryFragment> converter, QueryFragment... filters) {
            this.fragments.addAll(Arrays.asList(filters).stream().map(QueryFragment::getFilter).map(converter)
                    .collect(Collectors.toList()));
            return this;
        }

        public Builder with(Query other) {
            with(other.fragments);
            for (Query sub : other.getSubTrees()) {
                branch();
                with(sub);
                done();
            }

            return this;
        }

        public Builder with(Query other, Function<Filter, QueryFragment> converter) {
            with(converter, other.fragments);
            for (Query sub : other.getSubTrees()) {
                branch().with(sub, converter).done();
            }

            return this;
        }

        /**
         * Builds the <b>whole</b> tree regardless of where in the tree the current builder "operates".
         *
         * @return the fully built tree
         */
        public Query build() {
            Query.Builder root = this;
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
        private Query.Builder filters;
        private Function<Filter[], QueryFragment[]> queryFragmentSupplier;
        private Function<Filter, QueryFragment> converter;

        private SymmetricExtender(Query.Builder filters) {
            this.filters = filters;
        }

        public SymmetricExtender path() {
            queryFragmentSupplier = PathFragment::from;
            converter = PathFragment::new;
            return this;
        }

        public SymmetricExtender filter() {
            queryFragmentSupplier = FilterFragment::from;
            converter = FilterFragment::new;
            return this;
        }

        public SymmetricExtender with(Query other) {
            onLeaves(this.filters, (builder) -> builder.with(other, converter));
            return this;
        }

        public SymmetricExtender withExact(Query other) {
            onLeaves(this.filters, (builder) -> builder.with(other));
            return this;
        }

        public SymmetricExtender with(Filter[][] filters) {
            onLeaves(this.filters, (builder) -> {
                for (Filter[] fs : filters) {
                    builder.branch().with(queryFragmentSupplier.apply(fs));
                }
            });
            return this;
        }

        public SymmetricExtender with(Filter... filters) {
            onLeaves(this.filters, (t) -> t.with(queryFragmentSupplier.apply(filters)));
            return this;
        }

        public Query get() {
            return filters.build();
        }

        private void onLeaves(Query.Builder root, Consumer<Query.Builder> leafMutator) {
            if (root.children.isEmpty()) {
                leafMutator.accept(root);
            } else {
                for (Query.Builder c : root.children) {
                    onLeaves(c, leafMutator);
                }
            }
        }
    }
}
