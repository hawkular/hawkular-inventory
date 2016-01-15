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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.filters.Filter;

/**
 * Abstract base class for the 2 types of query fragments - {@link FilterFragment} and {@link PathFragment}.
 *
 * <p>A query fragment is an application of a single filter somwhere on the inventory traversal defined by the
 * {@link Query}.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public abstract class QueryFragment {

    private final Filter filter;

    QueryFragment(Filter filter) {
        this.filter = filter;
    }

    public Filter getFilter() {
        return filter;
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder(this.getClass().getSimpleName()).append("[");
        sb.append("filter=").append(filter);
        sb.append(']');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;

        if (!this.getClass().equals(o.getClass())) return false;

        QueryFragment that = (QueryFragment) o;

        return filter.equals(that.filter);

    }

    @Override
    public int hashCode() {
        return filter.hashCode();
    }
}
