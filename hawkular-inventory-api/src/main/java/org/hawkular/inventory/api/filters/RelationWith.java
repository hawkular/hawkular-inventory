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
package org.hawkular.inventory.api.filters;

import java.util.Arrays;

import org.hawkular.inventory.api.model.Entity;

/**
 * @author Jirka Kremser
 * @since 1.0
 */
public final class RelationWith {
    private RelationWith() {

    }

    public static Ids id(String id) {
        return new Ids(id);
    }

    public static Ids ids(String... ids) {
        return new Ids(ids);
    }

    public static PropertyValues property(String property) {
        return new PropertyValues(property);
    }

    public static PropertyValues propertyValue(String property, Object value) {
        return new PropertyValues(property, value);
    }

    @SafeVarargs
    public static PropertyValues propertyValues(String property, Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("there must be at least one value of the property");
        }
        PropertyValues properties = new PropertyValues(property, values);
        return properties;
    }


    public static PropertyValues name(String value) {
        return new PropertyValues("label", value);
    }

    @SafeVarargs
    public static PropertyValues names(String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("there must be at least one value of the relation name");
        }
        PropertyValues names = new PropertyValues("label", values);
        return names;
    }

    @SafeVarargs
    public static SourceOfType sourcesOfTypes(Class<? extends Entity<?, ?>>... types) {
        return new SourceOfType(types);
    }

    public static SourceOfType sourceOfType(Class<? extends Entity<?, ?>> type) {
        return new SourceOfType(type);
    }

    @SafeVarargs
    public static TargetOfType targetsOfTypes(Class<? extends Entity<?, ?>>... types) {
        return new TargetOfType(types);
    }

    public static TargetOfType targetOfType(Class<? extends Entity<?, ?>> type) {
        return new TargetOfType(type);
    }

    public static final class Ids extends RelationFilter {

        private final String[] ids;

        public Ids(String... ids) {
            this.ids = ids;
        }

        public String[] getIds() {
            return ids;
        }

        @Override
        public String toString() {
            return  "RelationshipIds" + Arrays.asList(ids).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Ids)) return false;

            Ids other = (Ids) o;

            return Arrays.equals(ids, other.ids);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ids);
        }
    }

    public static final class PropertyValues extends RelationFilter {

        private final String property;
        private final Object[] values;

        public PropertyValues(String property, Object... values) {
            this.property = property;
            this.values = values;
        }

        public String getProperty() {
            return property;
        }

        public Object[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            return  "RelationshipProperty: " + getProperty() + "=" + Arrays.asList(values).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PropertyValues)) return false;

            PropertyValues that = (PropertyValues) o;

            return property.equals(that.property) && Arrays.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            int result = property.hashCode();
            result = 31 * result + Arrays.hashCode(values);
            return result;
        }
    }

    public static class SourceOrTargetOfType extends RelationFilter {
        private final Class<? extends Entity<?, ?>>[] types;

        public String getFilterName() {
            return "SourceOrTargetOfType";
        }

        @SafeVarargs
        public SourceOrTargetOfType(Class<? extends Entity<?, ?>>... types) {
            this.types = types;
        }

        public Class<? extends Entity<?, ?>>[] getTypes() {
            return types;
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder(getFilterName() + "[");
            if (types.length > 0) {
                ret.append(types[0].getSimpleName());

                for(int i = 1; i < types.length; ++i) {
                    ret.append(", ").append(types[i].getSimpleName());
                }
            }
            ret.append("]");
            return ret.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceOrTargetOfType)) return false;

            SourceOrTargetOfType that = (SourceOrTargetOfType) o;

            return Arrays.equals(types, that.types);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(types);
        }
    }

    public static final class SourceOfType extends SourceOrTargetOfType {

        @SafeVarargs
        public SourceOfType(Class<? extends Entity<?, ?>>... types) {
            super(types);
        }

        @Override
        public String getFilterName() {
            return "SourceOfType";
        }
    }

    public static final class TargetOfType extends SourceOrTargetOfType {

        @SafeVarargs
        public TargetOfType(Class<? extends Entity<?, ?>>... types) {
            super(types);
        }

        @Override
        public String getFilterName() {
            return "TargetOfType";
        }
    }

}
