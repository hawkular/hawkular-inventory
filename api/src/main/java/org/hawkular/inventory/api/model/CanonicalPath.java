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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A path represents the canonical traversal to an element through the inventory graph. The canonical traversal
 * always starts at a tenant and follows only the "contains" relationships down to the entity in question. For
 * relationships the "traversal" comprises of merely referencing the relationship by its id.
 *
 * <p>For description of the basic behavior and serialized form of the path, please consult {@link AbstractPath}.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class CanonicalPath extends AbstractPath<CanonicalPath> implements Iterable<CanonicalPath>, Serializable {

    public static final Map<String, Class<?>> SHORT_NAME_TYPES = new HashMap<>();
    public static final Map<Class<?>, String> SHORT_TYPE_NAMES = new HashMap<>();
    public static final Map<Class<?>, List<Class<?>>> VALID_PROGRESSIONS = new HashMap<>();

    static {
        SHORT_NAME_TYPES.put("t", Tenant.class);
        SHORT_NAME_TYPES.put("e", Environment.class);
        SHORT_NAME_TYPES.put("f", Feed.class);
        SHORT_NAME_TYPES.put("m", Metric.class);
        SHORT_NAME_TYPES.put("r", Resource.class);
        SHORT_NAME_TYPES.put("rt", ResourceType.class);
        SHORT_NAME_TYPES.put("mt", MetricType.class);
        SHORT_NAME_TYPES.put("rl", Relationship.class);

        SHORT_TYPE_NAMES.put(Tenant.class, "t");
        SHORT_TYPE_NAMES.put(Environment.class, "e");
        SHORT_TYPE_NAMES.put(Feed.class, "f");
        SHORT_TYPE_NAMES.put(Metric.class, "m");
        SHORT_TYPE_NAMES.put(Resource.class, "r");
        SHORT_TYPE_NAMES.put(ResourceType.class, "rt");
        SHORT_TYPE_NAMES.put(MetricType.class, "mt");
        SHORT_TYPE_NAMES.put(Relationship.class, "rl");

        VALID_PROGRESSIONS.put(Tenant.class, Arrays.asList(Environment.class, MetricType.class, ResourceType.class));
        VALID_PROGRESSIONS.put(Environment.class, Arrays.asList(Metric.class, Resource.class, Feed.class));
        VALID_PROGRESSIONS.put(Feed.class, Arrays.asList(Metric.class, Resource.class));
        VALID_PROGRESSIONS.put(Resource.class, Collections.singletonList(Resource.class));
        VALID_PROGRESSIONS.put(null, Arrays.asList(Tenant.class, Relationship.class));
    }

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private CanonicalPath() {
    }

    CanonicalPath(int myIdx, List<Segment> path) {
        super(0, myIdx, path, CanonicalPath::new);
    }

    /**
     * this is here only to cooperate nicely with AbstractPath
     */
    CanonicalPath(int startIdx, int myIdx, List<Segment> path) {
        this(myIdx, path);
    }

    /**
     * @return an empty canonical path to be extended
     */
    public static Extender<CanonicalPath> empty() {
        return new Extender<>(0, new ArrayList<>(), VALID_PROGRESSIONS, CanonicalPath::new);
    }

    /**
     * @return a new path builder
     */
    public static Builder<CanonicalPath> of() {
        return new Builder<>(new ArrayList<>(), CanonicalPath::new);
    }

    /**
     * Creates a new path instance from the "serialized" slash-separated representation.
     *
     * <p>The escape character is {@code '\'} and special characters are {@code '\'}, {@code '|'} and {@code '/'}.
     *
     * @param path the string representation of the path
     * @return a new path instance
     */
    @JsonCreator
    public static CanonicalPath fromString(String path) {
        return AbstractPath.fromString(path, VALID_PROGRESSIONS, SHORT_NAME_TYPES, (x) -> true, CanonicalPath::new);
    }

    public <R, P> R accept(ElementTypeVisitor<R, P> visitor, P parameter) {
        return getSegment().accept(visitor, parameter);
    }

    /**
     * @return The path to the root resource as known to this path instance.
     */
    public CanonicalPath getRoot() {
        return new CanonicalPath(1, path);
    }

    /**
     * If this path was created by going {@link #up() up} from another path, then this returns the bottom-most path
     * in such chain.
     *
     * @return the bottom-most path in the shared chain
     */
    public CanonicalPath getLeaf() {
        return new CanonicalPath(path.size(), path);
    }

    /**
     * Creates a new path by appending the provided segment to the current path. The returned path does NOT share
     * the chain with the current path anymore.
     *
     * <p>The returned path will be the leaf path of the new path chain created by obtaining the segments of the
     * current path using {@link #getPath()} and appending the new segment to it.
     *
     * <p> It is checked that the new path is a valid canonical path. I.e. you cannot add a tenant segment under
     * a resource, etc.
     *
     * @param type the type of the entity to append
     * @param id the id of the appended entity
     * @return a new path instance
     * @throws IllegalArgumentException if adding the provided segment would create an invalid canonical path
     */
    public Extender<CanonicalPath> extend(Class<?> type, String id) {
        Extender<CanonicalPath> ret = new Extender<>(startIdx, new ArrayList<>(getPath()), VALID_PROGRESSIONS,
                CanonicalPath::new);
        return ret.extend(new Segment(type, id));
    }

    /**
     * The path contains ids of different entities. I.e. a path to environment always contains the id of the tenant
     * that the environment belongs to.
     *
     * <p>Using the returned extractor, one can obtain the ids of such different entities easily.
     *
     * @return the extractor object to use for extracting ids of different entity types from the path.
     */
    public IdExtractor ids() {
        return new IdExtractor();
    }

    @Override
    @JsonValue
    public String toString() {
        return new Encoder(SHORT_TYPE_NAMES).encode(this);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        constructor = CanonicalPath::new;
    }

    public final class IdExtractor {

        public String getTenantId() {
            return idIfTypeCorrect(getRoot(), Tenant.class);
        }

        public String getEnvironmentId() {
            return idIfTypeCorrect(getRoot().down(), Environment.class);
        }

        public String getMetricTypeId() {
            return idIfTypeCorrect(getRoot().down(), MetricType.class);
        }

        public String getResourceTypeId() {
            return idIfTypeCorrect(getRoot().down(), ResourceType.class);
        }

        public String getFeedId() {
            return idIfTypeCorrect(getRoot().down(2), Feed.class);
        }

        public String getResourceId() {
            String id = idIfTypeCorrect(getRoot().down(2), Resource.class);

            return id != null ? id : idIfTypeCorrect(getRoot().down(3), Resource.class);
        }

        public String getMetricId() {
            String id = idIfTypeCorrect(getRoot().down(2), Metric.class);

            return id != null ? id : idIfTypeCorrect(getRoot().down(3), Metric.class);
        }

        public String getRelationshipId() {
            return idIfTypeCorrect(getRoot(), Relationship.class);
        }

        private String idIfTypeCorrect(CanonicalPath path, Class<? extends AbstractElement<?, ?>> desiredType) {
            if (path.isDefined() && path.getSegment().getElementType().equals(desiredType)) {
                return path.getSegment().getElementId();
            } else {
                return null;
            }
        }
    }
}
