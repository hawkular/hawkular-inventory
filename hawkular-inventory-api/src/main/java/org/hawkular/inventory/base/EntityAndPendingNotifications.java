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
}
