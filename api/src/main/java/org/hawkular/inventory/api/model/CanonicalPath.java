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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import static java.util.stream.Collectors.toList;

/**
 * A path represents the canonical traversal to an element through the inventory graph. The canonical traversal
 * always starts at a tenant and follows only the "contains" relationships down to the entity in question. For
 * relationships the "traversal" comprises of merely referencing the relationship by its id.
 *
 * <p>The path can be iterated both ways either from the current segment up from the root down to the current segment
 * using the {@link #ascendingIterator()} or {@link #descendingIterator()} respectively. The {@link Iterable} interface
 * is implemented using the {@link #ascendingIterator()}.
 *
 * <p>The {@link #up()} and {@link #down()} methods can also be used to get at the paths of the ancestors in an
 * easy manner. Note though, that these methods don't do any "database lookup" of any sort. They merely work with
 * the path of the first path instance created.
 *
 * <p>The following examples illustrate the behavior of {@code up()} and {@code down()} methods:
 *
 * <code><pre>
 * CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").resource("r").build();
 * p.down(); // == undefined (p.down().isDefined() == false)
 * p.up(); // == t/e
 * p.up(2); // == t
 * p.up().up(2); // == undefined
 *
 * p.up().down(); // == t/e/r
 * p.up().up().down(); // == t/e
 * p.up().down().down(); // == undefined
 * </pre></code>
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class CanonicalPath implements Iterable<CanonicalPath>, Serializable {
    //all path instances created from this one in the up(), down() and *iterator() methods will share this list
    //and will only differ in their "myIdx" field.
    private final List<Segment> path;
    private final int myIdx;

    /**
     * JAXB support
     */
    private CanonicalPath() {
        this.path = null;
        myIdx = 0;
    }

    private CanonicalPath(int myIdx, List<Segment> path) {
        this.myIdx = myIdx;
        this.path = Collections.unmodifiableList(path);
    }

    /**
     * @return a new path builder
     */
    public static Builder of() {
        return new Builder();
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
        Extender extender = new Extender(new ArrayList<>());

        ElementType currentType = null;
        StringBuilder currentId = new StringBuilder();

        //0 = reading type ordinal
        //1 = reading id
        //2 = reading escape char
        int state = 0;
        for (int i = 0; i < path.length(); ++i) {
            char c = path.charAt(i);

            switch (state) {
                case 0: // reading type ordinal
                    switch (c) {
                        case '|':
                            if (currentId.length() == 0) {
                                throw new IllegalArgumentException("Unspecified entity type id at pos " + i +
                                        " in \"" + path + "\".");
                            }

                            currentType = ElementType.fromShortString(currentId.toString());
                            if (currentType == null) {
                                throw new IllegalArgumentException("Unrecognized entity type id: '" +
                                        currentId.toString() + "'. Only the following are recognized: " +
                                        Arrays.asList(ElementType.values()).stream().map(ElementType::getShortString)
                                                .collect(toList()));
                            }
                            currentId.delete(0, currentId.length());
                            state = 1; //reading id
                            break;
                        default:
                            currentId.append(c);
                    }
                    break;
                case 1: //reading id
                    switch (c) {
                        case '\\':
                            state = 2; // reading escape char
                            break;
                        case '/':
                            if (currentId.length() == 0) {
                                throw new IllegalArgumentException("Unspecified entity id at pos " + i + " in \"" +
                                        path + "\".");
                            }

                            extender.extend(new Segment(currentType, currentId.toString()));
                            state = 0;
                            currentId.delete(0, currentId.length());
                            currentType = null;
                            break;
                        default:
                            currentId.append(c);
                    }
                    break;
                case 2: //reading escape char
                    currentId.append(c);
                    state = 1;
                    break;
            }
        }

        //add the last segment
        if (currentType == null) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        if (currentId.length() == 0) {
            throw new IllegalArgumentException("Unspecified entity id in \"" + path + "\".");
        }
        extender.extend(new Segment(currentType, currentId.toString()));

        return extender.get();
    }

    public <R, P> R accept(ElementTypeVisitor<R, P> visitor, P parameter) {
        return getSegment().accept(visitor, parameter);
    }

    /**
     * Checks whether the path is well-formed. Going {@link #up()} or {@link #down()} on the path can render it
     * undefined if it is traveresed "too far" in either of the directions.
     *
     * @return true if this path is well-formed, false otherwise.
     */
    public boolean isDefined() {
        return myIdx > 0 && myIdx <= path.size();
    }

    /**
     * Returns a path corresponding to the direct ancestor of the resource represented by this path object.
     *
     * @return the ancestor path (may be {@link #isDefined() undefined}.
     */
    public CanonicalPath up() {
        return up(1);
    }

    /**
     * Returns a path corresponding to the n-th ancestor in the hierarchy.
     *
     * @param distance the distance of the ancestor from the resource represented by this path object.
     * @return the ancestor path (may be {@link #isDefined() undefined}.
     */
    public CanonicalPath up(int distance) {
        return new CanonicalPath(myIdx - distance, path);
    }

    /**
     * If this path was created by going {@link #up() up} from another path, then this method can be used to go back
     * down to the previous paths representing some child resource of the resource represented by this path object.
     *
     * @return a path to a direct child of the resource represented by this path (may be {@link #isDefined() undefined}.
     */
    public CanonicalPath down() {
        return down(1);
    }

    /**
     * If this path was created by going {@link #up() up} from another path, then this method can be used to go back
     * down n steps to the previous paths representing some (grand-)child resource of the resource represented by
     * this path object.
     *
     * @param distance the distance from this path to the child path
     * @return a path to a child of the resource represented by this path (may be {@link #isDefined() undefined}.
     */
    public CanonicalPath down(int distance) {
        return new CanonicalPath(myIdx + distance, path);
    }

    /**
     * @return the number of ancestors. This may be less than zero for undefined paths (see {@link #isDefined()}).
     */
    public int getDepth() {
        return myIdx - 1;
    }

    /**
     * @return the last path segment on the path or null if this path is not {@link #isDefined() defined}. E.g. if this
     * path represents {@code "a/b/c"} then the segment returned from this method is {@code "c"}
     */
    public Segment getSegment() {
        return isDefined() ? path.get(myIdx - 1) : null;
    }

    /**
     * Get the full path represented as an array of the individual segments. The 0-th element in the array
     * represents the segment of the root resource and the last element of the array is the segment of this path
     * instance.
     *
     * @return the unmodifiable path segments or empty list if this path is {@link #isDefined() undefined}.
     */
    public List<Segment> getPath() {
        return isDefined() ? path.subList(0, myIdx) : Collections.emptyList();
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
     * @param segment the segment to append to the path
     * @return a new path instance
     * @throws IllegalArgumentException if adding the provided segment would create an invalid canonical path
     */
    public Extender extend(Segment segment) {
        Extender ret = new Extender(new ArrayList<>(getPath()));
        return ret.extend(segment);
    }

    public Extender extend(Class<? extends AbstractElement<?, ?>> type, String id) {
        return extend(new Segment(ElementType.of(type), id));
    }

    /**
     * @return the {@link #ascendingIterator()}
     */
    @Override
    public Iterator<CanonicalPath> iterator() {
        return ascendingIterator();
    }

    /**
     * @return the iterator that ascends the path from the current segment up to the root
     */
    public Iterator<CanonicalPath> ascendingIterator() {
        return new Iterator<CanonicalPath>() {
            int idx = myIdx;

            @Override
            public boolean hasNext() {
                return idx > 0;
            }

            @Override
            public CanonicalPath next() {
                if (idx <= 0) {
                    throw new NoSuchElementException();
                }
                return new CanonicalPath(--idx, path);
            }
        };
    }

    /**
     * @return the iterator that descends from the root down to the current segment.
     */
    public Iterator<CanonicalPath> descendingIterator() {
        return new Iterator<CanonicalPath>() {
            int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < myIdx;
            }

            @Override
            public CanonicalPath next() {
                if (idx >= myIdx) {
                    throw new NoSuchElementException();
                }
                return new CanonicalPath(idx++, path);
            }
        };
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
    public int hashCode() {
        int ret = 1;
        for (int i = 0; i < myIdx; ++i) {
            ret = 31 * ret + (path.get(i).hashCode());
        }

        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof CanonicalPath)) {
            return false;
        }

        CanonicalPath other = (CanonicalPath) o;

        if (myIdx != other.myIdx) {
            return false;
        }

        for (int i = myIdx - 1; i >= 0; --i) {
            if (!path.get(i).equals(other.path.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    @JsonValue
    public String toString() {
        StringBuilder bld = new StringBuilder();

        for (int i = 0; i < myIdx; ++i) {
            Segment seg = path.get(i);
            bld.append(seg.getElementType().getShortString()).append('|');
            for (int j = 0; j < seg.getElementId().length(); ++j) {
                char c = seg.getElementId().charAt(j);
                if (c == '|' || c == '/' || c == '\\') {
                    bld.append('\\');
                }
                bld.append(c);
            }
            bld.append('/');
        }
        return bld.delete(bld.length() - 1, bld.length()).toString();
    }

    public static final class Segment {
        private final ElementType elementType;
        private final String entityId;

        public Segment(ElementType elementType, String entityId) {
            this.entityId = entityId;
            this.elementType = elementType;
        }

        public <R, P> R accept(ElementTypeVisitor<R, P> visitor, P parameter) {
            switch (elementType) {
                case ENVIRONMENT:
                    return visitor.visitEnvironment(parameter);
                case FEED:
                    return visitor.visitFeed(parameter);
                case METRIC:
                    return visitor.visitMetric(parameter);
                case METRIC_TYPE:
                    return visitor.visitMetricType(parameter);
                case RELATIONSHIP:
                    return visitor.visitRelationship(parameter);
                case RESOURCE:
                    return visitor.visitResource(parameter);
                case RESOURCE_TYPE:
                    return visitor.visitResourceType(parameter);
                case TENANT:
                    return visitor.visitTenant(parameter);
                default:
                    throw new AssertionError("Unknown element type: " + elementType);
            }
        }

        public String getElementId() {
            return entityId;
        }

        public ElementType getElementType() {
            return elementType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Segment)) return false;

            Segment segment = (Segment) o;

            return elementType == segment.elementType && entityId.equals(segment.entityId);

        }

        @Override
        public int hashCode() {
            int result = elementType.hashCode();
            result = 31 * result + entityId.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Segment[" + "entityId='" + entityId + '\'' + ", entityType=" + elementType + ']';
        }
    }

    public static final class Builder {
        private final List<Segment> segments = new ArrayList<>();

        private Builder() {

        }

        public TenantBuilder tenant(String id) {
            segments.add(new Segment(ElementType.TENANT, id));
            return new TenantBuilder();
        }

        public RelationshipBuilder relationship(String id) {
            segments.add(new Segment(ElementType.RELATIONSHIP, id));
            return new RelationshipBuilder();
        }

        public final class RelationshipBuilder {
            private RelationshipBuilder() {

            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }

        public final class TenantBuilder {
            private TenantBuilder() {
            }

            public EnvironmentBuilder environment(String id) {
                segments.add(new Segment(ElementType.ENVIRONMENT, id));
                return new EnvironmentBuilder();
            }

            public ResourceTypeBuilder resourceType(String id) {
                segments.add(new Segment(ElementType.RESOURCE_TYPE, id));
                return new ResourceTypeBuilder();
            }

            public MetricTypeBuilder metricType(String id) {
                segments.add(new Segment(ElementType.METRIC_TYPE, id));
                return new MetricTypeBuilder();
            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }

        public final class ResourceTypeBuilder {
            private ResourceTypeBuilder() {
            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }

        public final class MetricTypeBuilder {
            private MetricTypeBuilder() {
            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }

        public final class EnvironmentBuilder {
            private EnvironmentBuilder() {
            }

            public FeedBuilder feed(String id) {
                segments.add(new Segment(ElementType.FEED, id));
                return new FeedBuilder();
            }

            public ResourceBuilder resource(String id) {
                segments.add(new Segment(ElementType.RESOURCE, id));
                return new ResourceBuilder();
            }

            public MetricBuilder metric(String id) {
                segments.add(new Segment(ElementType.METRIC, id));
                return new MetricBuilder();
            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }

        public final class FeedBuilder {
            private FeedBuilder() {
            }

            public ResourceBuilder resource(String id) {
                segments.add(new Segment(ElementType.RESOURCE, id));
                return new ResourceBuilder();
            }

            public MetricBuilder metric(String id) {
                segments.add(new Segment(ElementType.METRIC, id));
                return new MetricBuilder();
            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }

        public final class ResourceBuilder {
            private ResourceBuilder() {
            }

            public ResourceBuilder resource(String id) {
                segments.add(new Segment(ElementType.RESOURCE, id));
                return this;
            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }

        public final class MetricBuilder {
            private MetricBuilder() {
            }

            public CanonicalPath get() {
                return new CanonicalPath(segments.size(), segments);
            }
        }
    }

    /**
     * While {@link org.hawkular.inventory.api.model.CanonicalPath.Builder} provides compile-time-safe canonical path
     * construction, this class provides the same behavior at runtime, throwing {@link IllegalArgumentException}s if
     * the segments being added to a path are invalid in given context.
     */
    public static final class Extender {
        private final ElementTypeVisitor<Void, Segment> CHECKER = new ElementTypeVisitor<Void, Segment>() {
            @Override
            public Void visitTenant(Segment segment) {
                return check(segment, ElementType.ENVIRONMENT, ElementType.METRIC_TYPE, ElementType.RESOURCE_TYPE);
            }

            @Override
            public Void visitEnvironment(Segment segment) {
                return check(segment, ElementType.METRIC, ElementType.RESOURCE, ElementType.FEED);
            }

            @Override
            public Void visitFeed(Segment segment) {
                return check(segment, ElementType.METRIC, ElementType.RESOURCE);
            }

            @Override
            public Void visitMetric(Segment segment) {
                return check(segment);
            }

            @Override
            public Void visitMetricType(Segment segment) {
                return check(segment);
            }

            @Override
            public Void visitResource(Segment segment) {
                return check(segment, ElementType.RESOURCE);
            }

            @Override
            public Void visitResourceType(Segment segment) {
                return check(segment);
            }

            @Override
            public Void visitRelationship(Segment segment) {
                return check(segment);
            }

            @Override
            public Void visitUnknown(Segment segment) {
                return check(segment);
            }

            private Void check(Segment segment, ElementType... allowedSegmentTypes) {
                if (!Arrays.asList(allowedSegmentTypes).contains(segment.getElementType())) {
                    throw new IllegalArgumentException("The provided segment " + segment + " is not valid extension" +
                            " of the canonical path: " + new CanonicalPath(segments.size(), segments));
                }

                return null;
            }
        };

        private final List<Segment> segments;

        private Extender(List<Segment> segments) {
            this.segments = segments;
        }

        public Extender extend(Segment segment) {
            if (segments.size() > 0) {
                segments.get(segments.size() - 1).accept(CHECKER, segment);
            } else {
                //first path segment needs to be either tenant or relationship
                if (segment.getElementType() != ElementType.TENANT &&
                        segment.getElementType() != ElementType.RELATIONSHIP) {
                    throw new IllegalArgumentException("Illegal root segment of a canonical path. Only tenant or" +
                            " relationship segments allowed. Encountered: " + segment);
                }
            }
            segments.add(segment);
            return this;
        }

        public Extender extend(Class<? extends AbstractElement<?, ?>> type, String id) {
            return extend(new Segment(ElementType.of(type), id));
        }

        public CanonicalPath get() {
            return new CanonicalPath(segments.size(), segments);
        }
    }

    public final class IdExtractor {

        public String getTenantId() {
            return idIfTypeCorrect(getRoot(), ElementType.TENANT);
        }

        public String getEnvironmentId() {
            return idIfTypeCorrect(getRoot().down(), ElementType.ENVIRONMENT);
        }

        public String getMetricTypeId() {
            return idIfTypeCorrect(getRoot().down(), ElementType.METRIC_TYPE);
        }

        public String getResourceTypeId() {
            return idIfTypeCorrect(getRoot().down(), ElementType.RESOURCE_TYPE);
        }

        public String getFeedId() {
            return idIfTypeCorrect(getRoot().down(2), ElementType.FEED);
        }

        public String getResourceId() {
            String id = idIfTypeCorrect(getRoot().down(2), ElementType.RESOURCE);

            return id != null ? id : idIfTypeCorrect(getRoot().down(3), ElementType.RESOURCE);
        }

        public String getMetricId() {
            String id = idIfTypeCorrect(getRoot().down(2), ElementType.METRIC);

            return id != null ? id : idIfTypeCorrect(getRoot().down(3), ElementType.METRIC);
        }

        public String getRelationshipId() {
            return idIfTypeCorrect(getRoot(), ElementType.RELATIONSHIP);
        }

        private String idIfTypeCorrect(CanonicalPath path, ElementType desiredType) {
            if (path.isDefined() && path.getSegment().getElementType() == desiredType) {
                return path.getSegment().getElementId();
            } else {
                return null;
            }
        }
    }
}
