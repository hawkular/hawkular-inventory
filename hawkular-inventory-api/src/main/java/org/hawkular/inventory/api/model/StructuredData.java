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
package org.hawkular.inventory.api.model;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Represents structured data. This is used to store configuration, operation params, etc.
 *
 * <p>Instances of structured data are built using a builder pattern starting with the {@link #get()} method.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
@ApiModel(value = "JSON", description = "Just a free form JSON.")
public final class StructuredData {

    public static final SegmentType SEGMENT_TYPE = SegmentType.sd;

    @ApiModelProperty(hidden = true)
    private final Serializable value;

    public static Builder get() {
        return new Builder();
    }

    private StructuredData(Serializable value) {
        this.value = value;
    }

    /**
     * Returns the value without much type information. If you need to specialize your behavior based on the
     * type of the value, consider using {@link #accept(Visitor, Object)} method.
     *
     * @return the value (possibly null for an undefined structured data)
     */
    @ApiModelProperty(hidden = true)
    public Serializable getValue() {
        return value;
    }

    /**
     * @return true, if this data represents no value
     */
    @ApiModelProperty(hidden = true)
    public boolean isUndefined() {
        return value == null;
    }

    @SuppressWarnings("unchecked")
    public <R, P> R accept(Visitor<R, P> visitor, P parameter) {
        if (value == null) {
            return visitor.visitUndefined(parameter);
        } else if (value instanceof java.util.List) {
            return visitor.visitList((List<StructuredData>) value, parameter);
        } else if (value instanceof java.util.Map) {
            return visitor.visitMap((java.util.Map<String, StructuredData>) value, parameter);
        } else if (value instanceof Boolean) {
            return visitor.visitBool((Boolean) value, parameter);
        } else if (value instanceof Long) {
            return visitor.visitIntegral((Long) value, parameter);
        } else if (value instanceof Double) {
            return visitor.visitFloatingPoint((Double) value, parameter);
        } else if (value instanceof String) {
            return visitor.visitString((String) value, parameter);
        } else {
            return visitor.visitUnknown(value, parameter);
        }
    }

    /**
     * @return the value as a boolean
     * @throws NullPointerException if the value is undefined
     * @throws ClassCastException   if the value is not a boolean
     */
    public boolean bool() {
        return (Boolean) value;
    }

    /**
     * @return the value as a long
     * @throws NullPointerException if the value is undefined
     * @throws ClassCastException   if the value is not integral
     */
    public long integral() {
        return (Long) value;
    }

    /**
     * @return the value as a double
     * @throws NullPointerException if the value is undefined
     * @throws ClassCastException   if the value is not a floating point number
     */
    public double floatingPoint() {
        return (Double) value;
    }

    /**
     * @return the value as a string
     * @throws NullPointerException if the value is undefined
     * @throws ClassCastException   if the value is not a string
     */
    public String string() {
        if (value == null) {
            throw new NullPointerException();
        }
        return (String) value;
    }

    /**
     * @return the value as a list
     * @throws NullPointerException if the value is undefined
     * @throws ClassCastException   if the value is not a list
     */
    @SuppressWarnings("unchecked")
    public List<StructuredData> list() {
        if (value == null) {
            throw new NullPointerException();
        }
        return (List<StructuredData>) value;
    }

    /**
     * @return the value as a list
     * @throws NullPointerException if the value is undefined
     * @throws ClassCastException   if the value is not a map
     */
    @SuppressWarnings("unchecked")
    public Map<String, StructuredData> map() {
        if (value == null) {
            throw new NullPointerException();
        }
        return (Map<String, StructuredData>) value;
    }

    @ApiModelProperty(hidden = true)
    public Type getType() {
        return this.accept(new Visitor<Type, Void>() {
            @Override
            public Type visitBool(boolean value, Void parameter) {
                return Type.bool;
            }

            @Override
            public Type visitIntegral(long value, Void parameter) {
                return Type.integral;
            }

            @Override
            public Type visitFloatingPoint(double value, Void parameter) {
                return Type.floatingPoint;
            }

            @Override
            public Type visitString(String value, Void parameter) {
                return Type.string;
            }

            @Override
            public Type visitUndefined(Void parameter) {
                return Type.undefined;
            }

            @Override
            public Type visitList(List<StructuredData> value, Void parameter) {
                return Type.list;
            }

            @Override
            public Type visitMap(Map<String, StructuredData> value, Void parameter) {
                return Type.map;
            }

            @Override
            public Type visitUnknown(Serializable value, Void parameter) {
                throw new AssertionError("Inconsistent StructuredData implementation -" +
                        " cannot determine type of value: " + value);
            }
        }, null);
    }

    /**
     * This instance WILL NOT be modified, the updates will be present in the newly constructed instance.
     *
     * @return an updater object to create modification of the this instance
     */
    public Updater update() {
        return new Updater(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructuredData)) return false;

        StructuredData that = (StructuredData) o;

        return Objects.equals(value, that.value);

    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        if (value == null) {
            return "undefined";
        } else {
            return value.toString();
        }
    }

    public String toJSON() {
        StringWriter wrt = new StringWriter();
        try {
            writeJSON(wrt);
            return wrt.toString();
        } catch (IOException e) {
            throw new AssertionError("IOException while writing to a StringWriter. This should never happen.", e);
        }
    }

    private static final class TransferIOException extends RuntimeException {
        public TransferIOException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }

    public void writeJSON(Appendable wrt) throws IOException {
        try {
            accept(new Visitor<Void, Void>() {
                @Override
                public Void visitBool(boolean value, Void parameter) {
                    try {
                        wrt.append(value ? "true" : "false");
                        return null;
                    } catch (IOException e) {
                        throw new TransferIOException(e);
                    }
                }

                @Override
                public Void visitIntegral(long value, Void parameter) {
                    try {
                        wrt.append(Long.toString(value));
                        return null;
                    } catch (IOException e) {
                        throw new TransferIOException(e);
                    }
                }

                @Override
                public Void visitFloatingPoint(double value, Void parameter) {
                    try {
                        wrt.append(Double.toString(value));
                        return null;
                    } catch (IOException e) {
                        throw new TransferIOException(e);
                    }
                }

                @Override
                public Void visitString(String value, Void parameter) {
                    try {
                        wrt.append('"').append(value).append('"');
                        return null;
                    } catch (IOException e) {
                        throw new TransferIOException(e);
                    }
                }

                @Override
                public Void visitUndefined(Void parameter) {
                    try {
                        wrt.append("null");
                        return null;
                    } catch (IOException e) {
                        throw new TransferIOException(e);
                    }
                }

                @Override
                public Void visitList(List<StructuredData> value, Void parameter) {
                    try {
                        wrt.append("[");
                        Iterator<StructuredData> it = value.iterator();
                        if (it.hasNext()) {
                            it.next().accept(this, parameter);
                        }

                        while (it.hasNext()) {
                            wrt.append(",");
                            it.next().accept(this, parameter);
                        }
                        wrt.append("]");
                        return null;
                    } catch (IOException e) {
                        throw new TransferIOException(e);
                    }
                }

                @Override
                public Void visitMap(Map<String, StructuredData> value, Void parameter) {
                    try {
                        BiFunction<String, Map.Entry<String, StructuredData>, Void> appender = (prefix, entry) -> {
                            String key = entry.getKey();
                            StructuredData d = entry.getValue();

                            try {
                                wrt.append(prefix).append('"').append(key).append("\":");
                            } catch (IOException e) {
                                throw new TransferIOException(e);
                            }
                            d.accept(this, parameter);

                            return null;
                        };

                        SortedMap<String, StructuredData> sorted = new TreeMap<>(value);

                        wrt.append("{");
                        Iterator<Map.Entry<String, StructuredData>> it = sorted.entrySet().iterator();
                        if (it.hasNext()) {
                            appender.apply("", it.next());
                        }

                        while (it.hasNext()) {
                            appender.apply(",", it.next());
                        }

                        wrt.append("}");
                        return null;
                    } catch (IOException e) {
                        throw new TransferIOException(e);
                    }
                }

                @Override
                public Void visitUnknown(Serializable value, Void parameter) {
                    return null;
                }
            }, null);
        } catch (TransferIOException e) {
            throw e.getCause();
        }
    }

    public enum Type {
        bool, integral, floatingPoint, string, undefined, list, map
    }

    public interface Visitor<R, P> {

        static <R, P> Visitor<R, P> bool(BiFunction<Boolean, P, R> handler) {
            return new VisitorSpecialization<>(Boolean.class, handler);
        }

        static <R, P> Visitor<R, P> integral(BiFunction<Long, P, R> handler) {
            return new VisitorSpecialization<>(Long.class, handler);
        }

        static <R, P> Visitor<R, P> floatingPoint(BiFunction<Double, P, R> handler) {
            return new VisitorSpecialization<>(Double.class, handler);
        }

        static <R, P> Visitor<R, P> string(BiFunction<String, P, R> handler) {
            return new VisitorSpecialization<>(String.class, handler);
        }

        static <R, P> Visitor<R, P> undefined(Function<P, R> handler) {
            return new VisitorSpecialization<>(Void.class, (v, p) -> handler.apply(p));
        }

        static <R, P> Visitor<R, P> list(BiFunction<List<StructuredData>, P, R> handler) {
            return new VisitorSpecialization<>(List.class, handler);
        }

        static <R, P> Visitor<R, P> map(BiFunction<Map<String, StructuredData>, P, R> handler) {
            return new VisitorSpecialization<>(Map.class, handler);
        }

        R visitBool(boolean value, P parameter);

        R visitIntegral(long value, P parameter);

        R visitFloatingPoint(double value, P parameter);

        R visitString(String value, P parameter);

        R visitUndefined(P parameter);

        R visitList(List<StructuredData> value, P parameter);

        R visitMap(Map<String, StructuredData> value, P parameter);

        R visitUnknown(Serializable value, P parameter);

        class Simple<R, P> implements Visitor<R, P> {

            protected R defaultAction(Serializable value, P parameter) {
                return null;
            }

            @Override
            public R visitBool(boolean value, P parameter) {
                return defaultAction(value, parameter);
            }

            @Override
            public R visitIntegral(long value, P parameter) {
                return defaultAction(value, parameter);
            }

            @Override
            public R visitFloatingPoint(double value, P parameter) {
                return defaultAction(value, parameter);
            }

            @Override
            public R visitString(String value, P parameter) {
                return defaultAction(value, parameter);
            }

            @Override
            public R visitUndefined(P parameter) {
                return defaultAction(null, parameter);
            }

            @Override
            public R visitUnknown(Serializable value, P parameter) {
                return defaultAction(value, parameter);
            }

            @Override
            public R visitList(List<StructuredData> value, P parameter) {
                return defaultAction((Serializable) value, parameter);
            }

            @Override
            public R visitMap(Map<String, StructuredData> value, P parameter) {
                return defaultAction((Serializable) value, parameter);
            }
        }
    }

    private static class VisitorSpecialization<R, P, T> extends Visitor.Simple<R, P> {
        private final Class<?> expectedType;
        private final BiFunction<T, P, R> handler;


        VisitorSpecialization(Class<?> expectedType, BiFunction<T, P, R> handler) {
            this.expectedType = expectedType;
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected R defaultAction(Serializable value, P parameter) {
            if (value == null && expectedType != Void.class) {
                throw new IllegalArgumentException("Expected a value of type " + expectedType.getSimpleName()
                        + " but got undefined");
            } else if (value != null && !expectedType.isAssignableFrom(value.getClass())) {
                throw new IllegalArgumentException("Expected a value of type " + expectedType.getSimpleName()
                        + " but got " + value.getClass().getSimpleName());
            } else {
                return handler.apply((T) value, parameter);
            }
        }
    }

    public static final class Builder {
        private Builder() {
        }

        public StructuredData bool(Boolean value) {
            return new StructuredData(value);
        }

        public StructuredData integral(Long value) {
            return new StructuredData(value);
        }

        public StructuredData floatingPoint(Double value) {
            return new StructuredData(value);
        }

        public StructuredData string(String value) {
            return new StructuredData(value);
        }

        public StructuredData undefined() {
            return new StructuredData(null);
        }

        public ListBuilder list() {
            return new ListBuilder();
        }

        public MapBuilder map() {
            return new MapBuilder();
        }
    }

    public static final class ListBuilder extends AbstractListBuilder<ListBuilder> {
        public ListBuilder() {
            super(new ArrayList<>());
        }

        public StructuredData build() {
            return new StructuredData(list);
        }
    }

    public static final class MapBuilder extends AbstractMapBuilder<MapBuilder> {
        public MapBuilder() {
            super(new LinkedHashMap<>());
        }

        public StructuredData build() {
            return new StructuredData(map);
        }
    }

    public abstract static class AbstractHierarchyBuilder {
        protected abstract void apply(Object context, StructuredData value);

        @SuppressWarnings("unchecked")
        protected <T> T castThis() {
            return (T) this;
        }
    }

    public abstract static class AbstractListBuilder<This extends AbstractListBuilder<This>>
            extends AbstractHierarchyBuilder {

        protected final ArrayList<StructuredData> list;

        AbstractListBuilder(ArrayList<StructuredData> list) {
            this.list = list;
        }

        public This addBool(boolean value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This addIntegral(long value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This addFloatingPoint(double value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This addString(String value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This addUndefined() {
            list.add(new StructuredData(null));
            return castThis();
        }

        public InnerListBuilder<This> addList() {
            return new InnerListBuilder<>(new ArrayList<>(), castThis(), null);
        }

        public InnerMapBuilder<This> addMap() {
            return new InnerMapBuilder<>(new LinkedHashMap<>(), castThis(), null);
        }

        @Override
        protected void apply(Object context, StructuredData value) {
            list.add(value);
        }
    }

    public static final class InnerListBuilder<Parent extends AbstractHierarchyBuilder>
            extends AbstractListBuilder<InnerListBuilder<Parent>> {

        private final Parent parentBuilder;
        private final Object parentContext;

        InnerListBuilder(ArrayList<StructuredData> list, Parent parentBuilder, Object parentContext) {
            super(list);
            this.parentBuilder = parentBuilder;
            this.parentContext = parentContext;
        }

        public Parent closeList() {
            parentBuilder.apply(parentContext, new StructuredData((Serializable) Collections.unmodifiableList(list)));
            return parentBuilder;
        }
    }

    public abstract static class AbstractMapBuilder<This extends AbstractMapBuilder<This>>
            extends AbstractHierarchyBuilder {

        protected final LinkedHashMap<String, StructuredData> map;

        AbstractMapBuilder(LinkedHashMap<String, StructuredData> map) {
            this.map = map;
        }

        public This putBool(String key, boolean value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putIntegral(String key, long value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putFloatingPoint(String key, double value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putString(String key, String value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putUndefined(String key) {
            map.put(key, new StructuredData(null));
            return castThis();
        }

        public InnerListBuilder<This> putList(String key) {
            return new InnerListBuilder<>(new ArrayList<>(), castThis(), key);
        }

        public InnerMapBuilder<This> putMap(String key) {
            return new InnerMapBuilder<>(new LinkedHashMap<>(), castThis(), key);
        }

        @Override
        protected void apply(Object context, StructuredData value) {
            map.put((String) context, value);
        }
    }

    public static final class InnerMapBuilder<Parent extends AbstractHierarchyBuilder>
            extends AbstractMapBuilder<InnerMapBuilder<Parent>> {

        private final Parent parentBuilder;
        private final Object parentContext;

        InnerMapBuilder(LinkedHashMap<String, StructuredData> map, Parent parentBuilder, Object parentContext) {
            super(map);
            this.parentBuilder = parentBuilder;
            this.parentContext = parentContext;
        }

        public Parent closeMap() {
            parentBuilder.apply(parentContext, new StructuredData((Serializable) Collections.unmodifiableMap(map)));
            return parentBuilder;
        }
    }

    public static final class Updater {
        private final Serializable origValue;

        private Updater(Serializable origValue) {
            this.origValue = origValue;
        }

        public StructuredData toBool(boolean value) {
            return new StructuredData(value);
        }

        public StructuredData toIntegral(long value) {
            return new StructuredData(value);
        }

        public StructuredData toFloatingPoint(double value) {
            return new StructuredData(value);
        }

        public StructuredData toString(String value) {
            return new StructuredData(value);
        }

        public StructuredData toUndefined() {
            return new StructuredData(null);
        }

        @SuppressWarnings("unchecked")
        public ListUpdater toList() {
            if (origValue instanceof List) {
                return new ListUpdater(new ArrayList<>((List<StructuredData>) origValue));
            } else {
                return new ListUpdater(new ArrayList<>());
            }
        }

        @SuppressWarnings("unchecked")
        public MapUpdater toMap() {
            if (origValue instanceof Map) {
                return new MapUpdater(new LinkedHashMap<>((Map<String, StructuredData>) origValue));
            } else {
                return new MapUpdater(new LinkedHashMap<>());
            }
        }
    }

    public abstract static class AbstractListUpdater<This extends AbstractListUpdater<This>>
            extends AbstractHierarchyBuilder {
        protected final ArrayList<StructuredData> list;

        private AbstractListUpdater(ArrayList<StructuredData> list) {
            this.list = list;
        }

        public This clear() {
            list.clear();
            return castThis();
        }

        public This remove(int index) {
            list.remove(index);
            return castThis();
        }

        public This addBool(boolean value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This setBool(int index, boolean value) {
            list.set(index, new StructuredData(value));
            return castThis();
        }

        public This addIntegral(long value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This setIntegral(int index, long value) {
            list.set(index, new StructuredData(value));
            return castThis();
        }

        public This addFloatingPoint(double value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This setFloatingPoint(int index, double value) {
            list.set(index, new StructuredData(value));
            return castThis();
        }

        public This addString(String value) {
            list.add(new StructuredData(value));
            return castThis();
        }

        public This setString(int index, String value) {
            list.set(index, new StructuredData(value));
            return castThis();
        }

        public This addUndefined() {
            list.add(new StructuredData(null));
            return castThis();
        }

        public This setUndefined(int index) {
            list.set(index, new StructuredData(null));
            return castThis();
        }

        public InnerListUpdater<This> addList() {
            return new InnerListUpdater<>(new ArrayList<>(), castThis(), null);
        }

        public InnerListUpdater<This> updateList(int index) {
            @SuppressWarnings("unchecked")
            ArrayList<StructuredData> list = this.list.get(index).value instanceof List
                    ? new ArrayList<>((List<StructuredData>) this.list.get(index).value)
                    : new ArrayList<>();

            return new InnerListUpdater<>(list, castThis(), index);
        }

        public InnerMapUpdater<This> addMap() {
            return new InnerMapUpdater<>(new LinkedHashMap<>(), castThis(), null);
        }

        public InnerMapUpdater<This> updateMap(int index) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, StructuredData> map = this.list.get(index).value instanceof Map
                    ? new LinkedHashMap<>((Map<String, StructuredData>) this.list.get(index).value)
                    : new LinkedHashMap<>();

            return new InnerMapUpdater<>(map, castThis(), index);
        }

        @Override
        protected void apply(Object context, StructuredData value) {
            Integer ctx = (Integer) context;

            if (ctx == null) {
                list.add(value);
            } else {
                list.set(ctx, value);
            }
        }
    }

    private abstract static class AbstractMapUpdater<This extends AbstractMapUpdater<This>>
            extends AbstractHierarchyBuilder {

        protected final LinkedHashMap<String, StructuredData> map;

        private AbstractMapUpdater(LinkedHashMap<String, StructuredData> map) {
            this.map = map;
        }

        public This clear() {
            this.map.clear();
            return castThis();
        }

        public This remove(String key) {
            this.map.remove(key);
            return castThis();
        }

        public This putBool(String key, boolean value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putIntegral(String key, long value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putFloatingPoint(String key, double value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putString(String key, String value) {
            map.put(key, new StructuredData(value));
            return castThis();
        }

        public This putUndefined(String key) {
            map.put(key, new StructuredData(null));
            return castThis();
        }

        public InnerListUpdater<This> updateList(String key) {
            @SuppressWarnings("unchecked")
            ArrayList<StructuredData> list = this.map.get(key).value instanceof List
                    ? new ArrayList<>((List<StructuredData>) this.map.get(key).value)
                    : new ArrayList<>();

            return new InnerListUpdater<>(list, castThis(), key);
        }

        public InnerMapUpdater<This> updateMap(String key) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, StructuredData> map =
                    null != this.map.get(key) && this.map.get(key).value instanceof Map
                    ? new LinkedHashMap<>((Map<String, StructuredData>) this.map.get(key).value)
                    : new LinkedHashMap<>();

            return new InnerMapUpdater<>(map, castThis(), key);
        }

        @Override
        protected void apply(Object context, StructuredData value) {
            map.put((String) context, value);
        }
    }

    public static final class ListUpdater extends AbstractListUpdater<ListUpdater> {
        public ListUpdater(ArrayList<StructuredData> list) {
            super(list);
        }

        public StructuredData build() {
            return new StructuredData((Serializable) Collections.unmodifiableList(list));
        }
    }

    public static final class MapUpdater extends AbstractMapUpdater<MapUpdater> {
        public MapUpdater(LinkedHashMap<String, StructuredData> map) {
            super(map);
        }

        public StructuredData build() {
            return new StructuredData((Serializable) Collections.unmodifiableMap(map));
        }
    }

    public static final class InnerListUpdater<Parent extends AbstractHierarchyBuilder>
            extends AbstractListUpdater<InnerListUpdater<Parent>> {

        private final Parent parent;
        private final Object context;

        public InnerListUpdater(ArrayList<StructuredData> list, Parent parent, Object context) {
            super(list);
            this.parent = parent;
            this.context = context;
        }

        public Parent closeList() {
            parent.apply(context, new StructuredData((Serializable) Collections.unmodifiableList(list)));
            return parent;
        }
    }

    public static final class InnerMapUpdater<Parent extends AbstractHierarchyBuilder>
            extends AbstractMapUpdater<InnerMapUpdater<Parent>> {

        private final Parent parent;
        private final Object context;

        public InnerMapUpdater(LinkedHashMap<String, StructuredData> map, Parent parent, Object context) {
            super(map);
            this.parent = parent;
            this.context = context;
        }

        public Parent closeMap() {
            parent.apply(context, new StructuredData((Serializable) Collections.unmodifiableMap(map)));
            return parent;
        }
    }
}
