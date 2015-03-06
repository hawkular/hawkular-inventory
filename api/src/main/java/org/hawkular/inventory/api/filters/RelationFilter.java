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
 * Base class for filters that filter relationships.
 *
 * <p>The implementations of the Hawkular inventory API are supposed to support filtering relationships by
 * {@link org.hawkular.inventory.api.filters.RelationWith.Properties},
 * {@link org.hawkular.inventory.api.filters.RelationWith.Ids},
 * {@link org.hawkular.inventory.api.filters.RelationWith.TargetOfType},
 * {@link org.hawkular.inventory.api.filters.RelationWith.SourceOfType} and
 * {@link org.hawkular.inventory.api.filters.RelationWith.SourceOrTargetOfType}.
 *
 * To create these filters, feel free to use the static helper methods defined on {@link RelationWith}.
 * <p>
 * Note: Additional information for the library consumers.
 * Don't extend this class with the hope the new filter will work without any further work, It needs to be registered
 * and handled properly. See the <code>FilterApplicator</code> class in the referential implementation.
 * </p>
 *
 * @author Jirka Kremser
 * @since 1.0
 */
public class RelationFilter extends Filter {
    private static final RelationFilter[] EMPTY = new RelationFilter[0];

    public static Accumulator by(RelationFilter... filters) {
        return new Accumulator(filters);
    }

    public static RelationFilter[] all() {
        return EMPTY;
    }

    public static final class Accumulator {
        private final List<RelationFilter> filters = new ArrayList<>();

        private Accumulator(RelationFilter... fs) {
            for (RelationFilter filter : fs) {
                filters.add(filter);
            }
        }

        public Accumulator and(RelationFilter f) {
            filters.add(f);
            return this;
        }

        public Accumulator and(RelationFilter... fs) {
            Collections.addAll(filters, fs);
            return this;
        }

        public RelationFilter[] get() {
            return filters.toArray(new RelationFilter[filters.size()]);
        }
    }
}
