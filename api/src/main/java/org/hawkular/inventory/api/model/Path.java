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
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
 * <li><b>t</b> - tenant,
 * <li><b>e</b> - environment,
 * <li><b>rt</b> - resource type,
 * <li><b>mt</b> - metric type,
 * <li><b>f</b> - feed,
 * <li><b>m</b> - metric,
 * <li><b>r</b> - resource,
 * <li><b>rl</b> - relationship
 * <p> In addition to that, the relative paths can contain the special "up" token - <code>..</code> - instead of the
 * {@code type:id} pair. E.g. a relative path may look like this: {@code ../e;production/r;myResource}.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public abstract class Path {

    public static final char TYPE_DELIM = ';';
    public static final char PATH_DELIM = '/';
    public static final char ESCAPE_CHAR = '\\';

    //all path instances created from this one in the up(), down() and *iterator() methods will share this list
    //and will only differ in their "myIdx" field.
    protected final List<Segment> path;
    protected final int startIdx;
    protected final int endIdx;

    Path() {
        path = null;
        startIdx = 0;
        endIdx = 0;
    }

    Path(int startIdx, int endIdx, List<Segment> path) {
        this.startIdx = startIdx;
        this.endIdx = endIdx;
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Empty path is not valid.");
        }
        this.path = Collections.unmodifiableList(path);
    }

    protected static Path fromString(String path, boolean shouldBeAbsolute, Map<String, Class<?>> shortNameTypes,
            ExtenderConstructor extenderConstructor, EnhancedTypeProvider typeProvider) {

        Extender extender = extenderConstructor.create(0, new ArrayList<>());

        int startPos = 0;

        try {
            if (shouldBeAbsolute) {
                if (!path.isEmpty() && path.charAt(0) == PATH_DELIM) {
                    startPos = 1;
                } else {
                    throw new IllegalArgumentException("Supplied path is not absolute.");
                }
            }

            ParsingProgress progress = new ParsingProgress(startPos, path);

            Decoder dec = new Decoder(typeProvider);

            while (!progress.isFinished()) {
                Segment seg = dec.decodeNext(progress);
                extender.extend(seg);
            }

            return extender.get();
        } finally {
            if (typeProvider != null) {
                typeProvider.finished();
            }
        }
    }

    @JsonCreator
    public static Path fromString(String path) {
        if (path.charAt(0) == PATH_DELIM) {
            return CanonicalPath.fromString(path);
        } else {
            return RelativePath.fromString(path);
        }
    }

    /**
     * Parses the provided path using the type provider ({@link org.hawkular.inventory.api.model.Path.TypeProvider})
     * to figure out the types of segments that don't explicitly mention it.
     *
     * <p>This is mainly geared at REST API in which this is used to parse the relative paths out of URIs in which
     * the type of some segments can be easily deduced from the endpoint address.
     *
     * @param path         the path to parse
     * @param typeProvider the type provider used to figure out types of segments that don't explicitly mention it
     * @return the parsed path
     */
    public static Path fromPartiallyUntypedString(String path, TypeProvider typeProvider) {
        if (path.charAt(0) == PATH_DELIM) {
            return CanonicalPath.fromPartiallyUntypedString(path, typeProvider);
        } else {
            return RelativePath.fromPartiallyUntypedString(path, typeProvider);
        }
    }

    protected abstract Path newInstance(int startIdx, int endIx, List<Segment> segments);

    /**
     * This is equivalent to merely casting this instance to the {@link RelativePath}.
     *
     * This method merely provides an ever so slightly nicer API, together with the {@link #isRelative()} and
     * {@link #isCanonical()} methods.
     *
     * @return this instance cast to RelativePath
     * @throws ClassCastException if this isn't a relative path
     */
    public RelativePath asRelativePath() {
        return (RelativePath) this;
    }

    /**
     * This is equivalent to merely casting this instance to the {@link CanonicalPath}.
     *
     * This method merely provides an ever so slightly nicer API, together with the {@link #isRelative()} and
     * {@link #isCanonical()} methods.
     *
     * @return this instance cast to CanonicalPath
     * @throws ClassCastException if this isn't a relative path
     */
    public CanonicalPath asCanonicalPath() {
        return (CanonicalPath) this;
    }

    /**
     * @return true if this is an instance of {@link CanonicalPath}, false otherwise
     */
    public boolean isCanonical() {
        return this instanceof CanonicalPath;
    }

    /**
     * @return true if this is an instance of {@link RelativePath}, false otherwise
     */
    public boolean isRelative() {
        return this instanceof RelativePath;
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
    public Path up() {
        return up(1);
    }

    /**
     * Returns a path corresponding to the n-th ancestor in the hierarchy.
     *
     * @param distance the distance of the ancestor from the resource represented by this path object.
     * @return the ancestor path (may be {@link #isDefined() undefined}.
     */
    public Path up(int distance) {
        return newInstance(startIdx, endIdx - distance, path);
    }

    /**
     * If this path was created by going {@link #up() up} from another path, then this method can be used to go back
     * down to the previous paths representing some child resource of the resource represented by this path object.
     *
     * @return a path to a direct child of the resource represented by this path (may be {@link #isDefined() undefined}.
     */
    public Path down() {
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
    public Path down(int distance) {
        return newInstance(startIdx, endIdx + distance, path);
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
     * @return the iterator that ascends the path from the current segment up to the root
     */
    public Iterator<? extends Path> ascendingIterator() {
        return new Iterator<Path>() {
            int idx = endIdx;

            @Override
            public boolean hasNext() {
                return idx > startIdx;
            }

            @Override
            public Path next() {
                if (idx <= startIdx) {
                    throw new NoSuchElementException();
                }
                return newInstance(startIdx, idx--, path);
            }
        };
    }

    /**
     * @return the iterator that descends from the root down to the current segment.
     */
    public Iterator<? extends Path> descendingIterator() {
        return new Iterator<Path>() {
            int idx = startIdx + 1;

            @Override
            public boolean hasNext() {
                return idx < endIdx;
            }

            @Override
            public Path next() {
                if (idx >= endIdx) {
                    throw new NoSuchElementException();
                }
                return newInstance(startIdx, idx++, path);
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

        Path other = (Path) o;

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

    @Override
    @JsonValue
    public String toString() {
        return super.toString();
    }

    @FunctionalInterface
    interface Constructor<Path> {
        Path create(int startIdx, int endIx, List<Segment> segments);
    }

    /**
     * An interface that is used to help parsing partially untyped path strings.
     */
    public interface TypeProvider {

        /**
         * This method gets called during the parsing process after a segment is successfully parsed.
         * This enables the type provider to keep track of the progress of the parsing.
         *
         * @param segment the segment parsed
         */
        void segmentParsed(Segment segment);

        /**
         * This is called during parsing of a single segment when the built-in defaults cannot determine the type and
         * id to use for segment.
         *
         * <p>If this method returns null, the parsing of the path will fail with an {@link IllegalArgumentException}.
         *
         * @param type   the parsed type name
         * @param id     the parsed id
         * @param isLast true if the parsed segment is the last in the path
         * @return the segment based on the provided info or null if the type provider cannot deduce one.
         */
        Segment deduceSegment(String type, String id, boolean isLast);

        /**
         * Called when the parsing of the path finishes (either successfully or unsuccessfully).
         */
        void finished();
    }

    /**
     * Used internally by the decoder to provide "type-safe" progression of the path.
     */
    abstract static class EnhancedTypeProvider implements TypeProvider {

        /**
         * Used only for error reporting.
         *
         * @return the set of all recognized type names
         */
        abstract Set<String> getValidTypeName();
    }

    protected static class ParsingProgress {
        private int pos;
        private final String source;

        public ParsingProgress(int pos, String source) {
            this.pos = pos;
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
        private final EnhancedTypeProvider typeProvider;

        protected Decoder(EnhancedTypeProvider typeProvider) {
            this.typeProvider = typeProvider;
        }

        public Segment decodeNext(ParsingProgress progress) {
            StringBuilder currentId = new StringBuilder();
            String currentTypeString = null;

            //0 = reading type ordinal
            //1 = reading id
            //2 = reading escape char
            int state = 0;

            loop:
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

                                currentTypeString = currentId.toString();
                                currentId.delete(0, currentId.length());
                                state = 1; //reading id
                                break;
                            case PATH_DELIM:
                                currentTypeString = currentId.toString();
                                currentId.delete(0, currentId.length());
                                break loop;
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

                                break loop;
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

            //we've finished reading the source. So we need to emit the segment.
            String currentIdString = currentId.toString();
            if (currentIdString.isEmpty()) {
                currentIdString = currentTypeString;
                currentTypeString = null;
            }

            Segment ret = typeProvider.deduceSegment(currentTypeString, currentIdString, progress.isFinished());

            if (ret == null) {
                throw new IllegalArgumentException("Unrecognized entity type '" + currentTypeString + "' for segment" +
                        " with id: '" + currentIdString + "'. The following types are recognized: " +
                        typeProvider.getValidTypeName());
            }

            typeProvider.segmentParsed(ret);

            return ret;
        }
    }

    protected static class Encoder {
        private final Map<Class<?>, String> typeMap;
        private final Function<Segment, Boolean> requiresId;

        public Encoder(Map<Class<?>, String> typeMap, Function<Segment, Boolean> requiresId) {
            this.typeMap = typeMap;
            this.requiresId = requiresId;
        }

        public String encode(String prefix, Path path) {
            StringBuilder bld = new StringBuilder(prefix);

            for (Segment seg : path.getPath()) {
                String type = typeMap.get(seg.getElementType());
                if (type != null) {
                    bld.append(type);
                }

                if (seg.getElementId() != null && requiresId.apply(seg)) {
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

    @FunctionalInterface
    protected interface ExtenderConstructor {
        Extender create(int from, List<Segment> segments);
    }

    /**
     * While {@link CanonicalPath.Builder} or {@link RelativePath.Builder} provide compile-time-safe canonical path
     * construction, this class provides the same behavior at runtime, throwing {@link IllegalArgumentException}s if
     * the segments being added to a path are invalid in given context.
     */
    public abstract static class Extender {
        protected final List<Segment> segments;
        private final Function<List<Segment>, List<Class<?>>> validProgressions;
        private final int from;

        protected abstract Path newPath(int startIdx, int endIdx, List<Segment> segments);

        /**
         * Constructs a new extender
         *
         * @param from              the start index in segments to be used by the constructed path
         * @param segments          the list of already existing segments.
         * @param validProgressions given the current path, return the valid types of the next segment
         */
        Extender(int from, List<Segment> segments, Function<List<Segment>, List<Class<?>>> validProgressions) {
            this.from = from;
            this.segments = segments;
            this.validProgressions = validProgressions;
        }

        public Extender extend(Segment segment) {
            List<Class<?>> progress = validProgressions.apply(segments);

            if (progress == null || !progress.contains(segment.getElementType())) {
                throw new IllegalArgumentException("The provided segment " + segment + " is not valid extension" +
                        " of the path: " + segments);
            }

            segments.add(segment);
            return this;
        }

        public Extender extend(Class<? extends AbstractElement<?, ?>> type, String id) {
            return extend(new Segment(type, id));
        }

        public Path get() {
            return newPath(from, segments.size(), segments);
        }
    }

    protected abstract static class AbstractBuilder<Impl extends Path> {
        protected final List<Segment> segments;
        protected final Constructor<Impl> constructor;

        AbstractBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            this.segments = segments;
            this.constructor = constructor;
        }
    }

    public static class Builder<Impl extends Path> extends AbstractBuilder<Impl> {
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

    public static class RelationshipBuilder<Impl extends Path> extends AbstractBuilder<Impl> {

        RelationshipBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class TenantBuilder<Impl extends Path> extends AbstractBuilder<Impl> {

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

    public static class ResourceTypeBuilder<Impl extends Path> extends AbstractBuilder<Impl> {
        ResourceTypeBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class MetricTypeBuilder<Impl extends Path> extends AbstractBuilder<Impl> {
        MetricTypeBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    public static class EnvironmentBuilder<Impl extends Path> extends AbstractBuilder<Impl> {
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

    public static class FeedBuilder<Impl extends Path> extends AbstractBuilder<Impl> {

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

    public static class ResourceBuilder<Impl extends Path> extends AbstractBuilder<Impl> {

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

    public static class MetricBuilder<Impl extends Path> extends AbstractBuilder<Impl> {

        MetricBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }
}
