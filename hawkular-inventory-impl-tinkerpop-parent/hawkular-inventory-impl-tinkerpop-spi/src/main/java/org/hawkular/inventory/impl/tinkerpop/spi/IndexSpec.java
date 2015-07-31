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
package org.hawkular.inventory.impl.tinkerpop.spi;

import com.tinkerpop.blueprints.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class IndexSpec {
    private final Map<String, Class<?>> properties;
    private final Class<? extends Element> elementType;

    public static Builder builder() {
        return new Builder();
    }

    public IndexSpec(Class<? extends Element> elementType, Map<String, Class<?>> properties) {
        this.elementType = elementType;
        this.properties = properties;
    }

    public Class<? extends Element> getElementType() {
        return elementType;
    }

    public Map<String, Class<?>> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "IndexSpec[type=" + elementType.getSimpleName() + ",properties=" + properties + "]";
    }

    public static final class Builder {
        private final Map<String, Class<?>> properties = new HashMap<>();
        private Class<? extends Element> elementType;

        private Builder() {

        }

        public Builder withProperty(String propertyName, Class<?> propertyValueType) {
            properties.put(propertyName, propertyValueType);
            return this;
        }

        public Builder withElementType(Class<? extends Element> elementType) {
            this.elementType = elementType;
            return this;
        }

        public IndexSpec build() {
            return new IndexSpec(elementType, properties);
        }
    }
}
