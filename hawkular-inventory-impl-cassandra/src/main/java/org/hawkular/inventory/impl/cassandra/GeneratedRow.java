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
package org.hawkular.inventory.impl.cassandra;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Token;
import com.datastax.driver.core.TupleValue;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.UDTValue;
import com.google.common.reflect.TypeToken;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public class GeneratedRow implements Row {

    private static final Constructor<ColumnDefinitions> COLUMN_DEFINITIONS_CONSTRUCTOR;
    private static final Constructor<ColumnDefinitions.Definition> DEFINITION_CONSTRUCTOR;
    static {
        //noinspection unchecked
        DEFINITION_CONSTRUCTOR = (Constructor<ColumnDefinitions.Definition>)
                ColumnDefinitions.Definition.class.getDeclaredConstructors()[0];
        DEFINITION_CONSTRUCTOR.setAccessible(true);

        //noinspection unchecked
        COLUMN_DEFINITIONS_CONSTRUCTOR = (Constructor<ColumnDefinitions>)
                ColumnDefinitions.class.getDeclaredConstructors()[0];
        COLUMN_DEFINITIONS_CONSTRUCTOR.setAccessible(true);
    }

    private final ColumnDefinitions columns;
    private final Object[] data;

    public static GeneratedRow ofEntity(String keyspace, String cp, String id, int type, String name,
                                        Map<String, String> props) {
        return new GeneratedRow(entityColDefs(keyspace), cp, id, type, name, props);
    }

    public static GeneratedRow ofRelationship(String keyspace, String cp, String sourceCp, String targetCp, String name,
                                              Map<String, String> props) {
        return new GeneratedRow(relationshipColDefs(keyspace), cp, sourceCp, targetCp, name, props);
    }

    private static ColumnDefinitions entityColDefs(String keyspace) {
        try {
            ColumnDefinitions.Definition[] entityCols = new ColumnDefinitions.Definition[]{
                    def(keyspace, Statements.ENTITY, Statements.CP, DataType.ascii()),
                    def(keyspace, Statements.ENTITY, Statements.ID, DataType.text()),
                    def(keyspace, Statements.ENTITY, Statements.TYPE, DataType.cint()),
                    def(keyspace, Statements.ENTITY, Statements.NAME, DataType.text()),
                    def(keyspace, Statements.ENTITY, Statements.PROPERTIES,
                            DataType.map(DataType.text(), DataType.text()))
            };

            return COLUMN_DEFINITIONS_CONSTRUCTOR.newInstance(entityCols, null);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new IllegalStateException("Failed to construct a fake Cassandra result row definitions for an" +
                    " entity.", e);
        }
    }

    private static ColumnDefinitions relationshipColDefs(String keyspace) {
        try {
            ColumnDefinitions.Definition[] relationshipCols = new ColumnDefinitions.Definition[]{
                    def(keyspace, Statements.ENTITY, Statements.CP, DataType.ascii()),
                    def(keyspace, Statements.ENTITY, Statements.NAME, DataType.text()),
                    def(keyspace, Statements.ENTITY, Statements.SOURCE_CP, DataType.ascii()),
                    def(keyspace, Statements.ENTITY, Statements.TARGET_CP, DataType.ascii()),
                    def(keyspace, Statements.ENTITY, Statements.PROPERTIES,
                            DataType.map(DataType.text(), DataType.text()))
            };

            return COLUMN_DEFINITIONS_CONSTRUCTOR.newInstance(relationshipCols, null);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new IllegalStateException("Failed to construct a fake Cassandra result row definitions for a" +
                    " relationship.", e);
        }
    }

    private static ColumnDefinitions.Definition def(String keyspace, String table, String name, DataType dataType)
            throws IllegalAccessException, InvocationTargetException, InstantiationException {
        return DEFINITION_CONSTRUCTOR.newInstance(keyspace, table, name, dataType);
    }

    private GeneratedRow(ColumnDefinitions cols, Object... data) {
        this.columns = cols;
        this.data = data;
    }

    @Override public ColumnDefinitions getColumnDefinitions() {
        return columns;
    }

    @Override public Token getToken(int i) {
        throw new UnsupportedOperationException();
    }

    @Override public Token getToken(String name) {
        throw new UnsupportedOperationException();
    }

    @Override public Token getPartitionKeyToken() {
        throw new UnsupportedOperationException();
     }

    @Override public boolean isNull(int i) {
        return data[i] == null;
    }

    @Override public boolean getBool(int i) {
        return (boolean) data[i];
    }

    @Override public byte getByte(int i) {
        return (byte) data[i];
    }

    @Override public short getShort(int i) {
        return (short) data[i];
    }

    @Override public int getInt(int i) {
        return (int) data[i];
    }

    @Override public long getLong(int i) {
        return (long) data[i];
    }

    @Override public Date getTimestamp(int i) {
        return (Date) data[i];
    }

    @Override public LocalDate getDate(int i) {
        return (LocalDate) data[i];
    }

    @Override public long getTime(int i) {
        return (long) data[i];
    }

    @Override public float getFloat(int i) {
        return (float) data[i];
    }

    @Override public double getDouble(int i) {
        return (double) data[i];
    }

    @Override public ByteBuffer getBytesUnsafe(int i) {
        return (ByteBuffer) data[i];
    }

    @Override public ByteBuffer getBytes(int i) {
        return (ByteBuffer) data[i];
    }

    @Override public String getString(int i) {
        return (String) data[i];
    }

    @Override public BigInteger getVarint(int i) {
        return (BigInteger) data[i];
    }

    @Override public BigDecimal getDecimal(int i) {
        return (BigDecimal) data[i];
    }

    @Override public UUID getUUID(int i) {
        return (UUID) data[i];
    }

    @Override public InetAddress getInet(int i) {
        return (InetAddress) data[i];
    }

    @SuppressWarnings("unchecked")
    @Override public <T> List<T> getList(int i, Class<T> elementsClass) {
        return (List<T>) data[i];
    }

    @SuppressWarnings("unchecked")
    @Override public <T> List<T> getList(int i, TypeToken<T> elementsType) {
        return (List<T>) data[i];
    }

    @SuppressWarnings("unchecked")
    @Override public <T> Set<T> getSet(int i, Class<T> elementsClass) {
        return (Set<T>) data[i];
    }

    @SuppressWarnings("unchecked")
    @Override public <T> Set<T> getSet(int i, TypeToken<T> elementsType) {
        return (Set<T>) data[i];
    }

    @SuppressWarnings("unchecked")
    @Override public <K, V> Map<K, V> getMap(int i, Class<K> keysClass, Class<V> valuesClass) {
        return (Map<K, V>) data[i];
    }

    @SuppressWarnings("unchecked")
    @Override public <K, V> Map<K, V> getMap(int i, TypeToken<K> keysType, TypeToken<V> valuesType) {
        return (Map<K, V>) data[i];
    }

    @Override public UDTValue getUDTValue(int i) {
        return (UDTValue) data[i];
    }

    @Override public TupleValue getTupleValue(int i) {
        return (TupleValue) data[i];
    }

    @Override public Object getObject(int i) {
        return data[i];
    }

    @Override public <T> T get(int i, Class<T> targetClass) {
        return targetClass.cast(data[i]);
    }

    @SuppressWarnings("unchecked")
    @Override public <T> T get(int i, TypeToken<T> targetType) {
        return (T) data[i];
    }

    @SuppressWarnings("unchecked")
    @Override public <T> T get(int i, TypeCodec<T> codec) {
        return (T) data[i];
    }

    @Override public boolean isNull(String name) {
        return isNull(columns.getIndexOf(name));
    }

    @Override public boolean getBool(String name) {
        return getBool(columns.getIndexOf(name));
    }

    @Override public byte getByte(String name) {
        return getByte(columns.getIndexOf(name));
    }

    @Override public short getShort(String name) {
        return getShort(columns.getIndexOf(name));
    }

    @Override public int getInt(String name) {
        return getInt(columns.getIndexOf(name));
    }

    @Override public long getLong(String name) {
        return getLong(columns.getIndexOf(name));
    }

    @Override public Date getTimestamp(String name) {
        return getTimestamp(columns.getIndexOf(name));
    }

    @Override public LocalDate getDate(String name) {
        return getDate(columns.getIndexOf(name));
    }

    @Override public long getTime(String name) {
        return getTime(columns.getIndexOf(name));
    }

    @Override public float getFloat(String name) {
        return getFloat(columns.getIndexOf(name));
    }

    @Override public double getDouble(String name) {
        return getDouble(columns.getIndexOf(name));
    }

    @Override public ByteBuffer getBytesUnsafe(String name) {
        return getBytesUnsafe(columns.getIndexOf(name));
    }

    @Override public ByteBuffer getBytes(String name) {
        return getBytes(columns.getIndexOf(name));
    }

    @Override public String getString(String name) {
        return getString(columns.getIndexOf(name));
    }

    @Override public BigInteger getVarint(String name) {
        return getVarint(columns.getIndexOf(name));
    }

    @Override public BigDecimal getDecimal(String name) {
        return getDecimal(columns.getIndexOf(name));
    }

    @Override public UUID getUUID(String name) {
        return getUUID(columns.getIndexOf(name));
    }

    @Override public InetAddress getInet(String name) {
        return getInet(columns.getIndexOf(name));
    }

    @Override public <T> List<T> getList(String name, Class<T> elementsClass) {
        return getList(columns.getIndexOf(name), elementsClass);
    }

    @Override public <T> List<T> getList(String name, TypeToken<T> elementsType) {
        return getList(columns.getIndexOf(name), elementsType);
    }

    @Override public <T> Set<T> getSet(String name, Class<T> elementsClass) {
        return getSet(columns.getIndexOf(name), elementsClass);
    }

    @Override public <T> Set<T> getSet(String name, TypeToken<T> elementsType) {
        return getSet(columns.getIndexOf(name), elementsType);
    }

    @Override public <K, V> Map<K, V> getMap(String name, Class<K> keysClass, Class<V> valuesClass) {
        return getMap(columns.getIndexOf(name), keysClass, valuesClass);
    }

    @Override public <K, V> Map<K, V> getMap(String name, TypeToken<K> keysType, TypeToken<V> valuesType) {
        return getMap(columns.getIndexOf(name), keysType, valuesType);
    }

    @Override public UDTValue getUDTValue(String name) {
        return getUDTValue(columns.getIndexOf(name));
    }

    @Override public TupleValue getTupleValue(String name) {
        return getTupleValue(columns.getIndexOf(name));
    }

    @Override public Object getObject(String name) {
        return getObject(columns.getIndexOf(name));
    }

    @Override public <T> T get(String name, Class<T> targetClass) {
        return get(columns.getIndexOf(name), targetClass);
    }

    @Override public <T> T get(String name, TypeToken<T> targetType) {
        return get(columns.getIndexOf(name), targetType);
    }

    @Override public <T> T get(String name, TypeCodec<T> codec) {
        return get(columns.getIndexOf(name), codec);
    }
}
