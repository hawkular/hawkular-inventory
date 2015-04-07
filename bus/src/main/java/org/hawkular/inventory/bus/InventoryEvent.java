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
package org.hawkular.inventory.bus;

import com.google.gson.annotations.Expose;
import org.hawkular.bus.common.BasicMessage;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class InventoryEvent extends BasicMessage {
    @Expose
    private final Object payload;

    public InventoryEvent(Object payload) {
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "InventoryMessage[" + "payload=" + payload + ']';
    }
}
