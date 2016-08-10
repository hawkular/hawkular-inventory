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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.ContentHashable;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.IdentityHashable;
import org.hawkular.inventory.api.model.Syncable;

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
    private static final Action<?, ?> _SYNC_HASH_CHANGED = new Action<>();
    private static final Action<?, ?> _IDENTITY_HASH_CHANGED = new Action<>();
    private static final Action<?, ?> _CONTENT_HASH_CHANGED = new Action<>();

    public static <E> Action<E, E> created() {
        return (Action<E, E>) _CREATED;
    }

    public static <U extends AbstractElement.Update, E extends AbstractElement<?, U>>
    Action<Update<E, U>, E> updated() {
        return (Action<Update<E, U>, E>) _UPDATED;
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

    public static <E extends Syncable> Action<E, E> syncHashChanged() {
        return (Action<E, E>) _SYNC_HASH_CHANGED;
    }

    public static <E extends IdentityHashable> Action<E, E> identityHashChanged() {
        return (Action<E, E>) _IDENTITY_HASH_CHANGED;
    }

    public static <E extends ContentHashable> Action<E, E> contentHashChanged() {
        return (Action<E, E>) _CONTENT_HASH_CHANGED;
    }

    private Action() {

    }

    public Enumerated asEnum() {
        return Enumerated.from(this);
    }

    @Override
    public String toString() {
        return asEnum().name();
    }

    public enum Enumerated {
        CREATED(_CREATED), UPDATED(_UPDATED), DELETED(_DELETED), COPIED(_COPIED), REGISTERED(_REGISTERED),
        SYNC_HASH_CHANGED(_SYNC_HASH_CHANGED), IDENTITY_HASH_CHANGED(_IDENTITY_HASH_CHANGED),
        CONTENT_HASH_CHANGED(_CONTENT_HASH_CHANGED);

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

        public Action<?, ?> getAction() {
            return action;
        }
    }

    public static final class EnvironmentCopy {
        private final Environment source;

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

    public static final class Update<E, U> {
        private final E originalEntity;

        private final U update;

        public Update(E originalEntity, U update) {
            this.originalEntity = originalEntity;
            this.update = update;
        }

        public E getOriginalEntity() {
            return originalEntity;
        }

        public U getUpdate() {
            return update;
        }
    }
}
