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
package org.hawkular.inventory.impl.tinkerpop;

import org.hawkular.inventory.api.filters.Contained;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Owned;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A filter applicator applies a filter to a Gremlin query.
 *
 * <p>There is a difference in how certain filters are applied if the query being constructed is considered to be a path
 * to a certain entity or if the filter is used to trim down the number of results.
 *
 * @see PathVisitor
 * @see FilterVisitor
 *
 * @author Lukas Krejci
 * @author Jirka Kremser
 * @since 0.0.1
 */
abstract class FilterApplicator<T extends Filter> {
    protected final Type type;
    protected final T filter;

    private FilterApplicator(Type type, T f) {
        this.type = type;
        this.filter = f;
    }

    /**
     * Applies all the filters from the applicator tree to the provided Gremlin query.
     *
     * @param filterTree the tree of filters to apply to the query
     * @param q          the query to update with filters from the tree
     * @param <S>        type of the source of the query
     * @param <E>        type of the output of the query
     */
    @SuppressWarnings("unchecked")
    public static <S, E> void applyAll(Tree filterTree, HawkularPipeline<S, E> q) {
        if (filterTree == null) {
            return;
        }

        for (FilterApplicator<?> fa : filterTree.filters) {
            fa.applyTo(q);
        }

        if (filterTree.subTrees.isEmpty()) {
            return;
        }

        if (filterTree.subTrees.size() == 1) {
            applyAll(filterTree.subTrees.get(0), q);
        } else {
            List<HawkularPipeline<E, ?>> branches = new ArrayList<>();
            for (Tree t : filterTree.subTrees) {
                HawkularPipeline<E, ?> branch = new HawkularPipeline<>();
                applyAll(t, branch);
                branches.add(branch);
            }

            q.copySplit(branches.toArray(new HawkularPipeline[branches.size()])).exhaustMerge();
        }
    }

    /**
     * Converts the list of applicators to the list of filters.
     *
     * @param applicators the list of applicators
     * @return the list of corresponding filters
     */
    private static Filter[] filters(FilterApplicator... applicators) {
        Filter[] ret = new Filter[applicators.length];

        for(int i = 0; i < applicators.length; ++i) {
            ret[i] = applicators[i].filter;
        }

        return ret;
    }

    /**
     * Converts the provided filter tree to a list of paths (array of filters). Each of the paths in the result
     * represent 1 branch from the tree from root to some leaf.
     *
     * @param applicators the tree of filters
     * @return the list of paths
     */
    public static Filter[][] filters(FilterApplicator.Tree applicators) {
        List<List<Filter>> paths = new ArrayList<>();

        Deque<Filter[]> path = new ArrayDeque<>();
        Deque<Iterator<Tree>> traversalPosition = new ArrayDeque<>();

        path.add(filters(applicators.filters));
        traversalPosition.push(applicators.subTrees.iterator());

        while (!traversalPosition.isEmpty()) {
            Iterator<Tree> currentPos = traversalPosition.peek();
            if (currentPos.hasNext()) {
                Tree child = currentPos.next();
                if (child.subTrees.isEmpty()) {
                    //we have a leaf here
                    List<Filter> pathToLeaf = new ArrayList<>();
                    for (Filter[] fs : path) {
                        Collections.addAll(pathToLeaf, fs);
                    }
                    Collections.addAll(pathToLeaf, filters(child.filters));
                    paths.add(pathToLeaf);
                } else {
                    path.add(filters(child.filters));
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

    private static Map<Class<? extends  Filter>, Class<? extends FilterApplicator>> applicators;
    static {
        applicators = new HashMap<>();
        applicators.put(Related.class, RelatedApplicator.class);
        applicators.put(Contained.class, RelatedApplicator.class);
        applicators.put(Defined.class, RelatedApplicator.class);
        applicators.put(Owned.class, RelatedApplicator.class);
        applicators.put(With.Ids.class, WithIdsApplicator.class);
        applicators.put(With.Types.class, WithTypesApplicator.class);
        applicators.put(RelationWith.Ids.class, RelationWithIdsApplicator.class);
        applicators.put(RelationWith.Properties.class, RelationWithPropertiesApplicator.class);
        applicators.put(RelationWith.SourceOfType.class, RelationWithSourcesOfTypesApplicator.class);
        applicators.put(RelationWith.TargetOfType.class, RelationWithTargetsOfTypesApplicator.class);
        applicators.put(RelationWith.SourceOrTargetOfType.class, RelationWithSourcesOrTargetsOfTypesApplicator.class);
        applicators.put(RelationshipBrowser.JumpInOutFilter.class, RelationWithJumpInOutApplicator.class);

    }

    private static FilterApplicator<?>[] with(Type type, Filter... filters) {
        FilterApplicator<?>[] ret = new FilterApplicator[filters.length];
        Arrays.setAll(ret, (i) -> FilterApplicator.with(type, filters[i]));
        return ret;
    }

    private static FilterApplicator<?> with(Type type, Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter == null");
        }
        Class<? extends Filter> filterClazz = filter.getClass();
        Class<? extends FilterApplicator> applicatorClazz = applicators.get(filterClazz);
        if (applicatorClazz == null) {
            throw new IllegalArgumentException("Unsupported filter type " + filterClazz);
        }
        Constructor<? extends FilterApplicator> constructor = null;
        try {
            constructor = applicatorClazz.getDeclaredConstructor(filterClazz, Type.class);
        } catch (NoSuchMethodException e) {
            try {
                // Contained, Defined, Owned
                constructor = applicatorClazz.getDeclaredConstructor(filterClazz.getSuperclass(), Type.class);
            } catch (NoSuchMethodException e1) {
                throw new IllegalArgumentException("Unable to create an instance of " + applicatorClazz);
            }
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(filter, type);
        } catch(InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException("Unable to create an instance of " + applicatorClazz);
        }
    }

    /**
     * Creates a new symmetric tree extender that start off from the provided list of paths.
     *
     * @param type    the type of the traversal
     * @param filters the list of paths
     * @return an extender that can be used to further extend the provided paths
     */
    public static SymmetricTreeExtender from(Type type, Filter[][] filters) {
        return new SymmetricTreeExtender(type, filters);
    }

    public static SymmetricTreeExtender fromPath(Filter[][] filters) {
        return from(Type.PATH, filters);
    }

    public static SymmetricTreeExtender fromPath(Filter... filters) {
        Filter[][] fs = new Filter[1][];
        fs[0] = filters;
        return fromPath(fs);
    }

    public static SymmetricTreeExtender from(FilterApplicator.Tree filters) {
        return new SymmetricTreeExtender(filters);
    }

    /**
     * To be implemented by inheritors, this applies the filter this applicator holds to the provided query taking into
     * the account the type of the filter.
     *
     * @param query the query to update with filter
     */
    public abstract void applyTo(HawkularPipeline<?, ?> query);

    public Filter filter() {
        return filter;
    }

    @Override
    public String toString() {
        return "FilterApplicator[type=" + type + ", filter=" + filter + "]";
    }

    private static final class RelatedApplicator<T extends Related<?>> extends FilterApplicator<Related<?>> {

        private RelatedApplicator(T filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class WithIdsApplicator extends FilterApplicator<With.Ids> {
        private WithIdsApplicator(With.Ids filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class WithTypesApplicator extends FilterApplicator<With.Types> {
        private WithTypesApplicator(With.Types filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    public enum Type {
        PATH(new PathVisitor()), FILTER(new FilterVisitor());

        final FilterVisitor visitor;

        Type(FilterVisitor visitor) {
            this.visitor = visitor;
        }
    }

    private static final class RelationWithIdsApplicator extends FilterApplicator<RelationWith.Ids> {
        private RelationWithIdsApplicator(RelationWith.Ids filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class RelationWithPropertiesApplicator extends FilterApplicator<RelationWith.Properties> {

        private RelationWithPropertiesApplicator(RelationWith.Properties filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class RelationWithSourcesOfTypesApplicator extends FilterApplicator<RelationWith
            .SourceOfType> {

        private RelationWithSourcesOfTypesApplicator(RelationWith.SourceOfType filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class RelationWithTargetsOfTypesApplicator extends FilterApplicator<RelationWith
            .TargetOfType> {

        private RelationWithTargetsOfTypesApplicator(RelationWith.TargetOfType filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

    }

    private static final class RelationWithSourcesOrTargetsOfTypesApplicator extends FilterApplicator<RelationWith
            .SourceOrTargetOfType> {
        private RelationWithSourcesOrTargetsOfTypesApplicator(RelationWith.SourceOrTargetOfType filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class RelationWithJumpInOutApplicator extends FilterApplicator<RelationshipBrowser
            .JumpInOutFilter> {
        private RelationWithJumpInOutApplicator(RelationshipBrowser.JumpInOutFilter filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    /**
     * Constructs an filter applicator tree by extending all the leaves with a uniform set of filters at a time.
     */
    static class SymmetricTreeExtender {
        private Tree.Builder filters;

        private SymmetricTreeExtender(Type type, Filter[][] filters) {
            this.filters = new Tree.Builder();
            and(type, filters);
        }

        private SymmetricTreeExtender(FilterApplicator.Tree filters) {
            this.filters = filters.asBuilder();
        }

        public SymmetricTreeExtender and(Type type, Filter[][] filters) {
            onLeaves(this.filters, (t) -> {
                for(Filter[] fs : filters) {
                    t.branch().with(FilterApplicator.with(type, fs));
                }
            });
            return this;
        }

        public SymmetricTreeExtender and(Type type, Filter... filters) {
            onLeaves(this.filters, (t) -> t.with(FilterApplicator.with(type, filters)));
            return this;
        }

        public SymmetricTreeExtender andFilter(Filter... filters) {
            return and(Type.FILTER, filters);
        }

        public SymmetricTreeExtender andPath(Filter... path) {
            return and(Type.PATH, path);
        }

        public SymmetricTreeExtender andPath(Filter[][] path) {
            return and(Type.PATH, path);
        }

        public FilterApplicator.Tree get() {
            return filters.build();
        }

        private void onLeaves(Tree.Builder root, Consumer<Tree.Builder> leafMutator) {
            if (root.children.isEmpty()) {
                leafMutator.accept(root);
            } else {
                for(Tree.Builder c : root.children) {
                    onLeaves(c, leafMutator);
                }
            }
        }
    }

    /**
     * Represents a tree of filter applicators.
     * The applicators that are used to represent a Gremlin query (so that new query instances can be generated at will)
     * form a tree because of the fact, environments can both contain resources or metrics directly or they can contain
     * feeds that in turn contain the metrics and resources.
     *
     * <p>The API contains a method to traverse both of these kinds of "placement" of metrics or resources
     * simultaneously. This is nice from a user perspective but creates problems in the impl, because using the
     * relationships of the resources or metrics, one can construct queries, the would be able to branch multiple times
     * and hence form a tree.
     */
    public static final class Tree {
        FilterApplicator<?>[] filters;
        List<Tree> subTrees = new ArrayList<>();

        private Tree() {}

        public Builder asBuilder() {
            Builder b = new Builder();
            b.filters = new ArrayList<>(Arrays.asList(filters));
            for (Tree subTree : subTrees) {
                Builder childBuilder = subTree.asBuilder();
                childBuilder.parent = b;
                b.children.add(childBuilder);
            }

            return b;
        }

        public static final class Builder {
            List<FilterApplicator<?>> filters = new ArrayList<>();
            Tree tree = new Tree();
            Builder parent;
            List<Builder> children = new ArrayList<>();
            boolean done;

            /**
             * Creates a new branch in the tree and returns a builder of that branch.
             */
            Builder branch() {
                Builder child = new Builder();
                child.parent = this;
                children.add(child);

                return child;
            }

            /**
             * Concludes the work on a branch and returns a builder of the parent "node", if any.
             */
            Builder done() {
                if (done) {
                    return parent;
                }

                this.tree.filters = filters.toArray(new FilterApplicator[filters.size()]);
                if (parent != null) {
                    parent.tree.subTrees.add(this.tree);
                    parent.children.remove(this);
                }

                //done will remove the child from the children, so we'd get concurrent modification exceptions
                //avoid that stupidly by working on a copy of children
                new ArrayList<>(children).forEach(Tree.Builder::done);

                done = true;

                return parent;
            }

            /**
             * Sets the filters to be used on the current node in the tree.
             * @param filters the list of filters to apply to the query at this position in the tree.
             * @return
             */
            Builder with(FilterApplicator<?>... filters) {
                Collections.addAll(this.filters, filters);
                return this;
            }

            /**
             * Builds the <b>whole</b> tree regardless of where in the tree the current builder "operates".
             * @return the fully built tree
             */
            Tree build() {
                Tree.Builder root = this;
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
    }
}
