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
package org.hawkular.inventory.lazy.spi;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;

import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;

/**
 * filter used internally by the lazy impl for jumping from a vertex to an edge or back.
 * This needs to be understood by all backends but is not directly part of the public API.
 */
public final class SwitchElementType extends Filter {
    private final Relationships.Direction direction;
    private final boolean fromEdge;

    public static SwitchElementType incomingRelationships() {
        return new SwitchElementType(incoming, false);
    }

    public static SwitchElementType outgoingRelationships() {
        return new SwitchElementType(outgoing, false);
    }

    public static SwitchElementType sourceEntities() {
        return new SwitchElementType(incoming, true);
    }

    public static SwitchElementType targetEntities() {
        return new SwitchElementType(outgoing, true);
    }

    public SwitchElementType(Relationships.Direction direction, boolean fromEdge) {
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
