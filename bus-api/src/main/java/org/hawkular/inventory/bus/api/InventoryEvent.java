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

import com.google.gson.annotations.Expose;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public abstract class InventoryEvent<T> extends BasicMessage {
    @Expose
    private Action.Enumerated action;

    @SuppressWarnings("unchecked")
    public static <O> InventoryEvent<O> from(Action<?, ?> action, O object) {
        if (object == null) {
            throw new IllegalArgumentException("object == null");
        }

        if (action == null) {
            throw new IllegalArgumentException("action == null");
        }

        if (object instanceof Tenant) {
            return (InventoryEvent<O>) new TenantEvent(action.asEnum(), (Tenant) object);
        } else if (object instanceof Environment) {
            return (InventoryEvent<O>) new EnvironmentEvent(action.asEnum(), (Environment) object);
        } else if (object instanceof Feed) {
            return (InventoryEvent<O>) new FeedEvent(action.asEnum(), (Feed) object);
        } else if (object instanceof Metric) {
            return (InventoryEvent<O>) new MetricEvent(action.asEnum(), (Metric) object);
        } else if (object instanceof MetricType) {
            return (InventoryEvent<O>) new MetricTypeEvent(action.asEnum(), (MetricType) object);
        } else if (object instanceof Resource) {
            return (InventoryEvent<O>) new ResourceEvent(action.asEnum(), (Resource) object);
        } else if (object instanceof ResourceType) {
            return (InventoryEvent<O>) new ResourceTypeEvent(action.asEnum(), (ResourceType) object);
        } else if (object instanceof Relationship) {
            return (InventoryEvent<O>) new RelationshipEvent(action.asEnum(), (Relationship) object);
        } else {
            throw new IllegalArgumentException("Unsupported entity type: " + object.getClass());
        }
    }

    protected InventoryEvent() {

    }

    protected InventoryEvent(Action.Enumerated action) {
        this.action = action;
    }

    public Action.Enumerated getAction() {
        return action;
    }

    public void setAction(Action.Enumerated action) {
        this.action = action;
    }

    public abstract T getObject();

    public abstract void setObject(T object);
}
