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
package org.hawkular.inventory.api.observable;

/**
 * Expresses what the user is interested in observing.
 *
 * @param <E> the type of the entity.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class Interest<E> {
    private final Action action;
    private final Class<E> entityType;

    public static Builder<?> inCreate() {
        return in(Action.CREATE);
    }

    public static Builder<?> inUpdate() {
        return in(Action.UPDATE);
    }

    public static Builder<?> inDelete() {
        return in(Action.DELETE);
    }

    public static Builder<?> in(Action action) {
        return new Builder(action);
    }

    public Interest(Action action, Class<E> entityType) {
        this.action = action;
        this.entityType = entityType;
    }

    /**
     * Checks whether given object is of interest to this interest instance.
     *
     * @param action the action to be performed on the object
     * @param object the object to check
     * @return true if the object is of interest to this, false otherwise
     */
    public boolean matches(Action action, Object object) {
        return this.action == action && object != null && entityType.isAssignableFrom(object.getClass());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Interest<?> interest = (Interest<?>) o;

        if (action != interest.action) return false;
        return entityType.equals(interest.entityType);

    }

    @Override
    public int hashCode() {
        int result = action.hashCode();
        result = 31 * result + entityType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Interest[" + "action=" + action + ", entityType=" + entityType + ']';
    }

    public enum Action {
        CREATE, UPDATE, DELETE
    }

    public static final class Builder<T> {
        private final Action action;
        private Class<?> entityType;

        private Builder(Action action) {
            this.action = action;
        }

        public <E> Builder<E> of(Class<E> entityType) {
            this.entityType = entityType;
            return cast(this);
        }

        public Interest<T> build() {
            return new Interest<>(action, cast(entityType));
        }

        @SuppressWarnings("unchecked")
        private <U> U cast(Object o) {
            return (U) o;
        }
    }
}
