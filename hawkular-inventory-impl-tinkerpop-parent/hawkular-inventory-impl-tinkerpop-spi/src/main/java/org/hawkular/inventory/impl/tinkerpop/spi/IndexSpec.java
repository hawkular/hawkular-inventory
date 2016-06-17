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
package org.hawkular.inventory.impl.tinkerpop.spi;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.tinkerpop.gremlin.structure.Element;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class IndexSpec {
    private final Set<Property> properties;
    private final Class<? extends Element> elementType;
    private final boolean unique;

    public static Builder builder() {
        return new Builder();
    }

    public IndexSpec(Class<? extends Element> elementType, Set<Property> properties, boolean unique) {
        this.elementType = elementType;
        this.properties = properties;
        this.unique = unique;
    }

    public Class<? extends Element> getElementType() {
        return elementType;
    }

    public Set<Property> getProperties() {
        return properties;
    }

    public boolean isUnique() {
        return unique;
    }

    @Override
    public String toString() {
        return "IndexSpec[type=" + elementType.getSimpleName() + ",properties=" + properties + "]";
    }


    public static final class Property {
        private final String name;
        private final Class<?> type;
        private final boolean unique;
        private final String labelIndex;

        public Property(String name, Class<?> type, boolean unique, String labelIndex) {
            this.name = name;
            this.type = type;
            this.unique = unique;
            this.labelIndex = labelIndex;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getName() {
            return name;
        }

        public String getLabelIndex() {
            return labelIndex;
        }

        public boolean isUnique() {
            return unique;
        }

        public Class<?> getType() {
            return type;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Property property = (Property) o;
            return Objects.equals(name, property.name);
        }

        @Override public int hashCode() {
            return Objects.hash(name);
        }

        public static final class Builder {

            private String name;
            private Class<?> type;
            private boolean unique;
            private String labelIndex;

            private Builder() {

            }

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withType(Class<?> type) {
                this.type = type;
                return this;
            }

            public Builder withUnique(boolean unique) {
                this.unique = unique;
                return this;
            }

            public Builder withLabelIndex(String labelIndex) {
                this.labelIndex = labelIndex;
                return this;
            }

            public Property build() {
                return new Property(name, type, unique, labelIndex);
            }

        }

    }

    public static final class Builder {

        private final Set<Property> properties = new HashSet<>();
        private Class<? extends Element> elementType;
        private boolean unique;

        private Builder() {

        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withProperty(Property property) {
            properties.add(property);
            return this;
        }

        public Builder withElementType(Class<? extends Element> elementType) {
            this.elementType = elementType;
            return this;
        }

        public Builder withUnique(boolean unique) {
            this.unique = unique;
            return this;
        }

        public IndexSpec build() {
            return new IndexSpec(elementType, properties, unique);
        }
    }
}
