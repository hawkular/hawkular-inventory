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

import java.time.Instant;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.model.Entity;

/**
 * Represents a change of a state of an entity. This is a simpler, internal representation of the
 * {@link org.hawkular.inventory.api.model.Change} API class.
 *
 * @author Lukas Krejci
 * @since 0.20.0
 */
public final class EntityStateChange<E extends Entity<?, ?>> implements Comparable<EntityStateChange<E>> {
    private final Action<?, E> action;
    private final E entity;
    private final Instant occurrenceTime;

    public EntityStateChange(Action<?, E> action, E entity, Instant occurrenceTime) {
        this.action = action;
        this.entity = entity;
        this.occurrenceTime = occurrenceTime;
    }

    public Action<?, E> getAction() {
        return action;
    }

    public E getEntity() {
        return entity;
    }

    public Instant getOccurrenceTime() {
        return occurrenceTime;
    }

    @Override public int compareTo(EntityStateChange<E> o) {
        int ret = occurrenceTime.compareTo(o.getOccurrenceTime());

        if (ret == 0) {
            ret = action.asEnum().compareTo(o.getAction().asEnum());
        }

        if (ret == 0) {
            return entity.getPath().toString().compareTo(o.entity.getPath().toString());
        }

        return ret;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EntityStateChange<?> that = (EntityStateChange<?>) o;

        if (!action.equals(that.action)) return false;
        if (!entity.equals(that.entity)) return false;
        return occurrenceTime.equals(that.occurrenceTime);

    }

    @Override public int hashCode() {
        int result = action.hashCode();
        result = 31 * result + entity.hashCode();
        result = 31 * result + occurrenceTime.hashCode();
        return result;
    }

    @Override public String toString() {
        final StringBuilder sb = new StringBuilder("EntityStateChange[");
        sb.append("action=").append(action.asEnum());
        sb.append(", entity=").append(entity);
        sb.append(", occurrenceTime=").append(occurrenceTime);
        sb.append(']');
        return sb.toString();
    }


}
