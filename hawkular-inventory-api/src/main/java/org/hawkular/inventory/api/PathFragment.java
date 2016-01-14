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
 * A query fragment that represents a path step (i.e. the traversal represented by a query progresses futher by
 * understanding the filter as a means of finding the target of the "hop").
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class PathFragment extends QueryFragment {

    /**
     * Constructs path fragments corresponding to the provided filters.
     *
     * @param filters the filters to create path fragments from
     * @return the path fragments
     */
    public static PathFragment[] from(Filter... filters) {
        PathFragment[] ret = new PathFragment[filters.length];
        Arrays.setAll(ret, (i) -> new PathFragment(filters[i]));
        return ret;
    }

    public static PathFragment from(Filter filter) {
        return new PathFragment(filter);
    }

    public PathFragment(Filter filter) {
        super(filter);
    }
}
