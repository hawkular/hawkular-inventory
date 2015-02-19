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
package org.hawkular.inventory.impl.blueprints;

import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class FilterWrapper {

    private FilterWrapper() {

    }

    public abstract <S, E> void accept(FilterVisitor<S, E> visitor, HawkularPipeline<S, E> query);

    public static FilterWrapper wrap(Filter filter) {
        if (filter == null) {
            throw new NullPointerException("filter == null");
        }
        if (filter instanceof Related) {
            return new RelatedWrapper((Related<?>)filter);
        } else if (filter instanceof With.Ids) {
            return new WithIdsWrapper((With.Ids) filter);
        } else if (filter instanceof With.Types) {
            return new WithTypesWrapper((With.Types) filter);
        }

        throw new IllegalArgumentException("Unsupported filter type " + filter.getClass());
    }

    private static final class RelatedWrapper extends FilterWrapper {
        private final Related<? extends Entity> filter;

        private RelatedWrapper(Related<? extends Entity> filter) {
            this.filter = filter;
        }

        public <S, E> void accept(FilterVisitor<S, E> visitor, HawkularPipeline<S, E> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class WithIdsWrapper extends FilterWrapper {
        private final With.Ids filter;

        private WithIdsWrapper(With.Ids filter) {
            this.filter = filter;
        }

        public <S, E> void accept(FilterVisitor<S, E> visitor, HawkularPipeline<S, E> query) {
            visitor.visit(query, filter);
        }
    }

    private static final class WithTypesWrapper extends FilterWrapper {
        private final With.Types filter;

        private WithTypesWrapper(With.Types filter) {
            this.filter = filter;
        }

        public <S, E> void accept(FilterVisitor<S, E> visitor, HawkularPipeline<S, E> query) {
            visitor.visit(query, filter);
        }
    }
}
