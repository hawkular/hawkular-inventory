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
package org.hawkular.inventory.api.model;

import java.time.Instant;
import java.util.Objects;

import org.hawkular.inventory.api.Action;

/**
 * Represents a change made to an entity at some point in time.
 *
 * @author Lukas Krejci
 * @since 0.20.0
 */
public final class Change<Element extends Entity<?, ?>> implements Comparable<Change<?>> {
    private final Instant time;
    private final Action<?, Element> action;
    private final Object actionContext;

    public <C> Change(Instant time, Action<C, Element> action, C actionContext) {
        time = Objects.requireNonNull(time, "time == null");
        action = Objects.requireNonNull(action, "action == null");
        actionContext = Objects.requireNonNull(actionContext, "actionContext == null");
        this.time = time;
        this.action = action;
        this.actionContext = actionContext;
    }

    public Instant getTime() {
        return time;
    }

    public Action<?, Element> getAction() {
        return action;
    }

    public Object getActionContext() {
        return actionContext;
    }

    @SuppressWarnings("unchecked")
    public Element getElement() {
        if (action.asEnum() == Action.updated().asEnum()) {
            return ((Action.Update<Element, ?>) actionContext).getOriginalEntity();
        } else {
            return (Element) actionContext;
        }
    }

    @Override public int compareTo(Change<?> o) {
        int diff = time.compareTo(o.time);
        if (diff != 0) {
            return diff;
        }

        diff = action.asEnum().ordinal() - o.action.asEnum().ordinal();
        if (diff != 0) {
            return diff;
        }

        return getElement().getPath().toString().compareTo(o.getElement().getPath().toString());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Change<?> change = (Change<?>) o;

        return time.equals(change.time) && action.equals(change.action) && getElement().equals(change.getElement());

    }

    @Override public int hashCode() {
        int result = time.hashCode();
        result = 31 * result + action.hashCode();
        result = 31 * result + actionContext.hashCode();
        return result;
    }
}
