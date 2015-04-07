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
 * @param <C> the type of the action context that will be passed to the observers
 * @param <E> the type of the entity.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class Interest<C, E> {
    private final Action<C, E> action;
    private final Class<E> entityType;

    public static <T> Builder<T> in(Class<T> entity) {
        return new Builder<>(entity);
    }

    public Interest(Action<C, E> action, Class<E> entityType) {
        this.action = action;
        this.entityType = entityType;
    }

    public Action<C, E> getAction() {
        return action;
    }

    public Class<E> getEntityType() {
        return entityType;
    }

    /**
     * Checks whether given object is of interest to this interest instance.
     *
     * @param action the action to be performed on the object
     * @param object the object to check
     * @return true if the object is of interest to this, false otherwise
     */
    public boolean matches(Action<?, ?> action, Object object) {
        return this.action == action && object != null && entityType.isAssignableFrom(object.getClass());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Interest<?, ?> interest = (Interest<?, ?>) o;

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

    public static final class Builder<E> {
        private final Class<E> entityType;

        private Builder(Class<E> entityType) {
            this.entityType = entityType;
        }

        public <C> Interest<C, E> being(Action<C, E> action) {
            return new Interest<>(action, entityType);
        }
    }
}
