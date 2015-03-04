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

import org.hawkular.inventory.api.model.Entity;

import java.util.Arrays;

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

    public static Properties property(String property, String value) {
        return new Properties(property, value);
    }

    @SafeVarargs
    public static Properties properties(String property, String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("there must be at least one value of the property");
        }
        Properties properties = new Properties(property, values);
        return properties;
    }


    public static Properties name(String value) {
        return new Properties("label", value);
    }

    @SafeVarargs
    public static Properties names(String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("there must be at least one value of the relation name");
        }
        Properties names = new Properties("label", values);
        return names;
    }

    @SafeVarargs
    public static EntityTypes entityTypes(Class<? extends Entity>... types) {
        return new EntityTypes(types);
    }

    public static EntityTypes entityType(Class<? extends Entity> type) {
        return new EntityTypes(type);
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
            return  "Ids" + Arrays.asList(ids).toString();
        }
    }

    public static final class Direction extends RelationFilter {
        private final Directions value;

        public enum Directions {
            // todo: consider renaming to targeted, sourced, both
            INCOMING, OUTGOING, BOTH
        }

        public Direction(Directions value) {
            this.value = value;
        }

        public static Direction incoming = new Direction(Directions.INCOMING);
        public static Direction outgoing = new Direction(Directions.OUTGOING);
        public static Direction both = new Direction(Directions.BOTH);
    }

    public static final class Properties extends RelationFilter {

        private final String property;
        private final String[] values;

        public Properties(String property, String... values) {
            this.property = property;
            this.values = values;
        }

        public String getProperty() {
            return property;
        }

        public String[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            return  "Property: " + getProperty() + "=" + Arrays.asList(values).toString();
        }
    }

    public static final class EntityTypes extends Filter {
        private final Class<? extends Entity>[] types;

        @SafeVarargs
        public EntityTypes(Class<? extends Entity>... types) {
            this.types = types;
        }

        public Class<? extends Entity>[] getTypes() {
            return types;
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder("EntityTypes[");
            if (types.length > 0) {
                ret.append(types[0].getSimpleName());

                for(int i = 1; i < types.length; ++i) {
                    ret.append(", ").append(types[i].getSimpleName());
                }
            }
            ret.append("]");
            return ret.toString();
        }
    }

}
