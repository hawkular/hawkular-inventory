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
package org.hawkular.inventory.api.filters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;

/**
 * A base class for filters. Defines no filtering logic in and of itself.
 *
 * <p>The implementations of the Hawkular inventory API are supposed to support filtering by {@link Related},
 * {@link With.Ids} and {@link With.Types}. There is also a sub-class of filters for the relation filtering {@link
 * RelationFilter}.
 *
 * To create these filters, feel free to use the static helper methods defined on {@link With}.
 * <p>
 * Note: Additional information for the library consumers.<br>
 * Don't extend this class with hope that the new filter will work. This class is extendable only for the benefit of
 * the API implementations that can reuse it internally. For the users of the API, only the subclasses of Filter
 * declared directly in the API are available
 *
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public abstract class Filter {
    private static final Filter[] EMPTY = new Filter[0];

    public static Accumulator by(Filter... filters) {
        return new Accumulator(filters);
    }

    public static Filter[] all() {
        return EMPTY;
    }

    public static Filter[] pathTo(Entity<?, ?> entity) {
        return pathTo(entity.getPath());
    }

    @SuppressWarnings("unchecked")
    public static Filter[] pathTo(CanonicalPath path) {
        if (!path.isDefined()) {
            return new Filter[0];
        }

        List<Filter> fs = new ArrayList<>();

        for (Path.Segment s : path.getPath()) {
            fs.add(Related.by(Relationships.WellKnown.contains));
            fs.add(With.type(Entity.entityTypeFromSegmentType(s.getElementType())));
            fs.add(With.id(s.getElementId()));
        }

        if (fs.size() < 2) {
            return new Filter[0];
        } else {
            //remove the first 'contains' defined in the loop above
            List<Filter> ret = fs.subList(1, fs.size());
            return ret.toArray(new Filter[ret.size()]);
        }
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
