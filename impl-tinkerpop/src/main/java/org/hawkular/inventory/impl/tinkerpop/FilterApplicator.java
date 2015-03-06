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

import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Lukas Krejci
 * @author Jirka Kremser
 * @since 1.0
 */
abstract class FilterApplicator {
    protected final Type type;

    private FilterApplicator(Type type) {
        this.type = type;
    }

    public static Filter[] filters(FilterApplicator... applicators) {
        Filter[] ret = new Filter[applicators.length];

        for(int i = 0; i < applicators.length; ++i) {
            ret[i] = applicators[i].filter();
        }

        return ret;
    }

    public static FilterApplicator with(Type type, Filter filter) {
        // note: this is a static method so this can't be achieved elegantly with polymorphism
        // (or without using a singleton + polymorphism)
        if (filter == null) {
            throw new IllegalArgumentException("filter == null");
        }
        if (filter instanceof Related) {
            return new RelatedApplicator((Related<?>)filter, type);
        } else if (filter instanceof With.Ids) {
            return new WithIdsApplicator((With.Ids) filter, type);
        } else if (filter instanceof With.Types) {
            return new WithTypesApplicator((With.Types) filter, type);
        } else if (filter instanceof RelationWith.Ids) {
            return new RelationWithIdsApplicator((RelationWith.Ids) filter, type);
        } else if (filter instanceof RelationWith.Properties) {
            return new RelationWithPropertiesApplicator((RelationWith.Properties) filter, type);
        } else if (filter instanceof RelationWith.SourceOfType) {
            return new RelationWithSourcesOfTypesApplicator((RelationWith.SourceOfType) filter, type);
        } else if (filter instanceof RelationWith.TargetOfType) {
            return new RelationWithTargetsOfTypesApplicator((RelationWith.TargetOfType) filter, type);
        } else if (filter instanceof RelationWith.SourceOrTargetOfType) {
            return new RelationWithSourcesOrTargetsOfTypesApplicator((RelationWith.SourceOrTargetOfType) filter, type);
        } else if (filter instanceof RelationshipBrowser.JumpInOutFilter) {
            return new RelationWithJumpInOutApplicator((RelationshipBrowser.JumpInOutFilter) filter, type);
        }

        throw new IllegalArgumentException("Unsupported filter type " + filter.getClass());
    }

    public static Builder from(Type type, Filter... filters) {
        return new Builder(type, filters);
    }

    public static Builder fromFilter(Filter... filters) {
        return from(Type.FILTER, filters);
    }

    public static Builder fromPath(Filter... filters) {
        return from(Type.PATH, filters);
    }

    public static Builder from(FilterApplicator... filters) {
        return new Builder(filters);
    }

    public abstract void applyTo(HawkularPipeline<?, ?> query);

    public abstract Filter filter();

    @Override
    public String toString() {
        return "FilterApplicator[type=" + type + ", filter=" + filter() + "]";
    }

    private static final class RelatedApplicator extends FilterApplicator {
        private final Related<? extends Entity> filter;

        private RelatedApplicator(Related<? extends Entity> filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    private static final class WithIdsApplicator extends FilterApplicator {
        private final With.Ids filter;

        private WithIdsApplicator(With.Ids filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    private static final class WithTypesApplicator extends FilterApplicator {
        private final With.Types filter;

        private WithTypesApplicator(With.Types filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    public enum Type {
        PATH(new PathVisitor()), FILTER(new FilterVisitor());

        final FilterVisitor visitor;

        Type(FilterVisitor visitor) {
            this.visitor = visitor;
        }
    }

    private static final class RelationWithIdsApplicator extends FilterApplicator {
        private final RelationWith.Ids filter;

        private RelationWithIdsApplicator(RelationWith.Ids filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    private static final class RelationWithPropertiesApplicator extends FilterApplicator {
        private final RelationWith.Properties filter;

        private RelationWithPropertiesApplicator(RelationWith.Properties filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    private static final class RelationWithSourcesOfTypesApplicator extends FilterApplicator {
        private final RelationWith.SourceOfType filter;

        private RelationWithSourcesOfTypesApplicator(RelationWith.SourceOfType filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    private static final class RelationWithTargetsOfTypesApplicator extends FilterApplicator {
        private final RelationWith.TargetOfType filter;

        private RelationWithTargetsOfTypesApplicator(RelationWith.TargetOfType filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    private static final class RelationWithSourcesOrTargetsOfTypesApplicator extends FilterApplicator {
        private final RelationWith.SourceOrTargetOfType filter;

        private RelationWithSourcesOrTargetsOfTypesApplicator(RelationWith.SourceOrTargetOfType filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    private static final class RelationWithJumpInOutApplicator extends FilterApplicator {
        private final RelationshipBrowser.JumpInOutFilter filter;

        private RelationWithJumpInOutApplicator(RelationshipBrowser.JumpInOutFilter filter, Type type) {
            super(type);
            this.filter = filter;
        }

        public void applyTo(HawkularPipeline<?, ?> query) {
            type.visitor.visit(query, filter);
        }

        @Override
        public Filter filter() {
            return filter;
        }
    }

    static class Builder {
        private List<FilterApplicator> filters;

        private Builder(Type type, Filter... filters) {
            this.filters = new ArrayList<>();
            and(type, filters);
        }

        private Builder(FilterApplicator... filters) {
            this.filters = new ArrayList<>();
            Collections.addAll(this.filters, filters);
        }

        public Builder and(Type type, Filter... filters) {
            for (Filter f : filters) {
                this.filters.add(FilterApplicator.with(type, f));
            }

            return this;
        }

        public Builder andFilter(Filter... filters) {
            return and(Type.FILTER, filters);
        }

        public Builder andPath(Filter... path) {
            return and(Type.PATH, path);
        }

        public FilterApplicator[] get() {
            return filters.toArray(new FilterApplicator[filters.size()]);
        }
    }
}
