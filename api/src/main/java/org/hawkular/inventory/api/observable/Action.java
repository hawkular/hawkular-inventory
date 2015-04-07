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

import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
@SuppressWarnings("unchecked")
public final class Action<C, E> {
    private static final Action<?, ?> CREATE = new Action<>();
    private static final Action<?, ?> UPDATE = new Action<>();
    private static final Action<?, ?> DELETE = new Action<>();
    private static final Action<EnvironmentCopy, Environment> COPY = new Action<>();
    private static final Action<Feed, Feed> REGISTER = new Action<>();

    private Action() {

    }

    //theses should really be Action<E, E> but I get a stack overflow in javac 8_u40 if I do that...
    //didn't isolate the cause of this yet.. :(
    public static <C, E> Action<C, E> created() {
        return (Action<C, E>) CREATE;
    }

    public static <C, E> Action<C, E> updated() {
        return (Action<C, E>) UPDATE;
    }

    public static <C, E> Action<C, E> deleted() {
        return (Action<C, E>) DELETE;
    }

    public static Action<EnvironmentCopy, Environment> copied() {
        return COPY;
    }

    public static Action<Feed, Feed> registered() {
        return REGISTER;
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
}
