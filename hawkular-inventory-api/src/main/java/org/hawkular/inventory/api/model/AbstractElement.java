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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * A common super class of both entities and relationships.
 *
 * @param <B> the blueprint class. The blueprint is used to create a new element.
 * @param <U> the update class. The update class is used to update the element.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public abstract class AbstractElement<B extends org.hawkular.inventory.api.model.Blueprint,
        U extends AbstractElement.Update> {
    public static final String ID_PROPERTY = "id";

    private final CanonicalPath path;

    protected final Map<String, Object> properties;

    /**
     * This should be used only in extreme cases where {@link SegmentType} is not possible.
     * @param elementType the type of the element
     * @return the class representing the element type
     */
    public static Class<? extends AbstractElement<?, ?>> toElementClass(SegmentType elementType) {
        switch (elementType) {
            case t:
                return Tenant.class;
            case e:
                return Environment.class;
            case f:
                return Feed.class;
            case m:
                return Metric.class;
            case mt:
                return MetricType.class;
            case r:
                return Resource.class;
            case rt:
                return ResourceType.class;
             case rl:
                return Relationship.class;
            case d:
                return DataEntity.class;
            case ot:
                return OperationType.class;
            case mp:
                return MetadataPack.class;
            default:
                throw new IllegalStateException("There is no " + Entity.class.getName() + " type for " +
                        elementType.getClass().getName() + " '" + elementType.name() + "'");
        }
    }

    //JAXB support
    AbstractElement() {
        properties = null;
        path = null;
    }

    AbstractElement(CanonicalPath path, Map<String, Object> properties) {
        if (properties == null) {
            this.properties = null;
        } else {
            this.properties = new HashMap<>(properties);
            this.properties.remove(ID_PROPERTY);
        }
        this.path = path;
    }

    /**
     * Returns the same result as {@link SegmentType#fromElementType(Class)} but provides a much better performance.
     *
     * @param cl the type to to map to a {@link SegmentType}
     * @return the {@link SegmentType} corresponding to the given {@code cl}
     * @throws IllegalStateException if there is no {@link SegmentType} corresponding to the given {@code cl}
     */
    public static SegmentType segmentTypeFromType(Class<?> cl) {
        if (Tenant.class.equals(cl)) {
            return Tenant.SEGMENT_TYPE;
        } else if (Environment.class.equals(cl)) {
            return Environment.SEGMENT_TYPE;
        } else if (Feed.class.equals(cl)) {
            return Feed.SEGMENT_TYPE;
        } else if (Metric.class.equals(cl)) {
            return Metric.SEGMENT_TYPE;
        } else if (MetricType.class.equals(cl)) {
            return MetricType.SEGMENT_TYPE;
        } else if (Resource.class.equals(cl)) {
            return Resource.SEGMENT_TYPE;
        } else if (ResourceType.class.equals(cl)) {
            return ResourceType.SEGMENT_TYPE;
        } else if (DataEntity.class.equals(cl)) {
            return DataEntity.SEGMENT_TYPE;
        } else if (OperationType.class.equals(cl)) {
            return OperationType.SEGMENT_TYPE;
        } else if (MetadataPack.class.equals(cl)) {
            return MetadataPack.SEGMENT_TYPE;
        } else if (Relationship.class.equals(cl)) {
            return Relationship.SEGMENT_TYPE;
        } else if (StructuredData.class.equals(cl)) {
            return StructuredData.SEGMENT_TYPE;
        } else if (RelativePath.Up.class.equals(cl)) {
            return RelativePath.Up.SEGMENT_TYPE;
        } else {
            throw new IllegalStateException("There is no " + SegmentType.class.getName() + " for type " +
                    (cl == null ? "null" : cl.getName()));
        }
    }

    /**
     * Accepts the provided visitor.
     *
     * @param visitor   the visitor to visit this entity
     * @param parameter the parameter to pass on to the visitor
     * @param <R>       the return type
     * @param <P>       the type of the parameter
     * @return the return value provided by the visitor
     */
    public abstract <R, P> R accept(ElementVisitor<R, P> visitor, P parameter);

    /**
     * @return canonical path to the element
     */
    public CanonicalPath getPath() {
        return path;
    }

    /**
     * @return the id of the element.
     */
    public String getId() {
        return path.getSegment().getElementId();
    }

    /**
     * @return a map of arbitrary properties of this entity.
     */
    public Map<String, Object> getProperties() {
        if (properties == null) {
            return Collections.emptyMap();
        }
        return properties;
    }

    protected static <T> T valueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * @return a new updater object to modify this entity and produce a new one.
     */
    //if only Java had "Self" type like Rust :(
    public abstract Updater<U, ? extends AbstractElement<?, U>> update();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractElement<?, ?> entity = (AbstractElement<?, ?>) o;

        return path.equals(entity.path);
    }

    @Override
    public int hashCode() {
        return getPath().hashCode();
    }

    public abstract static class Update {

        @SuppressWarnings("unchecked")
        static <U extends Update, E extends AbstractElement<?, U>> Class<? extends E> getEntityTypeOf(U update) {
            return (Class<? extends E>) (Class) update.getClass().getEnclosingClass();
        }

        private final Map<String, Object> properties;

        public Update(Map<String, Object> properties) {
            this.properties = properties;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public abstract <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter);

        public abstract static class Builder<U extends Update, This extends Builder<U, This>> {
            protected Map<String, Object> properties;

            private Map<String, Object> getProperties() {
                if (properties == null) {
                    properties = new HashMap<>();
                }

                return properties;
            }

            public This withProperty(String key, Object value) {
                getProperties().put(key, value);
                return castThis();
            }

            public This withProperties(Map<String, Object> properties) {
                getProperties().putAll(properties);
                return castThis();
            }

            public abstract U build();

            @SuppressWarnings("unchecked")
            protected This castThis() {
                return (This) this;
            }
        }
    }

    public static final class Updater<U extends Update, E extends AbstractElement<?, U>> {
        private final Function<U, E> updater;

        Updater(Function<U, E> updater) {
            this.updater = updater;
        }

        public E with(U update) {
            return updater.apply(update);
        }
    }

    public abstract static class Blueprint implements org.hawkular.inventory.api.model.Blueprint {
        private final Map<String, Object> properties;

        protected Blueprint(Map<String, Object> properties) {
            this.properties = properties;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public abstract static class Builder<B, This extends Builder<B, This>> {
            protected Map<String, Object> properties = new HashMap<>();

            public This withProperty(String key, Object value) {
                this.properties.put(key, value);
                return castThis();
            }

            public This withProperties(Map<String, Object> properties) {
                this.properties.putAll(properties);
                return castThis();
            }

            public abstract B build();

            @SuppressWarnings("unchecked")
            protected This castThis() {
                return (This) this;
            }
        }
    }
}
