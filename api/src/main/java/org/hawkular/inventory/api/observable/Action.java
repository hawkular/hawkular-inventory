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
public final class Action<T> {
    private static final Action<?> CREATE = new Action<>();
    private static final Action<?> UPDATE = new Action<>();
    private static final Action<?> DELETE = new Action<>();
    private static final Action<EnvironmentCopy> COPY = new Action<>();
    private static final Action<Feed> REGISTER = new Action<>();

    private Action() {

    }

    public static <T> Action<T> create() {
        return (Action<T>) CREATE;
    }

    public static <T> Action<T> update() {
        return (Action<T>) UPDATE;
    }

    public static <T> Action<T> delete() {
        return (Action<T>) DELETE;
    }

    public static Action<EnvironmentCopy> copy() {
        return COPY;
    }

    public static Action<Feed> register() {
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
