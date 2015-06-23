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

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class With {
    private With() {

    }

    public static Ids id(String id) {
        return new Ids(id);
    }

    public static Ids ids(String... ids) {
        return new Ids(ids);
    }

    @SafeVarargs
    public static Types types(Class<? extends Entity<?, ?>>... types) {
        return new Types(types);
    }

    public static Types type(Class<? extends Entity<?, ?>> type) {
        return new Types(type);
    }

    public static CanonicalPaths path(CanonicalPath path) {
        return new CanonicalPaths(path);
    }

    public static CanonicalPaths paths(CanonicalPath... paths) {
        return new CanonicalPaths(paths);
    }

    /**
     * Filters for entities that have a property of given name regardless of its value.
     *
     * @param name the name of the property
     * @return the filter
     */
    public static PropertyValues property(String name) {
        return new PropertyValues(name);
    }

    /**
     * Filters for entities that have a property of given name equal to given value
     *
     * @param name  the name of the property
     * @param value the desired value
     * @return the filter
     */
    public static PropertyValues propertyValue(String name, Object value) {
        return new PropertyValues(name, value);
    }

    /**
     * Filters for entities that have a property of given name equal to one of the provided values.
     *
     * @param name   the name of the property
     * @param values the possible values
     * @return the filter
     */
    public static PropertyValues propertyValues(String name, Object... values) {
        return new PropertyValues(name, values);
    }

    public static final class Ids extends Filter {

        private final String[] ids;

        public Ids(String... ids) {
            this.ids = ids;
        }

        public String[] getIds() {
            return ids;
        }

        @Override
        public String toString() {
            return  "Ids" + Arrays.asList(ids).toString();
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

    public static final class Types extends Filter {
        private final Class<? extends Entity<?, ?>>[] types;

        @SafeVarargs
        public Types(Class<? extends Entity<?, ?>>... types) {
            this.types = types;
        }

        public Class<? extends Entity<?, ?>>[] getTypes() {
            return types;
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder("Types[");
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
            if (!(o instanceof Types)) return false;

            Types other = (Types) o;

            return Arrays.equals(types, other.types);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(types);
        }
    }

    public static final class CanonicalPaths extends Filter {
        private final CanonicalPath[] paths;

        public CanonicalPaths(CanonicalPath... paths) {
            this.paths = paths;
        }

        public CanonicalPath[] getPaths() {
            return paths;
        }

        @Override
        public String toString() {
            return "CanonicalPaths" + Arrays.asList(paths).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CanonicalPaths)) return false;

            CanonicalPaths other = (CanonicalPaths) o;

            return Arrays.equals(paths, other.paths);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(paths);
        }
    }

    public static final class PropertyValues extends Filter {
        private final String name;
        private final Object[] values;

        public PropertyValues(String name, Object... values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public Object[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            return "PropertyValues[" + "name='" + name + '\'' + ", values=" + Arrays.toString(values) + ']';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PropertyValues)) return false;

            PropertyValues that = (PropertyValues) o;

            return name.equals(that.name) && Arrays.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(values);
            return result;
        }
    }
}
