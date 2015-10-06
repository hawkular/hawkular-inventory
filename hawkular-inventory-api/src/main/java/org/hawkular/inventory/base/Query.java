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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.base.spi.NoopFilter;

/**
 * Represents a tree of filters.
 * The filters that are used to represent an inventory query (so that new query instances can be generated at will)
 * form a tree because of the fact that environments can both contain resources or metrics directly or they can contain
 * feeds that in turn contain the metrics and resources.
 *
 * <p>The API contains a method to traverse both of these kinds of "placement" of metrics or resources
 * simultaneously. This is nice from a user perspective but creates problems in the impl, because using the
 * relationships of the resources or metrics, one can construct queries, that would be able to branch multiple times
 * and hence form a tree.
 *
 * @author Lukas Krejci
 * @since 0.1.0
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

            Consumer<Filter> addOps = (f) -> {
                if (!(f instanceof NoopFilter)) {
                    pathToLeaf.add(f);
                }
            };

            for (Filter[] fs : workingPath) {
                Arrays.asList(fs).forEach(addOps);
            }

            Arrays.asList(filters(tree.getFragments())).forEach(addOps);

            results.add(pathToLeaf);
        } else {
            workingPath.add(filters(tree.getFragments()));
            for (Query subTree : tree.getSubTrees()) {
                addPathsToLeaves(subTree, workingPath, results);
            }
            workingPath.remove(workingPath.size() - 1);
        }
    }

    @SuppressWarnings({"ConstantConditions", "unchecked"})
    public static Query to(CanonicalPath entity) {
        return Query.path().with(With.path(entity)).get();
    }

    /**
     * @return an empty query
     */
    public static Query empty() {
        return new Builder().build();
    }

    /**
     * @return a symmetric builder that will start appending filter fragments to the query
     */
    public static SymmetricExtender filter() {
        return empty().extend().filter();
    }

    /**
     * @return a symmetric builder that will start appending path fragments to the query
     */
    public static SymmetricExtender path() {
        return empty().extend().path();
    }

    private Query() {
    }

    /**
     * @return the query fragments to compose the query from
     */
    public QueryFragment[] getFragments() {
        return fragments;
    }

    /**
     * @return the list of query "branches" from the point this query object represents.
     */
    public List<Query> getSubTrees() {
        return subTrees;
    }

    /**
     * @return a new builder initialized to "contain" this query
     */
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

    /**
     * @return a new symmetric builder initialized with this query
     */
    public SymmetricExtender extend() {
        return new SymmetricExtender(asBuilder());
    }

    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder();
        addToString(bld, 0);
        return bld.toString();
    }

    private void addToString(StringBuilder bld, int indentation) {
        indent(bld, indentation);
        bld.append("Query[fragments=").append(Arrays.toString(fragments));
        if (!subTrees.isEmpty()) {
            bld.append("\n");
            subTrees.forEach((s) -> s.indent(bld, indentation + 1));
        }
        bld.append("\n");
        indent(bld, indentation);
        bld.append("]");
    }

    private void indent(StringBuilder bld, int indentation) {
        for (int i = 0; i < indentation; ++i) {
            bld.append("  ");
        }
    }

    /**
     * A low-level builder able to create new branches in the query tree.
     */
    public static final class Builder {
        private List<QueryFragment> fragments = new ArrayList<>();
        private Query tree = new Query();
        private Builder parent;
        private List<Builder> children = new ArrayList<>();
        private boolean done;

        /**
         * Creates a new branch in the tree and returns a builder of that branch.
         * @return a new builder instance for building the child
         */
        public Builder branch() {
            Builder child = new Builder();
            child.parent = this;
            children.add(child);

            return child;
        }

        /**
         * Concludes the work on a branch and returns a builder of the parent "node", if any.
         * @return the parent builder
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
         * <p>
         * This method tries to optimize the query by checking if the provided fragments (appended to the current query)
         * form a canonical traversal and if so, replaces the query so far with a simple filter on the canonical path
         * instead of a traversal with more steps.
         *
         * @param filters the list of filters to apply to the query at this position in the tree.
         * @return this builder
         */
        public Builder with(QueryFragment... filters) {
            QueryOptimizer.appendOptimized(fragments, filters);
            return this;
        }

        public Builder with(Function<Filter, QueryFragment> converter, QueryFragment... filters) {
            QueryFragment[] converted = Arrays.asList(filters).stream().map(QueryFragment::getFilter).map(converter)
                    .toArray(QueryFragment[]::new);

            QueryOptimizer.appendOptimized(fragments, converted);
            return this;
        }

        public Builder with(Query other) {
            with(other.fragments);
            for (Query sub : other.getSubTrees()) {
                branch().with(sub).done();
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
        private Boolean isFilter;
        private SymmetricExtender(Query.Builder filters) {
            this.filters = filters;
            onLeaves(filters, (b) -> {
                QueryFragment last = b.fragments.isEmpty() ? null : b.fragments.get(b.fragments.size() - 1);
                if (last != null) {
                    isFilter = last instanceof FilterFragment;
                    queryFragmentSupplier = isFilter ? FilterFragment::from : PathFragment::from;
                    converter = isFilter ? FilterFragment::new : PathFragment::new;
                }
            });
        }

        /**
         * Modifies this extender to append path fragments with future calls to {@code with()} methods.
         * @return this instance
         */
        public SymmetricExtender path() {
            queryFragmentSupplier = PathFragment::from;
            converter = PathFragment::new;
            if (isFilter != null && isFilter && !filters.fragments.isEmpty()) {
                with(NoopFilter.INSTANCE);
            }
            isFilter = false;
            return this;
        }

        /**
         * Modifies this extender to append filter fragments with future calls to {@code with()} methods.\
         *
         * @return this instance
         */
        public SymmetricExtender filter() {
            queryFragmentSupplier = FilterFragment::from;
            converter = FilterFragment::new;
            if (isFilter != null && !isFilter && filters.fragments.isEmpty()) {
                with(NoopFilter.INSTANCE);
            }
            isFilter = true;
            return this;
        }

        /**
         * Appends the provided query to the leaves of the current query tree, converting all its fragments to the
         * current fragment type (determined by the last call to {@link #filter()} or {@link #path()}).
         *
         * @param other the query to append
         * @return this instance
         */
        public SymmetricExtender with(Query other) {
            onLeaves(this.filters, (builder) -> builder.with(other, converter));
            return this;
        }

        /**
         * Appends the provided query to the leaves of the current query tree, leaving the type of its fragments as they
         * originally were.
         *
         * @param other the query to append
         * @return this instance
         */
        public SymmetricExtender withExact(Query other) {
            onLeaves(this.filters, (builder) -> builder.with(other));
            return this;
        }

        /**
         * Appends the filters as query fragments determined by the last call to {@link #filter()} or {@link #path()}.
         *
         * <p>The filters is an array of arrays representing a new set of branches to be created at all the leaves of
         * the current query tree.
         *
         * @param filters the filters to append to the leaves of the query tree
         * @return this instance
         */
        public SymmetricExtender with(Filter[][] filters) {
            onLeaves(this.filters, (builder) -> {
                for (Filter[] fs : filters) {
                    builder.branch().with(queryFragmentSupplier.apply(fs));
                }
            });
            return this;
        }

        /**
         * Appends the filters as query fragments determined by the last call to {@link #filter()} or {@link #path()}.
         *
         * @param filters the filters to append to the leaves of the query tree
         * @return this instance
         */
        public SymmetricExtender with(Filter... filters) {
            onLeaves(this.filters, (t) -> t.with(queryFragmentSupplier.apply(filters)));
            return this;
        }

        /**
         * @return the final query.
         */
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
