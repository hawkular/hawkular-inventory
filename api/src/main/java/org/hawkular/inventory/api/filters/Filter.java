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
package org.hawkular.inventory.api.filters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public abstract class Filter {
    private static final Filter[] EMPTY = new Filter[0];

    Filter() {

    }

    public static Accumulator by(Filter... filters) {
        return new Accumulator(filters);
    }

    public static Filter[] all() {
        return EMPTY;
    }

    public static final class Accumulator {
        private final List<Filter> filters = new ArrayList<>();

        private Accumulator(Filter... fs) {
            for (Filter filter : fs) {
                filters.add(filter);
            }
        }

        public Accumulator and(Filter f) {
            filters.add(f);
            return this;
        }

        public Accumulator and(Filter... fs) {
            Collections.addAll(filters, fs);
            return this;
        }

        public Filter[] get() {
            return filters.toArray(new Filter[filters.size()]);
        }
    }
}
