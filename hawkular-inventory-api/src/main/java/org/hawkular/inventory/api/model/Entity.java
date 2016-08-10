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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;

/**
 * Base class for all Hawkular entities.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
@ApiModel(description = "Defines the basic properties of all entity types in inventory",
        subTypes = {Environment.class, SyncedEntity.class, MetadataPack.class, Tenant.class})
public abstract class Entity<B extends Blueprint, U extends Entity.Update> extends AbstractElement<B, U> {

    public static Class<?> typeFromSegmentType(SegmentType segmentType) {
        switch (segmentType) {
            case up:
                return RelativePath.Up.class;
            default:
                return entityTypeFromSegmentType(segmentType);
        }
    }

    public static Class<? extends Entity<? extends org.hawkular.inventory.api.model.Blueprint, ?>>
    entityTypeFromSegmentType(SegmentType segmentType) {
        switch (segmentType) {
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
            // case rl:
            //    return Relationship.class;
            case d:
                return DataEntity.class;
            case ot:
                return OperationType.class;
            case mp:
                return MetadataPack.class;
            default:
                throw new IllegalStateException("There is no " + Entity.class.getName() + " type for " +
                        segmentType.getClass().getName() + " '" + segmentType.name() + "'");
        }
    }

    private final String name;

    Entity() {
        this.name = null;
    }

    Entity(CanonicalPath path) {
        this(path, null);
    }

    Entity(CanonicalPath path, Map<String, Object> properties) {
        this(null, path, properties);
    }

    public Entity(String name, CanonicalPath path) {
        this(name, path, null);
    }

    /**
     * Constructs a new entity
     *
     * @param name       the human readable name of the entity, can be null
     * @param path       the path of the entity, must not be null
     * @param properties the additional user-defined properties, can be null
     */
    Entity(String name, CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
        this.name = name;
        if (!segmentTypeFromType(this.getClass()).equals(path.getSegment().getElementType())) {
            throw new IllegalArgumentException("Invalid path specified. Trying to create " +
                    this.getClass().getSimpleName() + " but the path points to " +
                    path.getSegment().getElementType().getSimpleName());
        }
    }

    /**
     * @return The human readable name of the entity, can be null
     */
    public String getName() {
        return name;
    }

    /**
     * Use this to append additional information to the string representation of this instance
     * returned from the (final) {@link #toString()}.
     *
     * <p>Generally, one should call the super method first and then only add additional information
     * to the builder.
     *
     * @param toStringBuilder the builder to append stuff to.
     */
    protected void appendToString(StringBuilder toStringBuilder) {

    }

    @Override
    public final String toString() {
        StringBuilder bld = new StringBuilder(getClass().getSimpleName());
        bld.append("[path='").append(getPath()).append('\'');
        appendToString(bld);
        bld.append(']');

        return bld.toString();
    }

    /**
     * Base class for the blueprint types of concrete subclasses. Note that while it will usually fit the purpose,
     * the subclasses are free to use a blueprint type not inheriting from this one.
     */
    public abstract static class Blueprint extends AbstractElement.Blueprint {
        private final String id;
        private final String name;
        private final Map<String, Set<CanonicalPath>> outgoing;
        private final Map<String, Set<CanonicalPath>> incoming;

        /**
         * This no-arg constructor is provided for the needs of Jackson deserialization. The instance constructed using
         * it is semantically invalid and needs further processing (by reflection) to be correct. This is what Jackson
         * does but should not be relied upon in other circumstances.
         * <p>
         * Do <b>NOT</b> make this public in subclasses, rather make the no-arg constructor actually private in
         * final subclasses to stress this point even further.
         * <p>
         * Any constructor with arguments should <b>NOT</b> call this, because it will leave mandatory properties
         * uninitialized. Use one of the other provided constructors for initializing instances with arguments.
         */
        protected Blueprint() {
            super(null);
            this.id = null;
            this.name = null;
            this.outgoing = null;
            this.incoming = null;
        }

        protected Blueprint(String id, Map<String, Object> properties) {
            this(id, properties, null, null);
        }

        protected Blueprint(String id, Map<String, Object> properties, Map<String, Set<CanonicalPath>> outgoing,
                            Map<String, Set<CanonicalPath>> incoming) {

            this(id, null, properties, outgoing, incoming);
        }

        protected Blueprint(String id, String name, Map<String, Object> properties) {
            this(id, name, properties, null, null);
        }

        protected Blueprint(String id, String name, Map<String, Object> properties, Map<String,
                Set<CanonicalPath>> outgoing, Map<String, Set<CanonicalPath>> incoming) {

            super(properties);
            if (id == null) {
                throw new IllegalArgumentException("id == null");
            }
            this.id = id;
            this.name = name;
            this.outgoing = outgoing == null ? Collections.emptyMap() : copyAsUnmodifiable(outgoing);
            this.incoming = incoming == null ? Collections.emptyMap() : copyAsUnmodifiable(incoming);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Map<String, Set<CanonicalPath>> getOutgoingRelationships() {
            return outgoing == null ? Collections.emptyMap() : outgoing;
        }

        public Map<String, Set<CanonicalPath>> getIncomingRelationships() {
            return incoming == null ? Collections.emptyMap() : incoming;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Blueprint blueprint = (Blueprint) o;

            return id.equals(blueprint.id);
        }

        @Override public int hashCode() {
            return id.hashCode();
        }

        public abstract static class Builder<Blueprint, This extends Builder<Blueprint, This>>
                extends AbstractElement.Blueprint.Builder<Blueprint, This> {

            protected String id;
            protected String name;
            protected Map<String, Set<CanonicalPath>> outgoing;
            protected Map<String, Set<CanonicalPath>> incoming;

            public This withId(String id) {
                this.id = id;
                return castThis();
            }

            public This withName(String name) {
                this.name = name;
                return castThis();
            }

            public This withOutgoingRelationships(Map<String, Set<CanonicalPath>> outgoing) {
                this.outgoing = outgoing;
                return castThis();
            }

            public This withIncomingRelationships(Map<String, Set<CanonicalPath>> incoming) {
                this.incoming = incoming;
                return castThis();
            }

            public This addOutgoingRelationship(String label, CanonicalPath target) {
                outgoing = addRelationship(outgoing, label, target);
                return castThis();
            }

            public This addIncomingRelationship(String label, CanonicalPath source) {
                incoming = addRelationship(incoming, label, source);
                return castThis();
            }

            private Map<String, Set<CanonicalPath>> addRelationship(Map<String, Set<CanonicalPath>> map, String label,
                                                                    CanonicalPath path) {
                if (map == null) {
                    map = new HashMap<>();
                }

                Set<CanonicalPath> paths = map.get(label);
                if (paths == null) {
                    paths = new HashSet<>();
                    map.put(label, paths);
                }

                paths.add(path);

                return map;
            }
        }

        private static Map<String, Set<CanonicalPath>> copyAsUnmodifiable(Map<String, Set<CanonicalPath>> map) {
            Map<String, Set<CanonicalPath>> ret = new HashMap<>(map.size());
            map.forEach((k, v) -> ret.put(k, Collections.unmodifiableSet(v)));
            return Collections.unmodifiableMap(ret);
        }
    }

    public abstract static class Update extends AbstractElement.Update {

        protected final String name;

        protected Update(String name, Map<String, Object> properties) {
            super(properties);
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public abstract static class Builder<U extends Update, This extends Builder<U, This>>
                extends AbstractElement.Update.Builder<U, This> {

            protected String name;

            public This withName(String name) {
                this.name = name;
                return castThis();
            }
        }
    }
}
