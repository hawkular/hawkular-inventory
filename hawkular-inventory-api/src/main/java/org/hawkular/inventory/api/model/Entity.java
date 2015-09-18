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
package org.hawkular.inventory.api.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all Hawkular entities.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public abstract class Entity<B extends Blueprint, U extends AbstractElement.Update> extends AbstractElement<B, U> {

    Entity() {
    }

    Entity(CanonicalPath path) {
        this(path, null);
    }

    Entity(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
        if (!this.getClass().equals(path.getSegment().getElementType())) {
            throw new IllegalArgumentException("Invalid path specified. Trying to create " +
                    this.getClass().getSimpleName() + " but the path points to " +
                    path.getSegment().getElementType().getSimpleName());
        }
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
        private final Map<String, Set<CanonicalPath>> outgoing;
        private final Map<String, Set<CanonicalPath>> incoming;

        protected Blueprint(String id, Map<String, Object> properties) {
            this(id, properties, null, null);
        }

        protected Blueprint(String id, Map<String, Object> properties, Map<String, Set<CanonicalPath>> outgoing,
                            Map<String, Set<CanonicalPath>> incoming) {

            super(properties);
            this.id = id;
            this.outgoing = outgoing == null ? Collections.emptyMap() : copyAsUnmodifiable(outgoing);
            this.incoming = incoming == null ? Collections.emptyMap() : copyAsUnmodifiable(incoming);
        }

        public String getId() {
            return id;
        }

        public Map<String, Set<CanonicalPath>> getOutgoingRelationships() {
            return outgoing;
        }

        public Map<String, Set<CanonicalPath>> getIncomingRelationships() {
            return incoming;
        }

        public abstract static class Builder<Blueprint, This extends Builder<Blueprint, This>>
                extends AbstractElement.Blueprint.Builder<Blueprint, This> {

            protected String id;
            protected Map<String, Set<CanonicalPath>> outgoing;
            protected Map<String, Set<CanonicalPath>> incoming;

            public This withId(String id) {
                this.id = id;
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
}
