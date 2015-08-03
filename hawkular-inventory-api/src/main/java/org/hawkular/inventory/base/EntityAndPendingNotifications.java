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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.model.AbstractElement;

/**
 * A class for holding the results of wiring up a newly created element.
 *
 * @param <E> the type of the newly create inventory element
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class EntityAndPendingNotifications<E extends AbstractElement<?, ?>> {

    private final E entity;
    private final List<Notification<?, ?>> notifications;

    /**
     * Constructs a new instance.
     *
     * @param entity        the inventory element
     * @param notifications the list of notifications to be sent out describing the actions performed during the wiring
     *                      up of the new element.
     */
    EntityAndPendingNotifications(E entity, Notification<?, ?>... notifications) {
        this.entity = entity;
        this.notifications = new ArrayList<>();
        Collections.addAll(this.notifications, notifications);
    }

    EntityAndPendingNotifications(E entity, Iterable<Notification<?, ?>> notifications) {
        this.entity = entity;
        this.notifications = new ArrayList<>();
        notifications.forEach(this.notifications::add);
    }

    public E getEntity() {
        return entity;
    }

    public List<Notification<?, ?>> getNotifications() {
        return notifications;
    }

    /**
     * Represents a notification to be sent out. I.e. this wraps together the data necessary to sending a new inventory
     * event.
     *
     * @param <C> the type of the action context - i.e. the data describing the action
     * @param <V> the type of the value that the action has been performed upon
     */
    public static final class Notification<C, V> {
        private final C actionContext;
        private final V value;
        private final Action<C, V> action;

        /**
         * Constructs a new instance.
         *
         * @param actionContext the data describing the results of the action
         * @param value         the value that the action has been performed upon
         * @param action        the action itself
         */
        public Notification(C actionContext, V value, Action<C, V> action) {
            this.actionContext = actionContext;
            this.value = value;
            this.action = action;
        }

        /**
         * @return the action to be notified about
         */
        public Action<C, V> getAction() {
            return action;
        }

        /**
         * @return the data describing the action performed
         */
        public C getActionContext() {
            return actionContext;
        }

        /**
         * @return the value the action has been performed upon
         */
        public V getValue() {
            return value;
        }
    }
}
