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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Represents a path in an inventory. The path is either {@link CanonicalPath} or {@link RelativePath}.
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
 * <p>The serialized form of the path has the following format:
 * <pre>{@code
 * type;id/type;id/type;id
 * }</pre>
 * I.e. each of the path segments consists of the type of the element it represents followed by the id of the element.
 *
 * <p>The type of the entity is one of:
 * <ul>
 *     <li><b>t</b> - tenant,
 *     <li><b>e</b> - environment,
 *     <li><b>rt</b> - resource type,
 *     <li><b>mt</b> - metric type,
 *     <li><b>f</b> - feed,
 *     <li><b>m</b> - metric,
 *     <li><b>r</b> - resource,
 *     <li><b>rl</b> - relationship
 * <p> In addition to that, the relative paths can contain the special "up" token - <code>..</code> - instead of the
 * {@code type:id} pair. E.g. a relative path may look like this: {@code ../e;production/r;myResource}.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public class AbstractPath<This extends AbstractPath<This>> implements Iterable<This> {

    private static final char TYPE_DELIM = ';';
    private static final char PATH_DELIM = '/';
    private static final char ESCAPE_CHAR = '\\';

    //all path instances created from this one in the up(), down() and *iterator() methods will share this list
    //and will only differ in their "myIdx" field.
    protected final List<Segment> path;
    protected final int startIdx;
    protected final int endIdx;

    //this really should be final, but that would make it ugly to deserialize. Needs to be set by subclasses in
    //readObject
    protected transient Constructor<This> constructor;

    AbstractPath() {
        path = null;
        startIdx = 0;
        endIdx = 0;
        constructor = null;
    }

    AbstractPath(int startIdx, int endIdx, List<Segment> path, Constructor<This> constructor) {
        this.startIdx = startIdx;
        this.endIdx = endIdx;
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Empty path is not valid.");
        }
        this.path = Collections.unmodifiableList(path);
        this.constructor = constructor;
    }

    protected static <Impl extends AbstractPath<Impl>> Impl fromString(String path, Map<Class<?>,
            List<Class<?>>> validProgressions, Map<String, Class<?>> shortNameTypes,
            Function<Class<?>, Boolean> requiresId, Constructor<Impl> constructor) {

        Extender<Impl> extender = new Extender<>(0, new ArrayList<>(), validProgressions, constructor);

        ParsingProgress progress = new ParsingProgress(path);

        Decoder dec = new Decoder(requiresId, shortNameTypes);

        while (!progress.isFinished()) {
            Segment seg = dec.decodeNext(progress);
            extender.extend(seg);
        }

        return extender.get();
    }

    /**
     * Checks whether the path is well-formed. Going {@link #up()} or {@link #down()} on the path can render it
     * undefined if it is traversed "too far" in either of the directions.
     *
     * @return true if this path is well-formed, false otherwise.
     */
    public boolean isDefined() {
        return endIdx > startIdx && endIdx <= path.size();
    }

    /**
     * Returns a path corresponding to the direct ancestor of the resource represented by this path object.
     *
     * @return the ancestor path (may be {@link #isDefined() undefined}.
     */
    public This up() {
        return up(1);
    }

    /**
     * Returns a path corresponding to the n-th ancestor in the hierarchy.
     *
     * @param distance the distance of the ancestor from the resource represented by this path object.
     * @return the ancestor path (may be {@link #isDefined() undefined}.
     */
    public This up(int distance) {
        return constructor.create(startIdx, endIdx - distance, path);
    }

    /**
     * If this path was created by going {@link #up() up} from another path, then this method can be used to go back
     * down to the previous paths representing some child resource of the resource represented by this path object.
     *
     * @return a path to a direct child of the resource represented by this path (may be {@link #isDefined() undefined}.
     */
    public This down() {
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
    public This down(int distance) {
        return constructor.create(startIdx, endIdx + distance, path);
    }

    /**
     * @return the number of ancestors. This may be less than zero for undefined paths (see {@link #isDefined()}).
     */
    public int getDepth() {
        return endIdx - startIdx - 1;
    }

    /**
     * @return the last path segment on the path or null if this path is not {@link #isDefined() defined}. E.g. if this
     * path represents {@code "a/b/c"} then the segment returned from this method is {@code "c"}
     */
    public Segment getSegment() {
        return isDefined() ? path.get(endIdx - 1) : null;
    }

    /**
     * Get the full path represented as an array of the individual segments. The 0-th element in the array
     * represents the segment of the root resource and the last element of the array is the segment of this path
     * instance.
     *
     * @return the unmodifiable path segments or empty list if this path is {@link #isDefined() undefined}.
     */
    public List<Segment> getPath() {
        return isDefined() ? path.subList(startIdx, endIdx) : Collections.emptyList();
    }

    /**
     * @return the {@link #ascendingIterator()}
     */
    @Override
    public Iterator<This> iterator() {
        return ascendingIterator();
    }

    /**
     * @return the iterator that ascends the path from the current segment up to the root
     */
    public Iterator<This> ascendingIterator() {
        return new Iterator<This>() {
            int idx = endIdx;

            @Override
            public boolean hasNext() {
                return idx > startIdx;
            }

            @Override
            public This next() {
                if (idx <= startIdx) {
                    throw new NoSuchElementException();
                }
                return constructor.create(startIdx, idx--, path);
            }
        };
    }

    /**
     * @return the iterator that descends from the root down to the current segment.
     */
    public Iterator<This> descendingIterator() {
        return new Iterator<This>() {
            int idx = startIdx + 1;

            @Override
            public boolean hasNext() {
                return idx < endIdx;
            }

            @Override
            public This next() {
                if (idx >= endIdx) {
                    throw new NoSuchElementException();
                }
                return constructor.create(startIdx, idx++, path);
            }
        };
    }

    @Override
    public int hashCode() {
        int ret = startIdx;
        for (int i = startIdx; i < endIdx; ++i) {
            ret = 31 * ret + (path.get(i).hashCode());
        }

        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(this.getClass().equals(o.getClass()))) {
            return false;
        }

        AbstractPath other = (AbstractPath) o;

        if (endIdx != other.endIdx || startIdx != other.startIdx) {
            return false;
        }

        for (int i = endIdx - 1; i >= startIdx; --i) {
            if (!path.get(i).equals(other.path.get(i))) {
                return false;
            }
        }

        return true;
    }

    @FunctionalInterface
    interface Constructor<Path> {
        Path create(int startIdx, int endIx, List<Segment> segments);
    }

    protected static class ParsingProgress {
        private int pos;
        private final String source;

        public ParsingProgress(String source) {
            this.source = source;
        }

        public int getPos() {
            return pos;
        }

        public char getNextChar() {
            return source.charAt(pos++);
        }

        public String getSource() {
            return source;
        }

        public boolean isFinished() {
            return pos >= source.length();
        }
    }


    protected static class Decoder {
        private final Function<Class<?>, Boolean> requiresId;
        private final Map<String, Class<?>> typeMap;

        protected Decoder(Function<Class<?>, Boolean> requiresId, Map<String, Class<?>> typeMap) {
            this.requiresId = requiresId;
            this.typeMap = typeMap;
        }

        private Class<?> getSegmentType(String type) {
            return typeMap.get(type);
        }

        public Segment decodeNext(ParsingProgress progress) {
            StringBuilder currentId = new StringBuilder();
            Class<?> currentType = null;

            //0 = reading type ordinal
            //1 = reading id
            //2 = reading escape char
            int state = 0;
            while (!progress.isFinished()) {
                char c = progress.getNextChar();

                switch (state) {
                    case 0: // reading type ordinal
                        switch (c) {
                            case TYPE_DELIM:
                                if (currentId.length() == 0) {
                                    throw new IllegalArgumentException("Unspecified entity type id at pos " +
                                            progress.getPos() + " in \"" + progress.getSource() + "\".");
                                }

                                currentType = getSegmentType(currentId.toString());
                                if (!requiresId.apply(currentType)) {
                                    throw new IllegalArgumentException("Type specified by '" + currentId.toString() +
                                            "' doesn't need to provide an id, but one is found.");
                                } else if (currentType == null) {
                                    throw new IllegalArgumentException("Unrecognized entity type id: '" +
                                            currentId.toString() + "'. Only the following are recognized: " +
                                            typeMap.keySet());
                                }
                                currentId.delete(0, currentId.length());
                                state = 1; //reading id
                                break;
                            case PATH_DELIM:
                                currentType = getSegmentType(currentId.toString());
                                if (requiresId.apply(currentType)) {
                                    throw new IllegalArgumentException("Type specified by '" + currentId.toString() +
                                            "' needs to provide an id, but none found.");
                                } else if (currentType == null) {
                                    throw new IllegalArgumentException("Unrecognized entity type id: '" +
                                            currentId.toString() + "'. Only the following are recognized: " +
                                            typeMap.keySet());
                                } else {
                                    return new Segment(currentType, null);
                                }
                            default:
                                currentId.append(c);
                        }
                        break;
                    case 1: //reading id
                        switch (c) {
                            case ESCAPE_CHAR:
                                state = 2; // reading escape char
                                break;
                            case PATH_DELIM:
                                if (currentId.length() == 0) {
                                    throw new IllegalArgumentException("Unspecified entity id at pos " +
                                            progress.getPos() + " in \"" + progress.getSource() + "\".");
                                }

                                return new Segment(currentType, currentId.toString());
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

            //we've finished reading the source. So we need to emit the last segment.
            if (currentType == null) {
                currentType = getSegmentType(currentId.toString());
                if (requiresId.apply(currentType)) {
                    if (requiresId.apply(currentType)) {
                        throw new IllegalArgumentException("Type specified by '" + currentId.toString() +
                                "' needs to provide an id, but none found.");
                    } else if (currentType == null) {
                        throw new IllegalArgumentException("Unrecognized entity type id: '" +
                                currentId.toString() + "'. Only the following are recognized: " +
                                typeMap.keySet());
                    } else {
                        return new Segment(currentType, null);
                    }
                }
            }
            if (currentId.length() == 0) {
                if (requiresId.apply(currentType)) {
                    throw new IllegalArgumentException("Unspecified entity id in \"" + progress.getSource() + "\".");
                } else {
                    return new Segment(currentType, null);
                }
            }
            return new Segment(currentType, currentId.toString());
        }
    }

    protected static class Encoder {
        private final Map<Class<?>, String> typeMap;

        public Encoder(Map<Class<?>, String> typeMap) {
            this.typeMap = typeMap;
        }

        public <P extends AbstractPath<P>> String encode(AbstractPath<P> path) {
            StringBuilder bld = new StringBuilder();

            for (Segment seg : path.getPath()) {
                String type = typeMap.get(seg.getElementType());
                if (type != null) {
                    bld.append(type);
                }

                if (seg.getElementId() != null) {
                    if (type != null) {
                        bld.append(TYPE_DELIM);
                    }

                    for (int j = 0; j < seg.getElementId().length(); ++j) {
                        char c = seg.getElementId().charAt(j);
                        if (c == TYPE_DELIM || c == PATH_DELIM || c == ESCAPE_CHAR) {
                            bld.append(ESCAPE_CHAR);
                        }
                        bld.append(c);
                    }
                }
                bld.append(PATH_DELIM);
            }
            return bld.delete(bld.length() - 1, bld.length()).toString();
        }
    }

    public static final class Segment {
        private final Class<?> elementType;
        private final String entityId;

        public Segment(Class<?> elementType, String entityId) {
            this.entityId = entityId;
            this.elementType = elementType;
        }

        public <R, P> R accept(ElementTypeVisitor<R, P> visitor, P parameter) {
            if (Environment.class.equals(elementType)) {
                return visitor.visitEnvironment(parameter);
            } else if (Feed.class.equals(elementType)) {
                return visitor.visitFeed(parameter);
            } else if (Metric.class.equals(elementType)) {
                return visitor.visitMetric(parameter);
            } else if (MetricType.class.equals(elementType)) {
                return visitor.visitMetricType(parameter);
            } else if (Relationship.class.equals(elementType)) {
                return visitor.visitRelationship(parameter);
            } else if (Resource.class.equals(elementType)) {
                return visitor.visitResource(parameter);
            } else if (ResourceType.class.equals(elementType)) {
                return visitor.visitResourceType(parameter);
            } else if (Tenant.class.equals(elementType)) {
                return visitor.visitTenant(parameter);
            } else {
                return visitor.visitUnknown(parameter);
            }
        }

        public String getElementId() {
            return entityId;
        }

        public Class<?> getElementType() {
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
            return "Segment[" + "entityId='" + entityId + '\'' + ", entityType=" + elementType.getSimpleName() + ']';
        }
    }

    /**
     * While {@link CanonicalPath.Builder} or {@link RelativePath.Builder} provide compile-time-safe canonical path
     * construction, this class provides the same behavior at runtime, throwing {@link IllegalArgumentException}s if
     * the segments being added to a path are invalid in given context.
     */
    public static class Extender<PathImpl extends AbstractPath<PathImpl>> {
        protected final List<Segment> segments;
        private final Map<Class<?>, List<Class<?>>> validProgressions;
        private final Constructor<PathImpl> constructor;
        private final int from;

        /**
         * Constructs a new extender
         *
         * @param from
         * @param segments          the list of already existing segments.
         * @param validProgressions the map of valid progressions (from element of type A to elements of other types)
         * @param constructor       the constructor to use in the {@link #get()} method to create the path instance
         */
        Extender(int from, List<Segment> segments, Map<Class<?>, List<Class<?>>> validProgressions,
                Constructor<PathImpl> constructor) {
            this.from = from;
            this.segments = segments;
            this.validProgressions = validProgressions;
            this.constructor = constructor;
        }

        public Extender<PathImpl> extend(Segment segment) {
            Class<?> first = segments.isEmpty() ? null : segments.get(segments.size() - 1).getElementType();

            if (!isValidProgression(first, segment.getElementType())) {
                throw new IllegalArgumentException("The provided segment " + segment + " is not valid extension" +
                        " of the path: " + segments);
            }
            segments.add(segment);
            return this;
        }

        public Extender<PathImpl> extend(Class<? extends AbstractElement<?, ?>> type, String id) {
            return extend(new Segment(type, id));
        }

        public PathImpl get() {
            return constructor.create(from, segments.size(), segments);
        }

        private boolean isValidProgression(Class<?> from, Class<?> to) {
            List<Class<?>> validNexts = validProgressions.get(from);
            if (validNexts == null) {
                return false;
            }
            return validNexts.contains(to);
        }
    }

    protected abstract static class AbstractBuilder<Impl extends AbstractPath<Impl>> {
        protected final List<Segment> segments;
        protected final Constructor<Impl> constructor;

        AbstractBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            this.segments = segments;
            this.constructor = constructor;
        }
    }

    public static class Builder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {
        Builder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public TenantBuilder<Impl> tenant(String id) {
            segments.add(new Segment(Tenant.class, id));
            return new TenantBuilder<>(segments, constructor);
        }

        public RelationshipBuilder<Impl> relationship(String id) {
            segments.add(new Segment(Relationship.class, id));
            return new RelationshipBuilder<>(segments, constructor);
        }
    }

    public static class RelationshipBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {

        RelationshipBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class TenantBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {

        TenantBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public EnvironmentBuilder<Impl> environment(String id) {
            segments.add(new Segment(Environment.class, id));
            return new EnvironmentBuilder<>(segments, constructor);
        }

        public ResourceTypeBuilder<Impl> resourceType(String id) {
            segments.add(new Segment(ResourceType.class, id));
            return new ResourceTypeBuilder<>(segments, constructor);
        }

        public MetricTypeBuilder<Impl> metricType(String id) {
            segments.add(new Segment(MetricType.class, id));
            return new MetricTypeBuilder<>(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class ResourceTypeBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {
        ResourceTypeBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class MetricTypeBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {
        MetricTypeBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class EnvironmentBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {
        EnvironmentBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public FeedBuilder<Impl> feed(String id) {
            segments.add(new Segment(Feed.class, id));
            return new FeedBuilder<>(segments, constructor);
        }

        public ResourceBuilder<Impl> resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return new ResourceBuilder<>(segments, constructor);
        }

        public MetricBuilder<Impl> metric(String id) {
            segments.add(new Segment(Metric.class, id));
            return new MetricBuilder<>(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class FeedBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {

        FeedBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public ResourceBuilder<Impl> resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return new ResourceBuilder<>(segments, constructor);
        }

        public MetricBuilder<Impl> metric(String id) {
            segments.add(new Segment(Metric.class, id));
            return new MetricBuilder<>(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class ResourceBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {

        ResourceBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public ResourceBuilder<Impl> resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return this;
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class MetricBuilder<Impl extends AbstractPath<Impl>> extends AbstractBuilder<Impl> {

        MetricBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }
}
