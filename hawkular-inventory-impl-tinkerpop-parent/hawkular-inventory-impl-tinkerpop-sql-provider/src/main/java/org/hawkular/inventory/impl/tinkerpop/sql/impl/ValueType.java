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
package org.hawkular.inventory.impl.tinkerpop.sql.impl;

import java.math.BigDecimal;
import java.sql.NClob;
import java.sql.SQLException;
import java.util.Iterator;

/**
* @author Lukas Krejci
* @since 0.13.0
*/
enum ValueType {
    BOOLEAN(Boolean.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((BigDecimal) input).byteValue() != 0;
        }

        @Override
        public Object covertToDBType(Object input) {
            return ((Boolean) input) ? 1 : 0;
        }
    }, CHARACTER(Character.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((String) input).charAt(0);
        }
    }, BYTE(Byte.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((BigDecimal) input).byteValue();
        }
    }, SHORTINT(Short.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((BigDecimal) input).shortValue();
        }
    }, INT(Integer.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((BigDecimal) input).intValue();
        }
    }, LONG(Long.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((BigDecimal) input).longValue();
        }
    }, FLOAT(Float.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((BigDecimal) input).floatValue();
        }
    }, DOUBLE(Double.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return ((BigDecimal) input).doubleValue();
        }
    }, STRING(String.class) {
        @Override
        public Object convertFromDBType(Object input) {
            if (input instanceof String) {
                return input;
            }

            NClob lob = (NClob) input;
            try {
                return lob.getSubString(1, (int) lob.length());
            } catch (SQLException e) {
                throw new IllegalStateException("Could not get a string value from DB.", e);
            }
        }
    }, NULL(Void.class) {
        @Override
        public Object convertFromDBType(Object input) {
            return null;
        }
    };

    private final Class<?> valueType;

    ValueType(Class<?> valueType) {
        this.valueType = valueType;
    }

    public abstract Object convertFromDBType(Object input);

    public Object covertToDBType(Object input) {
        return input;
    }

    public boolean isNumeric() {
        switch (this) {
        case CHARACTER:
        case STRING:
            return false;
        default:
            return true;
        }
    }

    public static ValueType of(Object object, boolean toBeStored) {
        if (object == null) {
            return NULL;
        }

        if (!toBeStored) {
            if (object instanceof Iterable) {
                Iterator<?> it = ((Iterable<?>) object).iterator();
                if (!it.hasNext()) {
                    return null;
                }

                object = it.next();
            }
        }

        for (ValueType v : ValueType.values()) {
            if (v.valueType.isAssignableFrom(object.getClass())) {
                return v;
            }
        }

        return null;
    }
}
