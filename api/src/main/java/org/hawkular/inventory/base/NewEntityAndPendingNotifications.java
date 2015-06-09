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
package org.hawkular.inventory.base;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.model.AbstractElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class NewEntityAndPendingNotifications<E extends AbstractElement<?, ?>> {

    private final E entity;
    private final List<Notification<?, ?>> notifications;

    NewEntityAndPendingNotifications(E entity, Notification<?, ?>... notifications) {
        this.entity = entity;
        this.notifications = new ArrayList<>();
        Collections.addAll(this.notifications, notifications);
    }

    public E getEntity() {
        return entity;
    }

    public List<Notification<?, ?>> getNotifications() {
        return notifications;
    }

    public static final class Notification<C, V> {
        private final C actionContext;
        private final V value;
        private final Action<C, V> action;

        public Notification(C actionContext, V value, Action<C, V> action) {
            this.actionContext = actionContext;
            this.value = value;
            this.action = action;
        }

        public Action<C, V> getAction() {
            return action;
        }

        public C getActionContext() {
            return actionContext;
        }

        public V getValue() {
            return value;
        }
    }
}
