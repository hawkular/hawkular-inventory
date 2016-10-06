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
package org.hawkular.inventory.base.spi;

import java.util.List;

import org.hawkular.inventory.api.model.Entity;

/**
 * Represents a series of changes in the history of an entity.
 *
 * @author Lukas Krejci
 * @since 0.20.0
 */
public final class EntityHistory<E extends Entity<?, ?>> {
    private final List<EntityStateChange<E>> changes;
    private final E initialState;

    public EntityHistory(List<EntityStateChange<E>> changes, E initialState) {
        this.changes = changes;
        this.initialState = initialState;
    }

    public List<EntityStateChange<E>> getChanges() {
        return changes;
    }

    public E getInitialState() {
        return initialState;
    }

    @Override public String toString() {
        final StringBuilder sb = new StringBuilder("EntityHistory[");
        sb.append("changes=").append(changes);
        sb.append(", initialState=").append(initialState);
        sb.append(']');
        return sb.toString();
    }
}
