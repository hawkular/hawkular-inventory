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
import org.hawkular.inventory.base.spi.SwitchElementType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A filter applicator applies a filter to a Gremlin query.
 *
 * <p>There is a difference in how certain filters are applied if the query being constructed is considered to be a path
 * to a certain entity or if the filter is used to trim down the number of results.
 *
 * @author Lukas Krejci
 * @author Jirka Kremser
 * @see PathVisitor
 * @see FilterVisitor
 * @since 0.0.1
 */
abstract class FilterApplicator<T extends Filter> {
    protected final Type type;
    protected final T filter;

    private static Map<Class<? extends Filter>, Class<? extends FilterApplicator>> applicators;

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
        applicators.put(SwitchElementType.class, SwitchElementTypeApplicator.class);

    }

    private FilterApplicator(Type type, T f) {
        this.type = type;
        this.filter = f;
    }

    public static FilterApplicator of(Type type, Filter filter) {
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
    @SuppressWarnings("unchecked")
    public static <S, E> void applyAll(Query filterTree, HawkularPipeline<S, E> q) {
        if (filterTree == null) {
            return;
        }

        for (QueryFragment qf : filterTree.getFragments()) {
            Type applicatorType = (qf instanceof FilterFragment) ? Type.FILTER : Type.PATH;

            FilterApplicator.of(applicatorType, qf.getFilter()).applyTo(q);
        }

        if (filterTree.getSubTrees().isEmpty()) {
            return;
        }

        if (filterTree.getSubTrees().size() == 1) {
            applyAll(filterTree.getSubTrees().get(0), q);
        } else {
            List<HawkularPipeline<E, ?>> branches = new ArrayList<>();
            for (Query t : filterTree.getSubTrees()) {
                HawkularPipeline<E, ?> branch = new HawkularPipeline<>();
                applyAll(t, branch);
                branches.add(branch);
            }

            q.copySplit(branches.toArray(new HawkularPipeline[branches.size()])).exhaustMerge();
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

    private static final class WithIdsApplicator
            extends FilterApplicator<With.Ids> {
        private WithIdsApplicator(With.Ids filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class WithTypesApplicator
            extends FilterApplicator<With.Types> {
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

    private static final class RelationWithIdsApplicator
            extends FilterApplicator<RelationWith.Ids> {
        private RelationWithIdsApplicator(RelationWith.Ids filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class RelationWithPropertiesApplicator
            extends FilterApplicator<RelationWith.Properties> {

        private RelationWithPropertiesApplicator(RelationWith.Properties filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class RelationWithSourcesOfTypesApplicator
            extends FilterApplicator<RelationWith
            .SourceOfType> {

        private RelationWithSourcesOfTypesApplicator(RelationWith.SourceOfType filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class RelationWithTargetsOfTypesApplicator
            extends FilterApplicator<RelationWith.TargetOfType> {

        private RelationWithTargetsOfTypesApplicator(RelationWith.TargetOfType filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

    }

    private static final class RelationWithSourcesOrTargetsOfTypesApplicator
            extends FilterApplicator<RelationWith
            .SourceOrTargetOfType> {
        private RelationWithSourcesOrTargetsOfTypesApplicator(RelationWith.SourceOrTargetOfType filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }

    private static final class SwitchElementTypeApplicator
            extends FilterApplicator<SwitchElementType> {
        private SwitchElementTypeApplicator(SwitchElementType filter, Type type) {
            super(type, filter);
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }
    }
}
