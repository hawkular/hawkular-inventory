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
 *
 */

package org.hawkular.inventory.api.model;

import java.time.Instant;

import org.hawkular.inventory.api.Action;

/**
 * Represents a change made to an entity at some point in time.
 *
 * @author Lukas Krejci
 * @since 0.19.0
 */
public final class Change<Element extends AbstractElement<?, ?>, Context> implements Comparable<Change<?, ?>> {
    private final Instant time;
    private final Action<Context, Element> action;
    private final Context actionContext;
    private final Element element;

    public Change(Instant time, Action<Context, Element> action, Context actionContext, Element element) {
        this.time = time;
        this.action = action;
        this.actionContext = actionContext;
        this.element = element;
    }

    public Instant getTime() {
        return time;
    }

    public Action<Context, Element> getAction() {
        return action;
    }

    public Context getActionContext() {
        return actionContext;
    }

    public Element getElement() {
        return element;
    }

    @Override public int compareTo(Change<?, ?> o) {
        int diff = time.compareTo(o.time);
        if (diff != 0) {
            return diff;
        }

        diff = action.asEnum().ordinal() - o.action.asEnum().ordinal();
        if (diff != 0) {
            return diff;
        }

        return element.getId().compareTo(o.element.getId());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Change<?, ?> change = (Change<?, ?>) o;

        return time.equals(change.time) && action.equals(change.action) && element.equals(change.element);

    }

    @Override public int hashCode() {
        int result = time.hashCode();
        result = 31 * result + action.hashCode();
        result = 31 * result + element.hashCode();
        return result;
    }
}
