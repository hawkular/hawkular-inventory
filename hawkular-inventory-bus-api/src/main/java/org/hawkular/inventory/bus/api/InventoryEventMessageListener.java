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
package org.hawkular.inventory.bus.api;

import javax.jms.Message;

import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.hawkular.inventory.api.model.Tenant;

/**
 * This can be used as a base class for receiving inventory events. This class takes care of automatically determining
 * the correct type of the inventory message received and deserializing it appropriately.
 *
 * <p>The users are supposed to implement the {@link #onBasicMessage(BasicMessage)} method where they receive an
 * instance of one of the concrete subclasses of the {@link InventoryEvent} (i.e. {@link TenantEvent}, {@link FeedEvent}
 * and the like).
 *
 * @author Lukas Krejci
 * @since 0.3.2
 */
public abstract class InventoryEventMessageListener extends BasicMessageListener<InventoryEvent<?>> {

    private ThreadLocal<Class<? extends InventoryEvent<?>>> currentEventType = new ThreadLocal<>();

    @SuppressWarnings("unchecked")
    public InventoryEventMessageListener() {
        super((Class<InventoryEvent<?>>) (Class) DummyEvent.class);
    }

    @Override
    protected InventoryEvent<?> getBasicMessageFromMessage(Message message) {
        try {
            currentEventType.set(InventoryEvent.determineEventType(message));
            return super.getBasicMessageFromMessage(message);
        } finally {
            currentEventType.set(null);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<InventoryEvent<?>> getBasicMessageClass() {
        Class<? extends InventoryEvent<?>> cls = currentEventType.get();

        if (cls == null) {
            return super.getBasicMessageClass();
        } else {
            return (Class<InventoryEvent<?>>) cls;
        }
    }

    private static final class DummyEvent extends InventoryEvent<Tenant> {
    }
}
