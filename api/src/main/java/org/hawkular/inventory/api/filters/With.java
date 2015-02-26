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
 * @author Lukas Krejci
 * @since 1.0
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
    public static Types types(Class<? extends Entity>... types) {
        return new Types(types);
    }

    public static Types type(Class<? extends Entity> type) {
        return new Types(type);
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
    }

    public static final class Types extends Filter {
        private final Class<? extends Entity>[] types;

        @SafeVarargs
        public Types(Class<? extends Entity>... types) {
            this.types = types;
        }

        public Class<? extends Entity>[] getTypes() {
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
    }

}
