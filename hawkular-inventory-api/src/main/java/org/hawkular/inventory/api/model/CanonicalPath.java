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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A path represents the canonical traversal to an element through the inventory graph. The canonical traversal
 * always starts at a tenant and follows only the "contains" relationships down to the entity in question. For
 * relationships the "traversal" comprises of merely referencing the relationship by its id.
 *
 * <p>For description of the basic behavior and serialized form of the path, please consult {@link Path}.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class CanonicalPath extends Path implements Iterable<CanonicalPath>, Serializable {

    static final Map<String, Class<?>> SHORT_NAME_TYPES = new HashMap<>();
    static final Map<Class<?>, String> SHORT_TYPE_NAMES = new HashMap<>();
    static final Map<Class<?>, List<Class<?>>> VALID_PROGRESSIONS = new HashMap<>();

    static {
        SHORT_NAME_TYPES.put("t", Tenant.class);
        SHORT_NAME_TYPES.put("e", Environment.class);
        SHORT_NAME_TYPES.put("f", Feed.class);
        SHORT_NAME_TYPES.put("m", Metric.class);
        SHORT_NAME_TYPES.put("r", Resource.class);
        SHORT_NAME_TYPES.put("rt", ResourceType.class);
        SHORT_NAME_TYPES.put("mt", MetricType.class);
        SHORT_NAME_TYPES.put("rl", Relationship.class);
        SHORT_NAME_TYPES.put("d", DataEntity.class);
        SHORT_NAME_TYPES.put("ot", OperationType.class);
        SHORT_NAME_TYPES.put("mp", MetadataPack.class);

        SHORT_TYPE_NAMES.put(Tenant.class, "t");
        SHORT_TYPE_NAMES.put(Environment.class, "e");
        SHORT_TYPE_NAMES.put(Feed.class, "f");
        SHORT_TYPE_NAMES.put(Metric.class, "m");
        SHORT_TYPE_NAMES.put(Resource.class, "r");
        SHORT_TYPE_NAMES.put(ResourceType.class, "rt");
        SHORT_TYPE_NAMES.put(MetricType.class, "mt");
        SHORT_TYPE_NAMES.put(Relationship.class, "rl");
        SHORT_TYPE_NAMES.put(DataEntity.class, "d");
        SHORT_TYPE_NAMES.put(OperationType.class, "ot");
        SHORT_TYPE_NAMES.put(MetadataPack.class, "mp");

        VALID_PROGRESSIONS.put(Tenant.class, Arrays.asList(Environment.class, MetricType.class, ResourceType.class,
                Feed.class, MetadataPack.class));
        VALID_PROGRESSIONS.put(Environment.class, Arrays.asList(Metric.class, Resource.class));
        VALID_PROGRESSIONS.put(Feed.class, Arrays.asList(Metric.class, Resource.class, MetricType.class,
                ResourceType.class));
        VALID_PROGRESSIONS.put(ResourceType.class, Arrays.asList(DataEntity.class, OperationType.class));
        VALID_PROGRESSIONS.put(OperationType.class, Collections.singletonList(DataEntity.class));
        VALID_PROGRESSIONS.put(Resource.class, Arrays.asList(Resource.class, DataEntity.class, Metric.class));
        VALID_PROGRESSIONS.put(DataEntity.class, Collections.singletonList(StructuredData.class));
        VALID_PROGRESSIONS.put(StructuredData.class, Collections.singletonList(StructuredData.class));
        VALID_PROGRESSIONS.put(null, Arrays.asList(Tenant.class, Relationship.class));
    }

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private CanonicalPath() {
    }

    CanonicalPath(int myIdx, List<Segment> path) {
        super(0, myIdx, path);
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
    public static Extender empty() {
        return new Extender(0, new ArrayList<>());
    }

    /**
     * @return a new path builder
     */
    public static Builder of() {
        return new Builder(new ArrayList<>());
    }

    /**
     * Creates a new path instance from the "serialized" slash-separated representation.
     *
     * <p>The escape character is {@code '\'} and special characters are {@code '\'}, {@code '|'} and {@code '/'}.
     *
     * @param path the string representation of the path
     * @return a new path instance
     */
    public static CanonicalPath fromString(String path) {
        return fromPartiallyUntypedString(path, new StructuredDataHintingTypeProvider());
    }

    /**
     * @param path         the canonical path to parse
     * @param typeProvider the type provider used to figure out types of segments that don't explicitly mention it
     * @return the parsed canonical path
     * @see Path#fromPartiallyUntypedString(String, TypeProvider)
     */
    public static CanonicalPath fromPartiallyUntypedString(String path, TypeProvider typeProvider) {
        return (CanonicalPath) Path.fromString(path, true, Extender::new,
                new CanonicalTypeProvider(typeProvider));
    }

    /**
     * An overload of {@link #fromPartiallyUntypedString(String, TypeProvider)} which uses the provided initial position
     * to figure out the possible type if is missing in the provided relative path.
     *
     * @param path              the relative path to parse
     * @param initialPosition   the initial position using which the types will be deduced for the segments that don't
     *                          specify the type explicitly
     * @param intendedFinalType the type of the final segment in the path. This can resolve potentially ambiguous
     *                          situations where, given the initial position, more choices are possible.
     * @return the parsed relative path
     */
    public static CanonicalPath fromPartiallyUntypedString(String path, CanonicalPath initialPosition,
            Class<?> intendedFinalType) {

        ExtenderConstructor ctor = (idx, list) -> {
            if (initialPosition != null) {
                list.addAll(initialPosition.getPath());
            }
            return new Extender(idx, list);
        };

        return (CanonicalPath) Path.fromString(path, true, ctor, new CanonicalTypeProvider(
                        new HintedTypeProvider(intendedFinalType,
                                new Extender(0, initialPosition == null ? new ArrayList<>()
                                        : new ArrayList<>(initialPosition.getPath()))))
        );
    }

    public <R, P> R accept(ElementTypeVisitor<R, P> visitor, P parameter) {
        return getSegment().accept(visitor, parameter);
    }

    @Override
    public CanonicalPath toCanonicalPath() {
        return this;
    }

    @Override
    public RelativePath toRelativePath() {
        return RelativePath.empty().extend(getPath()).get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<CanonicalPath> ascendingIterator() {
        return (Iterator<CanonicalPath>) super.ascendingIterator();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<CanonicalPath> descendingIterator() {
        return (Iterator<CanonicalPath>) super.descendingIterator();
    }

    @Override
    public CanonicalPath down() {
        return (CanonicalPath) super.down();
    }

    @Override
    public CanonicalPath down(int distance) {
        return (CanonicalPath) super.down(distance);
    }

    @Override
    protected CanonicalPath newInstance(int startIdx, int endIdx, List<Segment> segments) {
        return new CanonicalPath(startIdx, endIdx, segments);
    }

    @Override
    public CanonicalPath up() {
        return (CanonicalPath) super.up();
    }

    @Override
    public CanonicalPath up(int distance) {
        return (CanonicalPath) super.up(distance);
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

    public boolean isParentOf(CanonicalPath other) {
        if (other == null) {
            throw new IllegalArgumentException("other == null");
        }

        if (other.path.size() <= path.size()) {
            return false;
        }

        for (int i = 0; i < path.size(); ++i) {
            Segment mySeg = path.get(i);
            Segment otherSeg = other.path.get(i);

            if (!mySeg.equals(otherSeg)) {
                return false;
            }
        }

        return true;
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
     * @param id   the id of the appended entity
     * @return a new path instance
     * @throws IllegalArgumentException if adding the provided segment would create an invalid canonical path
     */
    public Extender extend(Class<?> type, String id) {
        return modified().extend(new Segment(type, id));
    }

    /**
     * The returned extender will produce a modified version of this path. This path will remain unaffected.
     *
     * @return an extender initialized with the current path
     */
    public Extender modified() {
        return new Extender(startIdx, new ArrayList<>(getPath()));
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
    public String toString() {
        return new Encoder(SHORT_TYPE_NAMES, x -> true).encode(Character.toString(PATH_DELIM), this);
    }

    @Override
    public Iterator<CanonicalPath> iterator() {
        return ascendingIterator();
    }

    public static final class Builder extends Path.Builder<CanonicalPath, TenantBuilder, EnvironmentBuilder,
            ResourceTypeBuilder, MetricTypeBuilder, RelationshipBuilder, OperationTypeBuilder, StructuredDataBuilder,
            MetadataPackBuilder, FeedBuilder, ResourceBuilder, MetricBuilder> {

        private Builder(List<Segment> list) {
            super(list, CanonicalPath::new);
        }

        @Override
        protected RelationshipBuilder relationshipBuilder(List<Segment> list) {
            return new RelationshipBuilder(list);
        }

        @Override
        protected TenantBuilder tenantBuilder(List<Segment> list) {
            return new TenantBuilder(list);
        }
    }

    public static final class TenantBuilder extends Path.TenantBuilder<CanonicalPath, EnvironmentBuilder,
            ResourceTypeBuilder, MetricTypeBuilder, OperationTypeBuilder, StructuredDataBuilder,
            MetadataPackBuilder, FeedBuilder, ResourceBuilder, MetricBuilder> {

        private TenantBuilder(List<Segment> list) {
            super(list, CanonicalPath::new);
        }

        @Override
        protected EnvironmentBuilder environmentBuilder(List<Segment> list) {
            return new EnvironmentBuilder(list);
        }

        @Override protected FeedBuilder feedBuilder(List<Segment> segments) {
            return new FeedBuilder(segments);
        }

        @Override
        protected ResourceTypeBuilder resourceTypeBuilder(List<Segment> list) {
            return new ResourceTypeBuilder(list);
        }

        @Override
        protected MetricTypeBuilder metricTypeBuilder(List<Segment> list) {
            return new MetricTypeBuilder(list);
        }

        @Override
        protected MetadataPackBuilder metadataPackBuilder(List<Segment> segments) {
            return new MetadataPackBuilder(segments);
        }
    }

    public static final class MetadataPackBuilder extends Path.MetadataPackBuilder<CanonicalPath> {
        private MetadataPackBuilder(List<Segment> segments) {
            super(segments, CanonicalPath::new);
        }
    }

    public static final class EnvironmentBuilder extends Path.EnvironmentBuilder<CanonicalPath,
            ResourceBuilder, MetricBuilder, ResourceTypeBuilder, MetricTypeBuilder, OperationTypeBuilder,
            StructuredDataBuilder> {

        private EnvironmentBuilder(List<Segment> list) {
            super(list, CanonicalPath::new);
        }

        @Override
        protected ResourceBuilder resourceBuilder(List<Segment> segments) {
            return new ResourceBuilder(segments);
        }

        @Override
        protected MetricBuilder metricBuilder(List<Segment> segments) {
            return new MetricBuilder(segments);
        }
    }

    public static final class ResourceTypeBuilder extends Path.ResourceTypeBuilder<CanonicalPath,
            OperationTypeBuilder, StructuredDataBuilder> {

        private ResourceTypeBuilder(List<Segment> list) {
            super(list, CanonicalPath::new);
        }

        @Override
        protected OperationTypeBuilder operationTypeBuilder(List<Segment> segments) {
            return new OperationTypeBuilder(segments);
        }

        @Override
        protected StructuredDataBuilder structuredDataBuilder(List<Segment> segments) {
            return new StructuredDataBuilder(segments);
        }
    }

    public static final class MetricTypeBuilder extends Path.MetricTypeBuilder<CanonicalPath> {

        private MetricTypeBuilder(List<Segment> segments) {
            super(segments, CanonicalPath::new);
        }
    }

    public static final class OperationTypeBuilder extends Path.OperationTypeBuilder<CanonicalPath,
            StructuredDataBuilder> {

        private OperationTypeBuilder(List<Segment> segments) {
            super(segments, CanonicalPath::new);
        }

        @Override
        protected StructuredDataBuilder structuredDataBuilder(List<Segment> segments) {
            return new StructuredDataBuilder(segments);
        }
    }

    public static final class ResourceBuilder extends Path.ResourceBuilder<CanonicalPath, ResourceBuilder,
            MetricBuilder, StructuredDataBuilder> {
        private ResourceBuilder(List<Segment> segments) {
            super(segments, CanonicalPath::new);
        }

        @Override protected MetricBuilder metricBuilder(List<Segment> segments) {
            return new MetricBuilder(segments);
        }

        @Override
        protected StructuredDataBuilder structuredDataBuilder(List<Segment> segments) {
            return new StructuredDataBuilder(segments);
        }
    }

    public static final class MetricBuilder extends Path.MetricBuilder<CanonicalPath> {
        private MetricBuilder(List<Segment> segments) {
            super(segments, CanonicalPath::new);
        }
    }

    public static final class FeedBuilder extends Path.FeedBuilder<CanonicalPath, ResourceTypeBuilder,
            MetricTypeBuilder, ResourceBuilder, MetricBuilder, OperationTypeBuilder, StructuredDataBuilder> {
        private FeedBuilder(List<Segment> list) {
            super(list, CanonicalPath::new);
        }

        @Override
        protected ResourceTypeBuilder resourceTypeBuilder(List<Segment> segments) {
            return new ResourceTypeBuilder(segments);
        }

        @Override
        protected MetricTypeBuilder metricTypeBuilder(List<Segment> segments) {
            return new MetricTypeBuilder(segments);
        }

        @Override
        protected ResourceBuilder resourceBuilder(List<Segment> segments) {
            return new ResourceBuilder(segments);
        }

        @Override
        protected MetricBuilder metricBuilder(List<Segment> segments) {
            return new MetricBuilder(segments);
        }
    }

    public static final class RelationshipBuilder extends Path.RelationshipBuilder<CanonicalPath> {
        private RelationshipBuilder(List<Segment> segments) {
            super(segments, CanonicalPath::new);
        }
    }

    public static final class StructuredDataBuilder extends Path.StructuredDataBuilder<CanonicalPath,
            CanonicalPath.StructuredDataBuilder> {

        private StructuredDataBuilder(List<Segment> segments) {
            super(segments, CanonicalPath::new);
        }
    }

    public final class IdExtractor {

        public String getTenantId() {
            return idIfTypeCorrect(getRoot(), Tenant.class);
        }

        public String getEnvironmentId() {
            return idIfTypeCorrect(getRoot().down(), Environment.class);
        }

        public String getMetricTypeId() {
            if (getFeedId() != null) {
                return idIfTypeCorrect(getRoot().down(2), MetricType.class);
            } else {
                return idIfTypeCorrect(getRoot().down(), MetricType.class);
            }
        }

        public String getResourceTypeId() {
            if (getFeedId() != null) {
                return idIfTypeCorrect(getRoot().down(2), ResourceType.class);
            } else {
                return idIfTypeCorrect(getRoot().down(), ResourceType.class);
            }
        }

        public String getFeedId() {
            return idIfTypeCorrect(getRoot().down(), Feed.class);
        }

        /**
         * This creates a relative path that represents the resource hierarchy present in this path.
         * <p>
         * Note that the returned relative path is backed by this canonical path and therefore you can call
         * {@link RelativePath#slide(int, int)} on it and modify it to "see" the parts of the path "above" and "below"
         * the resource segments.
         *
         * @return the resource hierarchy in the path represented as a relative path or null if there are no resources
         * in the path.
         */
        public RelativePath getResourcePath() {
            int from, to;
            List<Segment> path = CanonicalPath.this.path;

            for (from = CanonicalPath.this.startIdx; from < CanonicalPath.this.endIdx; ++from) {
                if (Resource.class.equals(path.get(from).getElementType())) {
                    break;
                }
            }

            if (from == path.size()) {
                return null;
            }
            for (to = from; to < CanonicalPath.this.endIdx; ++to) {
                if (!Resource.class.equals(path.get(to).getElementType())) {
                    break;
                }
            }

            return new RelativePath(from, to, CanonicalPath.this.path);
        }

        public String getMetricId() {
            return idIfTypeCorrect(CanonicalPath.this, Metric.class);
        }

        public String getRelationshipId() {
            return idIfTypeCorrect(getRoot(), Relationship.class);
        }

        public String getOperationTypeId() {
            return idIfTypeCorrect(CanonicalPath.this, OperationType.class);
        }

        @SuppressWarnings("unchecked")
        public <R extends DataEntity.Role> R getDataRole() {
            CanonicalPath currentPath = CanonicalPath.this;

            //move up from the potential data path segments
            while (StructuredData.class.equals(currentPath.getSegment().getElementType())) {
                currentPath = currentPath.up();
            }

            //now we should be at the data entity, which should contain our role
            String roleStr = idIfTypeCorrect(currentPath, DataEntity.class);

            return roleStr == null ? null : (R) DataEntity.Role.valueOf(roleStr);
        }

        private String idIfTypeCorrect(CanonicalPath path, Class<? extends AbstractElement<?, ?>> desiredType) {
            if (path.isDefined() && path.getSegment().getElementType().equals(desiredType)) {
                return path.getSegment().getElementId();
            } else {
                return null;
            }
        }
    }

    public static class Extender extends Path.Extender {

        Extender(int from, List<Segment> segments) {
            super(from, segments, true, (s) -> s.isEmpty() ? Arrays.asList(Tenant.class, Relationship.class)
                    : VALID_PROGRESSIONS.get(s.get(s.size() - 1).getElementType()));
        }

        @Override
        protected CanonicalPath newPath(int startIdx, int endIdx, List<Segment> segments) {
            return new CanonicalPath(startIdx, endIdx, segments);
        }

        @Override
        public Extender extend(Segment segment) {
            return (Extender) super.extend(segment);
        }

        public Extender extend(Collection<Segment> segments) {
            return (Extender) super.extend(segments);
        }

        @Override
        public Extender extend(Class<? extends AbstractElement<?, ?>> type, String id) {
            return (Extender) super.extend(type, id);
        }

        @Override
        public CanonicalPath get() {
            return (CanonicalPath) super.get();
        }
    }

    private static class CanonicalTypeProvider extends EnhancedTypeProvider {
        private final TypeProvider wrapped;

        private CanonicalTypeProvider(TypeProvider wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void segmentParsed(Segment segment) {
            if (wrapped != null) {
                wrapped.segmentParsed(segment);
            }
        }

        @Override
        public Segment deduceSegment(String type, String id, boolean isLast) {
            if (type != null && !type.isEmpty()) {
                if (id == null || id.isEmpty()) {
                    return null;
                } else {
                    return new Segment(SHORT_NAME_TYPES.get(type), id);
                }
            }

            if (id == null || id.isEmpty()) {
                return null;
            }

            Class<?> cls = SHORT_NAME_TYPES.get(id);
            if (cls == null && wrapped != null) {
                return wrapped.deduceSegment(type, id, isLast);
            } else if (cls != null) {
                return new Segment(cls, id);
            }

            return null;
        }

        @Override
        public void finished() {
            if (wrapped != null) {
                wrapped.finished();
            }
        }

        @Override
        Set<String> getValidTypeName() {
            return SHORT_NAME_TYPES.keySet();
        }
    }
}
