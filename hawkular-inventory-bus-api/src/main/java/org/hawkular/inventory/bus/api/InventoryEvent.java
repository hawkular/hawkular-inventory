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
package org.hawkular.inventory.bus.api;

import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.hawkular.bus.common.AbstractMessage;
import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.hawkular.inventory.json.mixins.CanonicalPathMixin;
import org.hawkular.inventory.paths.CanonicalPath;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public abstract class InventoryEvent<T extends AbstractElement<?, ?>> extends AbstractMessage {

    private Action.Enumerated action;
    private Tenant tenant;
    private T object;

    public static Class<? extends InventoryEvent<?>> determineEventType(Message message) {
        try {
            String entityType = message.getStringProperty("entityType");

            if (entityType == null) {
                throw new IllegalArgumentException("Cannot determine inventory message type. " +
                        "'entityType' header property is missing.");
            }

            switch (entityType) {
                case "relationship":
                    return RelationshipEvent.class;
                case "tenant":
                    return TenantEvent.class;
                case "environment":
                    return EnvironmentEvent.class;
                case "resourceType":
                    return ResourceTypeEvent.class;
                case "metricType":
                    return MetricTypeEvent.class;
                case "feed":
                    return FeedEvent.class;
                case "resource":
                    return ResourceEvent.class;
                case "metric":
                    return MetricEvent.class;
                case "dataEntity":
                    return DataEntityEvent.class;
                default:
                    throw new IllegalArgumentException("Failed to determine inventory event type from the " +
                            "'entityType' property: " + entityType);
            }
        } catch (JMSException e) {
            throw new IllegalArgumentException("Failed to read inventory event type.", e);
        }
    }

    public static InventoryEvent<?> decode(Message message) {
        try {
            String body = ((TextMessage) message).getText();
            return AbstractMessage.fromJSON(body, determineEventType(message));
        } catch (JMSException e) {
            throw new IllegalArgumentException("Failed to decode inventory event.", e);
        }
    }

    public static InventoryEvent<?> from(Action<?, ?> action, Tenant tenant, Object object) {
        if (object == null) {
            throw new IllegalArgumentException("object == null");
        }

        if (action == null) {
            throw new IllegalArgumentException("action == null");
        }

        if (object instanceof Tenant) {
            return new TenantEvent(action.asEnum(), (Tenant) object);
        } else if (object instanceof Environment) {
            return new EnvironmentEvent(action.asEnum(), tenant, (Environment) object);
        } else if (object instanceof Feed) {
            return new FeedEvent(action.asEnum(), tenant, (Feed) object);
        } else if (object instanceof Metric) {
            return new MetricEvent(action.asEnum(), tenant, (Metric) object);
        } else if (object instanceof MetricType) {
            return new MetricTypeEvent(action.asEnum(), tenant, (MetricType) object);
        } else if (object instanceof Resource) {
            return new ResourceEvent(action.asEnum(), tenant, (Resource) object);
        } else if (object instanceof ResourceType) {
            return new ResourceTypeEvent(action.asEnum(), tenant, (ResourceType) object);
        } else if (object instanceof Relationship) {
            return new RelationshipEvent(action.asEnum(), tenant, (Relationship) object);
        } else if (object instanceof DataEntity) {
            return new DataEntityEvent(action.asEnum(), tenant, (DataEntity) object);
        } else if (object instanceof Action.Update) {
            @SuppressWarnings("unchecked")
            AbstractElement<?, AbstractElement.Update> updated =
                    (AbstractElement<?, AbstractElement.Update>) ((Action.Update) object).getOriginalEntity();

            updated.update().with((AbstractElement.Update) ((Action.Update) object).getUpdate());

            //TODO should we instead send the whole update object? No time for that now, but it'd be preferable I think
            return from(action, tenant, updated);
        } else {
            throw new IllegalArgumentException("Unsupported entity type: " + object.getClass());
        }
    }

    protected InventoryEvent() {

    }

    protected InventoryEvent(Action.Enumerated action, Tenant tenant, T object) {
        this.action = action;
        this.tenant = tenant;
        this.object = object;
    }

    public Action.Enumerated getAction() {
        return action;
    }

    public void setAction(Action.Enumerated action) {
        this.action = action;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public T getObject() {
        return object;
    }

    public void setObject(T object) {
        this.object = object;
    }

    public Map<String, String> createMessageHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("action", action.name());
        if (object != null) {
            headers.put("entityType", firstLetterLowercased(object.getClass().getSimpleName()));
            headers.put("path", object.getPath().toString());
        }
        return headers;
    }

    @Override
    protected ObjectMapper buildObjectMapperForSerialization() {
        final ObjectMapper mapper = new ObjectMapper();
        InventoryJacksonConfig.configure(mapper);
        mapper.addMixIn(CanonicalPath.class, CanonicalPathMixin.class);
        return mapper;
    }

    @SuppressWarnings("unused")
    public static ObjectMapper buildObjectMapperForDeserialization() {
        final ObjectMapper mapper = new ObjectMapper();
        InventoryJacksonConfig.configure(mapper);
        mapper.addMixIn(CanonicalPath.class, CanonicalPathMixin.class);
        return mapper;
    }


    private static String firstLetterLowercased(String source) {
        return Character.toLowerCase(source.charAt(0)) + source.substring(1);
    }
}
