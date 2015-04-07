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

import com.google.gson.annotations.Expose;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
@SuppressWarnings("unchecked")
public final class Action<C, E> {
    private static final Action<?, ?> _CREATED = new Action<>();
    private static final Action<?, ?> _UPDATED = new Action<>();
    private static final Action<?, ?> _DELETED = new Action<>();
    private static final Action<EnvironmentCopy, Environment> _COPIED = new Action<>();
    private static final Action<Feed, Feed> _REGISTERED = new Action<>();

    public static <E> Action<E, E> created() {
        return (Action<E, E>) _CREATED;
    }

    public static <E> Action<E, E> updated() {
        return (Action<E, E>) _UPDATED;
    }

    public static <E> Action<E, E> deleted() {
        return (Action<E, E>) _DELETED;
    }

    public static Action<EnvironmentCopy, Environment> copied() {
        return _COPIED;
    }

    public static Action<Feed, Feed> registered() {
        return _REGISTERED;
    }

    private Action() {

    }

    public Enumerated asEnum() {
        return Enumerated.from(this);
    }

    public enum Enumerated {
        CREATED(_CREATED), UPDATED(_UPDATED), DELETED(_DELETED), COPIED(_COPIED), REGISTERED(_REGISTERED);

        private final Action<?, ?> action;

        Enumerated(Action<?, ?> action) {
            this.action = action;
        }

        public static Enumerated from(Action<?, ?> action) {
            for (Enumerated e : values()) {
                if (e.action == action) {
                    return e;
                }
            }

            throw new AssertionError("Unknown action");
        }
    }

    public static final class EnvironmentCopy {
        @Expose
        private final Environment source;

        @Expose
        private final Environment target;

        public EnvironmentCopy(Environment source, Environment target) {
            this.source = source;
            this.target = target;
        }

        public Environment getSource() {
            return source;
        }

        public Environment getTarget() {
            return target;
        }
    }
}
