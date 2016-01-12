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
public final class EntityAndPendingNotifications<BE, E extends AbstractElement<?, ?>> {

    private final BE entityRepresentation;
    private final E entity;
    private final List<Notification<?, ?>> notifications;

    /**
     * Constructs a new instance.
     *
     * @param entityRepresentation the backend representation of the entity
     * @param entity        the inventory element
     * @param notifications the list of notifications to be sent out describing the actions performed during the wiring
     */
    EntityAndPendingNotifications(BE entityRepresentation, E entity, Notification<?, ?>... notifications) {
        this.entityRepresentation = entityRepresentation;
        this.entity = entity;
        this.notifications = new ArrayList<>();
        Collections.addAll(this.notifications, notifications);
    }

    EntityAndPendingNotifications(BE entityRepresentation, E entity, Iterable<Notification<?, ?>> notifications) {
        this.entityRepresentation = entityRepresentation;
        this.entity = entity;
        this.notifications = new ArrayList<>();
        notifications.forEach(this.notifications::add);
    }

    public BE getEntityRepresentation() {
        return entityRepresentation;
    }

    public E getEntity() {
        return entity;
    }

    public List<Notification<?, ?>> getNotifications() {
        return notifications;
    }
}
