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
import org.hawkular.inventory.base.FilterFragment;
import org.hawkular.inventory.base.Query;
import org.hawkular.inventory.base.QueryFragment;
import org.hawkular.inventory.base.spi.NoopFilter;
import org.hawkular.inventory.base.spi.SwitchElementType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A filter applicator applies a filter to a Gremlin query.
 *
 * @author Lukas Krejci
 * @author Jirka Kremser
 * @see FilterVisitor
 * @since 0.0.1
 */
abstract class FilterApplicator<T extends Filter> {
    private static Map<Class<? extends Filter>, Class<? extends FilterApplicator<?>>> applicators;

    static {
        applicators = new HashMap<>();
        applicators.put(Related.class, RelatedApplicator.class);
        applicators.put(Contained.class, RelatedApplicator.class);
        applicators.put(Defined.class, RelatedApplicator.class);
        applicators.put(Owned.class, RelatedApplicator.class);
        applicators.put(With.Ids.class, WithIdsApplicator.class);
        applicators.put(With.Types.class, WithTypesApplicator.class);
        applicators.put(With.PropertyValues.class, WithPropertyValuesApplicator.class);
        applicators.put(RelationWith.Ids.class, RelationWithIdsApplicator.class);
        applicators.put(RelationWith.PropertyValues.class, RelationWithPropertiesApplicator.class);
        applicators.put(RelationWith.SourceOfType.class, RelationWithSourcesOfTypesApplicator.class);
        applicators.put(RelationWith.TargetOfType.class, RelationWithTargetsOfTypesApplicator.class);
        applicators.put(RelationWith.SourceOrTargetOfType.class, RelationWithSourcesOrTargetsOfTypesApplicator.class);
        applicators.put(SwitchElementType.class, SwitchElementTypeApplicator.class);
        applicators.put(NoopFilter.class, NoopApplicator.class);
    }

    protected final T filter;
    protected final FilterVisitor visitor = new FilterVisitor();

    private FilterApplicator(T f) {
        this.filter = f;
    }

    public static FilterApplicator of(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter == null");
        }
        Class<? extends Filter> filterClazz = filter.getClass();
        Class<? extends FilterApplicator<?>> applicatorClazz = applicators.get(filterClazz);
        if (applicatorClazz == null) {
            throw new IllegalArgumentException("Unsupported filter type " + filterClazz);
        }
        Constructor<? extends FilterApplicator<?>> constructor = null;
        try {
            constructor = applicatorClazz.getDeclaredConstructor(filterClazz);
        } catch (NoSuchMethodException e) {
            try {
                // Contained, Defined, Owned
                constructor = applicatorClazz.getDeclaredConstructor(filterClazz.getSuperclass());
            } catch (NoSuchMethodException e1) {
                throw new IllegalArgumentException("Unable to create an instance of " + applicatorClazz);
            }
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(filter);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException("Unable to create an instance of " + applicatorClazz);
        }
    }

    /**
     * Applies all the filters from the applicator tree to the provided Gremlin query.
     *
     * @param filterTree the tree of filters to apply to the query
     * @param q          the query to update with filters from the tree
     * @param <S>        type of the source of the query
     * @param <E>        type of the output of the query
     */
    public static <S, E> void applyAll(Query filterTree, HawkularPipeline<S, E> q) {
        if (filterTree == null) {
            return;
        }

        if (applyAll(filterTree, q, false)) {
            q.recall();
        }
    }

    /**
     * A private impl of the {@code applyAll()} method that tracks the current type of the filter being applied.
     * The type of the filter is either a path ({@code isFilter == false}) which potentially progresses the query to
     * next positions in the inventory traversal or a filter ({@code isFilter == true}) which merely trims down the
     * number of the elements at the current "tail" of the traversal by applying filters to them.
     *
     * @param query    the query
     * @param pipeline the Gremlin pipeline that the query gets translated to
     * @param isFilter whether we are currently processing filters as filters or path elements
     * @param <S>      the start element type of the pipeline
     * @param <E>      the end element type of the pipeline
     * @return true if after applying the filters, we're the filtering state or false if we are in path-progression
     * state.
     */
    @SuppressWarnings("unchecked")
    private static <S, E> boolean applyAll(Query query, HawkularPipeline<S, E> pipeline, boolean isFilter) {

        for (QueryFragment qf : query.getFragments()) {
            boolean thisIsFilter = qf instanceof FilterFragment;

            if (thisIsFilter != isFilter) {
                isFilter = thisIsFilter;
                if (thisIsFilter) {
                    pipeline.remember();
                } else {
                    pipeline.recall();
                }
            }

            FilterApplicator.of(qf.getFilter()).applyTo(pipeline);
        }


        if (query.getSubTrees().isEmpty()) {
            return isFilter;
        }

        if (query.getSubTrees().size() == 1) {
            return applyAll(query.getSubTrees().get(0), pipeline, isFilter);
        } else {
            List<HawkularPipeline<E, ?>> branches = new ArrayList<>();
            Iterator<Query> it = query.getSubTrees().iterator();

            // apply the first branch - in here, we know there are at least 2 actually
            HawkularPipeline<E, ?> branch = new HawkularPipeline<>();

            // the branch is a brand new pipeline, so it doesn't make sense for it to inherit
            // our current filter state.
            boolean newIsFilter = applyAll(it.next(), branch, false);
            // close the filter in the branch, if needed
            if (newIsFilter) {
                branch.recall();
            }
            branches.add(branch);

            while (it.hasNext()) {
                branch = new HawkularPipeline<>();
                boolean nextIsFilter = applyAll(it.next(), branch, false);
                // close the filter in the branch, if needed
                if (nextIsFilter) {
                    branch.recall();
                }
                if (nextIsFilter != newIsFilter) {
                    // this shouldn't normally be the case because the base impl extends the query tree
                    // symmetrically, but here we can't be sure of that.
                    throw new IllegalArgumentException("The branches of the query [" + query + "] don't change" +
                            " the path/filter state consistently.");
                }
                branches.add(branch);
            }

            pipeline.copySplit(branches.toArray(new HawkularPipeline[branches.size()])).exhaustMerge();

            return isFilter;
        }
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
        return "FilterApplicator[filter=" + filter + "]";
    }

    private static final class RelatedApplicator extends FilterApplicator<Related<?>> {

        private RelatedApplicator(Related<?> filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class WithIdsApplicator extends FilterApplicator<With.Ids> {
        private WithIdsApplicator(With.Ids filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class WithTypesApplicator extends FilterApplicator<With.Types> {
        private WithTypesApplicator(With.Types filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class RelationWithIdsApplicator extends FilterApplicator<RelationWith.Ids> {
        private RelationWithIdsApplicator(RelationWith.Ids filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class RelationWithPropertiesApplicator extends FilterApplicator<RelationWith.PropertyValues> {

        private RelationWithPropertiesApplicator(RelationWith.PropertyValues filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class RelationWithSourcesOfTypesApplicator
            extends FilterApplicator<RelationWith.SourceOfType> {

        private RelationWithSourcesOfTypesApplicator(RelationWith.SourceOfType filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class RelationWithTargetsOfTypesApplicator
            extends FilterApplicator<RelationWith.TargetOfType> {

        private RelationWithTargetsOfTypesApplicator(RelationWith.TargetOfType filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }

    }

    private static final class RelationWithSourcesOrTargetsOfTypesApplicator
            extends FilterApplicator<RelationWith.SourceOrTargetOfType> {
        private RelationWithSourcesOrTargetsOfTypesApplicator(RelationWith.SourceOrTargetOfType filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class SwitchElementTypeApplicator extends FilterApplicator<SwitchElementType> {
        private SwitchElementTypeApplicator(SwitchElementType filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class NoopApplicator extends FilterApplicator<NoopFilter> {
        private NoopApplicator(NoopFilter filter) {
            super(filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class WithPropertyValuesApplicator extends FilterApplicator<With.PropertyValues> {

        private WithPropertyValuesApplicator(With.PropertyValues f) {
            super(f);
        }

        @Override
        public void applyTo(HawkularPipeline<?, ?> query) {
            visitor.visit(query, filter);
        }
    }
}
