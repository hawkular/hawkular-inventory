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
package org.hawkular.inventory.base.spi;

import org.hawkular.inventory.api.filters.Filter;

/**
 * A type of filter that does no filtering. This is seemingly useless but is used to delimit individual sets of filters
 * that should be applied during {@link org.hawkular.inventory.api.ResolvingToMultiple#getAll(Filter[][])}.
 *
 * <p>A call to the above mentioned method gets translated into a series of filter fragments delimited by a single
 * "noop" path fragment.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class NoopFilter extends Filter {
    public static NoopFilter INSTANCE = new NoopFilter();

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof NoopFilter;
    }

    @Override
    public String toString() {
        return "NoopFilter";
    }
}
