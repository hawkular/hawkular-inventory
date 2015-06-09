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
package org.hawkular.inventory.impl.tinkerpop.lazy;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;

/**
 * filter used internally by the impl for jumping from a vertex to an edge or back
 *
 * @author Jirka Kremser
 */
final class JumpInOutFilter extends Filter {
    private final Relationships.Direction direction;
    private final boolean fromEdge;

    JumpInOutFilter(Relationships.Direction direction, boolean fromEdge) {
        this.direction = direction;
        this.fromEdge = fromEdge;
    }

    public Relationships.Direction getDirection() {
        return direction;
    }

    public boolean isFromEdge() {
        return fromEdge;
    }

    @Override
    public String toString() {
        return "Jump[" + (fromEdge ? "from " : "to ") + direction.name() + " edges]";
    }
}
