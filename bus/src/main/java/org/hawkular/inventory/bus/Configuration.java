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

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hawkular.inventory.bus.Log.LOG;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class Configuration {

    private final String connectionFactoryJndiName;
    private final String entityChangesTopicName;

    public static Configuration fromProperties(Properties properties) {
        Map<String, String> map = new HashMap<>();

        properties.forEach((k, v) -> map.put(k.toString(), v.toString()));

        return fromMap(map);
    }

    public static Configuration fromMap(Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!Property.isValid(e.getKey())) {
                LOG.unknownConfigurationProperty(e.getKey());
            }
        }

        EnumMap<Property, String> emap = new EnumMap<>(Property.class);
        map.forEach((k,v) -> emap.put(Property.valueOf(k), v));

        return fromEnumMap(emap);
    }

    public static Configuration fromEnumMap(Map<Property, String> map) {
        String connectionFactoryJndiName = null;
        String entityChangesTopicName = null;

        for (Property p : Property.values()) {
            String value = map.get(p);
            if (value == null) {
                value = p.getDefaultValue();
            }

            switch (p) {
                case CONNECTION_FACTORY_JNDI_NAME:
                    connectionFactoryJndiName = value;
                    break;
                case INVENTORY_CHANGES_TOPIC_NAME:
                    entityChangesTopicName = value;
                    break;
            }
        }

        return new Configuration(connectionFactoryJndiName, entityChangesTopicName);
    }

    public static Configuration getDefaultConfiguration() {
        return fromEnumMap(Collections.emptyMap());
    }

    public static Builder builder() {
        return new Builder();
    }

    private Configuration(String connectionFactoryJndiName, String entityChangesTopicName) {
        this.connectionFactoryJndiName = connectionFactoryJndiName;
        this.entityChangesTopicName = entityChangesTopicName;
    }

    public String getConnectionFactoryJndiName() {
        return connectionFactoryJndiName;
    }

    public String getInventoryChangesTopicName() {
        return entityChangesTopicName;
    }

    public Builder modify() {
        EnumMap<Property, String> m = new EnumMap<>(Property.class);
        toMap().forEach((k, v) -> m.put(Property.valueOf(k), v));

        return new Builder(m);
    }

    public Map<String, String> toMap() {
        Map<String, String> ret = new HashMap<>();

        ret.put(Property.CONNECTION_FACTORY_JNDI_NAME.propertyName, connectionFactoryJndiName);
        ret.put(Property.INVENTORY_CHANGES_TOPIC_NAME.propertyName, entityChangesTopicName);

        return ret;
    }

    public enum Property {
        CONNECTION_FACTORY_JNDI_NAME("java:/HawkularBusConnectionFactory",
                "hawkular.inventory.bus.connectionFactoryJndiName"),
        INVENTORY_CHANGES_TOPIC_NAME("java:/topic/HawkularInventoryChanges",
                "hawkular.inventory.bus.inventoryChangesTopicName");

        private final String defaultValue;
        private final String propertyName;

        Property(String defaultValue, String propertyName) {
            this.defaultValue = defaultValue;
            this.propertyName = propertyName;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public static boolean isValid(String name) {
            for (Property p : values()) {
                if (p.getPropertyName().equals(name)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static final class Builder {
        private final Map<Property, String> config;

        private Builder() {
            config = new EnumMap<>(Property.class);
        }

        private Builder(EnumMap<Property, String> seed) {
            config = seed;
        }

        public Builder with(Property property, String value) {
            config.put(property, value);
            return this;
        }

        public Configuration build() {
            return Configuration.fromEnumMap(config);
        }
    }
}
