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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.tinkerpop.blueprints.Element;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class SqlElement implements Element {

    protected final SqlGraph graph;
    private final Long id;
    private HashMap<String, Object> cachedProperties;
    private long transactionCountAtCacheCreation;

    protected SqlElement(SqlGraph graph, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id can't be null");
        }

        this.graph = graph;
        this.id = id;
    }

    protected abstract String getPropertiesTableName();

    protected abstract String getUniquePropertiesTableName();

    protected abstract String getPropertyTableElementIdName();

    protected abstract List<String> getDisallowedPropertyNames();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key) {
        if (graph.isLoadPropertiesEagerly()) {
            cacheProperties();
            return (T) cachedProperties.get(key);
        }

        String table = graph.getIndexedKeys(this.getClass()).contains(key)
                ? getUniquePropertiesTableName()
                : getPropertiesTableName();

        String sql = "SELECT string_value, numeric_value, value_type FROM " + table + " WHERE " +
            getPropertyTableElementIdName() + " = ? AND name = ?";

        try {
            PreparedStatement stmt = graph.getStatements().get(sql);
            stmt.setLong(1, id);
            stmt.setString(2, key);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                int valueTypeInt = rs.getInt(3);
                ValueType valueType = ValueType.values()[valueTypeInt];

                return (T) valueType.convertFromDBType(rs.getObject(valueType.isNumeric() ? 2 : 1));
            }
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    private synchronized void cacheProperties() {
        if (cachedProperties == null || transactionCountAtCacheCreation != graph.getTransactionCount()) {
            cachedProperties = new HashMap<>();

            Consumer<String> runner = table -> {
                String sql = "SELECT name, string_value, numeric_value, value_type FROM " + table
                        + " WHERE "
                        + getPropertyTableElementIdName() + " = ?";

                synchronized (graph) {
                    transactionCountAtCacheCreation = graph.getTransactionCount();

                    try {
                        PreparedStatement stmt = graph.getStatements().get(sql);
                        stmt.setLong(1, id);

                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                int valueTypeInt = rs.getInt(4);
                                ValueType valueType = ValueType.values()[valueTypeInt];

                                Object val = valueType.convertFromDBType(rs.getObject(valueType.isNumeric() ? 3 : 2));
                                String name = rs.getString(1);

                                cachedProperties.put(name, val);
                            }
                        }

                    } catch (SQLException e) {
                        throw new SqlGraphException(e);
                    }
                }
            };

            runner.accept(getPropertiesTableName());
            runner.accept(getUniquePropertiesTableName());
        }
    }

    @Override
    public synchronized Set<String> getPropertyKeys() {
        if (graph.isLoadPropertiesEagerly()) {
            cacheProperties();
            return new HashSet<>(cachedProperties.keySet());
        }

        synchronized (graph) {
            String sql = "SELECT name FROM " + getPropertiesTableName() + " WHERE " + getPropertyTableElementIdName() +
                    " = ?";

            try {
                PreparedStatement stmt = graph.getStatements().get(sql);
                stmt.setLong(1, id);

                Set<String> ret = new HashSet<>();

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ret.add(rs.getString(1));
                    }
                }

                return ret;
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }
        }
    }

    @Override
    public synchronized void setProperty(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("empty key");
        }

        if (getDisallowedPropertyNames().contains(key)) {
            throw new IllegalArgumentException("disallowed property name");
        }

        if (value == null) {
            throw new IllegalArgumentException("null value not allowed");
        }

        ValueType valueType = ValueType.of(value, true);
        if (valueType == null) {
            throw new IllegalArgumentException(
                "Unsupported value type " + value.getClass() + ". Only primitive types and string are supported.");
        }

        Object usedValue = valueType.covertToDBType(value);

        synchronized (graph) {
            graph.withSavePoint(() -> {
                String table = graph.getIndexedKeys(this.getClass()).contains(key)
                        ? getUniquePropertiesTableName()
                        : getPropertiesTableName();

                String sql = "UPDATE " + table + " SET " +
                        (valueType.isNumeric() ? "numeric_value" : "string_value") + " = ?, value_type = ? WHERE " +
                        getPropertyTableElementIdName() + " = ? AND name = ?";

                PreparedStatement stmt = graph.getStatements().get(sql);
                stmt.setObject(1, usedValue);
                stmt.setInt(2, valueType.ordinal());
                stmt.setLong(3, id);
                stmt.setString(4, key);

                if (stmt.executeUpdate() == 0) {
                    sql = "INSERT INTO " + table + " (" + getPropertyTableElementIdName() +
                            ", name, string_value, numeric_value, value_type) VALUES (?, ?, ?, ?, ?)";

                    PreparedStatement stmt2 = graph.getStatements().get(sql);
                    stmt2.setLong(1, id);
                    stmt2.setString(2, key);
                    stmt2.setObject(3, valueType.isNumeric() ? null : usedValue);
                    stmt2.setObject(4, valueType.isNumeric() ? usedValue : null);
                    stmt2.setInt(5, valueType.ordinal());

                    stmt2.executeUpdate();
                }

                if (cachedProperties != null) {
                    cachedProperties.put(key, usedValue);
                }

                return null;
            });
        }
    }

    @Override
    public synchronized <T> T removeProperty(String key) {
        T value = getProperty(key);

        synchronized (graph) {
            String sql = "DELETE FROM " + getPropertiesTableName() + " WHERE " + getPropertyTableElementIdName() +
                    " = ? AND name = ?";

            return graph.withSavePoint(() -> {
                PreparedStatement stmt = graph.getStatements().get(sql);
                stmt.setLong(1, id);
                stmt.setString(2, key);
                stmt.executeUpdate();

                if (cachedProperties != null) {
                    cachedProperties.remove(key);
                }

                return value;
            });
        }
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SqlElement sqlVertex = (SqlElement) o;

        if (!id.equals(sqlVertex.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[id=").append(id);
        sb.append(']');
        return sb.toString();
    }

}
