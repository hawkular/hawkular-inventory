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

import java.util.Arrays;

import org.hawkular.inventory.api.filters.Filter;

/**
 * A query fragment that represents a filtering step (i.e. the possible set of results is filtered down but the query
 * doesn't progress futher along the path in the inventory).
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class FilterFragment extends QueryFragment {

    /**
     * A new array of filter fragments each constructed using the corresponding filters in the provided array.
     *
     * @param filters the filters to create the fragments from
     * @return the array of filter fragments
     */
    public static FilterFragment[] from(Filter... filters) {
        FilterFragment[] ret = new FilterFragment[filters.length];
        Arrays.setAll(ret, (i) -> new FilterFragment(filters[i]));
        return ret;
    }

    public FilterFragment(Filter filter) {
        super(filter);
    }
}
