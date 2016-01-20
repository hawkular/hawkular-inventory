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
package org.hawkular.inventory.paths;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
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
 * <li><b>t</b> - tenant,
 * <li><b>e</b> - environment,
 * <li><b>rt</b> - resource type,
 * <li><b>mt</b> - metric type,
 * <li><b>f</b> - feed,
 * <li><b>m</b> - metric,
 * <li><b>r</b> - resource,
 * <li><b>rl</b> - relationship
 * <li><b>d</b> - structured data (configurations)
 * <p> In addition to that, the relative paths can contain the special "up" token - <code>..</code> - instead of the
 * {@code type:id} pair. E.g. a relative path may look like this: {@code ../e;production/r;myResource}.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public abstract class Path {

    public static final char TYPE_DELIM = ';';
    public static final char PATH_DELIM = '/';

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
        this.path = Collections.unmodifiableList(path);
    }

    static Path fromString(String path, boolean shouldBeAbsolute, ExtenderConstructor extenderConstructor,
            EnhancedTypeProvider typeProvider) {

        Extender extender = extenderConstructor.create(0, new ArrayList<>());

        int startPos = 0;

        try {
            if (shouldBeAbsolute) {
                if (!path.isEmpty() && path.charAt(0) == PATH_DELIM) {
                    startPos = 1;
                } else {
                    throw new IllegalArgumentException("Supplied path '"+ path +"' is not absolute.");
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

    public static Path fromString(String path) {
        if (path.charAt(0) == PATH_DELIM) {
            return CanonicalPath.fromString(path);
        } else {
            return RelativePath.fromString(path);
        }
    }

    /**
     * Parses the provided path using the type provider ({@link org.hawkular.inventory.paths.Path.TypeProvider})
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

    /**
     * This is mostly meant as a helper to REST API.
     *
     * If the provided path is canonical (starts with a slash) the {@code canonicalPathOrigin} together with the
     * {@code intendedFinalType} will be used to construct a full canonical path from the potentially untyped input.
     *
     * If the input path is relative, the {@code relativePathsOrigin} will be used instead.
     *
     * @param path the potentially partially untyped input path
     * @param canonicalPathsOrigin the origin to "prefix" the path if it is canonical
     * @param relativePathsOrigin the origin to resolve the relative path against (if the input path is relative)
     * @param intendedFinalType the intended type of the final segment of the path
     */
    public static Path fromPartiallyUntypedString(String path, CanonicalPath canonicalPathsOrigin,
            CanonicalPath relativePathsOrigin, SegmentType intendedFinalType) {
        if (path.charAt(0) == PATH_DELIM) {
            return CanonicalPath.fromPartiallyUntypedString(path, canonicalPathsOrigin, intendedFinalType);
        } else {
            return RelativePath.fromPartiallyUntypedString(path, relativePathsOrigin, intendedFinalType);
        }
    }

    protected abstract Path newInstance(int startIdx, int endIx, List<Segment> segments);

    /**
     * Tries to convert this path to a relative path. This can result in a new instance construction if this instance
     * is a canonical path.
     *
     * @return this instance converted to RelativePath
     */
    public abstract RelativePath toRelativePath();

    /**
     * Tries to convert this path to a canonical path. This can result in a new instance construction if this instance
     * is a relative path.
     *
     * @return this instance converted to CanonicalPath
     * @throws IllegalArgumentException if this instance is a relative path and it cannot be converted to canonical path
     */
    public abstract CanonicalPath toCanonicalPath();

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
        return startIdx >= 0 && endIdx > startIdx && endIdx <= path.size();
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
     * @return the top segment in this path or null if this path is not {@link #isDefined() defined}.
     */
    public Segment getTop() {
        return isDefined() ? path.get(startIdx) : null;
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
     * represents the segment of the root entity and the last element of the array is the segment of this path
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
                return idx <= endIdx;
            }

            @Override
            public Path next() {
                if (idx > endIdx) {
                    throw new NoSuchElementException();
                }
                return newInstance(startIdx, idx++, path);
            }
        };
    }

    @Override
    public int hashCode() {
        int ret = 0;
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

        List<Segment> thisSegs = getPath();
        List<Segment> thatSegs = other.getPath();

        return thisSegs.equals(thatSegs);
    }

    @Override
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

    @FunctionalInterface
    protected interface ExtenderConstructor {
        Extender create(int from, List<Segment> segments);
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

    private static class ParsingProgress {
        private final String source;
        private int pos;

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

    private static final class Decoder {
        private final EnhancedTypeProvider typeProvider;

        Decoder(EnhancedTypeProvider typeProvider) {
            this.typeProvider = typeProvider;
        }

        Segment decodeNext(ParsingProgress progress) {
            StringBuilder currentId = new StringBuilder();
            String currentTypeString = null;

            //0 = reading type ordinal
            //1 = reading id
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
                                            progress.getPos() + " in '" + progress.getSource() + "'.");
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
                            case PATH_DELIM:
                                break loop;
                            default:
                                currentId.append(c);
                        }
                        break;
                }
            }

            //we've finished reading the source. So we need to emit the segment.
            String currentIdString = currentId.toString();
            if (currentIdString.isEmpty()) {
                currentIdString = currentTypeString;
                currentTypeString = null;

                //if we saw a type delimiter but then found no other id characters then consider the type delimiter part
                //of the ID
                if (state == 1) {
                    currentIdString += TYPE_DELIM;
                }
            }

            currentIdString = PathSegmentCodec.decode(currentIdString);

            Segment ret = typeProvider.deduceSegment(currentTypeString, currentIdString, progress.isFinished());

            if (ret == null) {
                throw new IllegalArgumentException("Unrecognized entity type '" + currentTypeString + "' for segment"
                        + " with id: '" + currentIdString + "' in path '" + progress.getSource()
                        + "'. The following types are recognized: " + typeProvider.getValidTypeName());
            }

            typeProvider.segmentParsed(ret);

            return ret;
        }
    }

    static final class Encoder {
        private final Function<Segment, Boolean> requiresId;

        public Encoder(Function<Segment, Boolean> requiresId) {
            this.requiresId = requiresId;
        }

        public String encode(String prefix, Path path) {
            StringBuilder bld = new StringBuilder(prefix);

            for (Segment seg : path.getPath()) {
                SegmentType segmentType = seg.getElementType();
                if (segmentType.isSerializable()) {
                    bld.append(segmentType.getSerialized());
                }

                if (seg.getElementId() != null && requiresId.apply(seg)) {
                    if (segmentType.isSerializable()) {
                        bld.append(TYPE_DELIM);
                    }

                    bld.append(PathSegmentCodec.encode(seg.getElementId()));
                }
                bld.append(PATH_DELIM);
            }
            return bld.delete(bld.length() - 1, bld.length()).toString();
        }
    }

    public static final class Segment {
        private final SegmentType elementType;
        private final String entityId;

        private Segment() {
            this((SegmentType) null, (String) null);
        }

        public Segment(SegmentType elementType, String entityId) {
            this.entityId = entityId;
            this.elementType = elementType;
        }

        public <R, P> R accept(ElementTypeVisitor<R, P> visitor, P parameter) {
            return ElementTypeVisitor.accept(elementType, visitor, parameter);
        }

        public String getElementId() {
            return entityId;
        }

        public SegmentType getElementType() {
            return elementType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Segment)) return false;

            Segment segment = (Segment) o;

            return elementType == segment.elementType && Objects.equals(entityId, segment.entityId);

        }

        @Override
        public int hashCode() {
            int result = elementType.hashCode();
            result = 31 * result + entityId.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Segment[" + "entityId='" + entityId + '\'' + ", entityType="
                    + (elementType == null ? "null" : elementType.getSimpleName()) + ']';
        }
    }

    /**
     * While {@link CanonicalPath.Builder} or {@link RelativePath.Builder} provide compile-time-safe canonical path
     * construction, this class provides the same behavior at runtime, throwing {@link IllegalArgumentException}s if
     * the segments being added to a path are invalid in given context.
     */
    public abstract static class Extender {
        private final List<Segment> segments;
        private final Function<List<Segment>, List<SegmentType>> validProgressions;
        private final int from;
        private int checkIndex;

        /**
         * Constructs a new extender
         *
         * @param from              the start index in segments to be used by the constructed path
         * @param segments          the list of already existing segments.
         * @param mergeWithInitial  if true, the extensions will first be compared against the segments provided in the
         *                          constructor and if they correspond, they will be "merged" instead of extending the
         *                          initial segments
         * @param validProgressions given the current path, return the valid types of the next segment
         */
        Extender(int from, List<Segment> segments, boolean mergeWithInitial,
                Function<List<Segment>, List<SegmentType>> validProgressions) {
            this.from = from;
            this.segments = segments;
            this.validProgressions = validProgressions;
            this.checkIndex = mergeWithInitial ? from : -1;
        }

        protected abstract Path newPath(int startIdx, int endIdx, List<Segment> segments);

        /**
         * Checks whether this path can be extended by a segment of the provided type.
         *
         * @param segmentType the type of the next segment
         * @return true if the path can be definitely extended by a segment of the provided type, false if definitely
         * not or null if the the extension will only succeed with a correct id (which can happen when this extender is
         * set up with some origin path and has not been extended past it).
         */
        public Boolean canExtendTo(SegmentType segmentType) {
            switch (checkCanProgress(segmentType, false)) {
                case PROCEED_IF_ID_MATCHES:
                    return null;
                case PROCEED:
                case CLEAR_SEGMENTS_AND_JUMP_TO_END:
                case JUMP_TO_END:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Checks whether this path can be extended with the provided segment.
         *
         * @param segment the segment to extend the path with
         * @return true if the path can be extended with the provided segment, false otherwise.
         */
        public boolean canExtendTo(Segment segment) {
            boolean idMatches = checkIndex >= 0 && checkIndex < segments.size() &&
                    Objects.equals(segment, segments.get(checkIndex));
            switch (checkCanProgress(segment.elementType, idMatches)) {
                case PROCEED:
                case CLEAR_SEGMENTS_AND_JUMP_TO_END:
                case JUMP_TO_END:
                    return true;
                default:
                    return false;
            }
        }

        public Extender extend(Segment segment) {
            //matching ids are only important when we are inside the origin path and checkIndex is therefore "inside"
            //the segments
            boolean idMatches = checkIndex >= 0 && checkIndex < segments.size() &&
                    Objects.equals(segment, segments.get(checkIndex));

            switch (checkCanProgress(segment.getElementType(), idMatches)) {
                case PROCEED_IF_ID_MATCHES:
                case INVALID_IN_ORIGIN:
                    throw new IllegalArgumentException("The provided segment " + segment + " does not match the" +
                            " expected origin of the path.");
                case PROCEED:
                    break;
                case CLEAR_SEGMENTS_AND_JUMP_TO_END:
                    segments.clear();
                    checkIndex = 0;
                    break;
                case JUMP_TO_END:
                    checkIndex = segments.size();
                    break;
                default:
                    List<Segment> currentSegs = checkIndex >= 0 ? segments.subList(from, checkIndex) : segments;

                    List<SegmentType> progress = validProgressions.apply(currentSegs);

                    throw new IllegalArgumentException("The provided segment " + segment + " is not valid extension" +
                            " of the path: " + currentSegs +
                            (progress == null ? ". There are no further extensions possible."
                                    : ". Valid extension types are: "
                                            + progress.stream().map(SegmentType::getSimpleName)
                                                    .collect(toList())));
            }

            if (checkIndex < 0 || checkIndex >= segments.size()) {
                segments.add(segment);
            }

            if (checkIndex >= 0) {
                checkIndex++;
            }

            return this;
        }

        protected void removeLastSegment() {
            if (segments.size() > 0) {
                segments.remove(segments.size() - 1);
                if (checkIndex > 0) {
                    checkIndex--;
                }
            }
        }

        public Extender extend(Collection<Segment> segments) {
            segments.forEach(this::extend);
            return this;
        }

        public Extender extend(SegmentType type, String id) {
            return extend(new Segment(type, id));
        }

        public Path get() {
            return newPath(from, segments.size(), segments);
        }

        private ProgressCheck checkCanProgress(SegmentType nextSegmentType, boolean idMatches) {
            int indexToCheck = checkIndex;
            ProgressCheck ret = ProgressCheck.PROCEED;

            if (checkIndex >= 0 && checkIndex < segments.size()) {
                Segment matching = segments.get(checkIndex);
                if (nextSegmentType.equals(matching.elementType)) {
                    //ok, the the caller might be adding a segment that matches our initial path
                    return idMatches ? ProgressCheck.PROCEED : ProgressCheck.PROCEED_IF_ID_MATCHES;
                } else {
                    if (checkIndex == from) {
                        // slight hack commences ;)
                        // the reasoning behind this is that a) this is the first segment of a path we're creating
                        // and b) we're adding a relationship segment.
                        // Relationships are top-level and cannot be followed by anything else.
                        // Also relationships are independent of any entity hierarchy so it doesn't really make sense
                        // to "force them under" some custom "root path".
                        if (SegmentType.rl.equals(nextSegmentType)) {
                            ret = ProgressCheck.CLEAR_SEGMENTS_AND_JUMP_TO_END;
                            indexToCheck = 0;
                        } else {
                            // this is the first extension we're getting. if it does not match the expected segment
                            // in the initial list then that means that the caller doesn't actually take into account
                            // the fact that they can go through the initial list (or is not actually even aware of it).
                            ret = ProgressCheck.JUMP_TO_END;
                            indexToCheck = segments.size();
                        }
                    } else {
                        return ProgressCheck.INVALID_IN_ORIGIN;
                    }
                }
            }

            List<Segment> currentSegs = indexToCheck >= 0 ? segments.subList(from, indexToCheck) : segments;

            List<SegmentType> progress = validProgressions.apply(currentSegs);

            if (progress == null || !progress.contains(nextSegmentType)) {
                //update the checkIndex so that the error reporting is correct in the calling method
                checkIndex = indexToCheck;
                return ProgressCheck.INVALID;
            }

            return ret;
        }

        private enum ProgressCheck {
            PROCEED, PROCEED_IF_ID_MATCHES, INCREMENT_INITIAL_POSITION, CLEAR_SEGMENTS_AND_JUMP_TO_END, JUMP_TO_END,
            INVALID_IN_ORIGIN, INVALID
        }
    }

    public static class StructuredDataHintingTypeProvider implements TypeProvider {

        private boolean insideDataEntity;

        @Override
        public void segmentParsed(Segment segment) {
            insideDataEntity = SegmentType.d.equals(segment.getElementType());
        }

        @Override
        public Segment deduceSegment(String type, String id, boolean isLast) {
            if (type == null && insideDataEntity) {
                return new Segment(SegmentType.sd, id);
            }
            return null;
        }

        @Override
        public void finished() {
            insideDataEntity = false;
        }
    }

    /**
     * This is a type provider ({@link org.hawkular.inventory.paths.Path.TypeProvider}) implementation that tries
     * to deduce the types on the path based on the current position and the intended type of the final segment of
     * the path.
     */
    public static class HintedTypeProvider extends StructuredDataHintingTypeProvider {
        private final SegmentType intendedFinalType;
        private final Extender extender;

        /**
         * Constructs a new instance.
         *
         * @param intendedFinalType the anticipated type of the final segment
         * @param extender          an initial path
         */
        public HintedTypeProvider(SegmentType intendedFinalType, Extender extender) {
            this.intendedFinalType = intendedFinalType;
            this.extender = extender;
        }

        @Override
        public void segmentParsed(Segment segment) {
            extender.extend(segment);
        }

        @Override
        public Segment deduceSegment(String type, String id, boolean isLast) {
            if (type != null && !type.isEmpty()) {
                //we're here only to figure out what the default handler couldn't, if there was a type
                //the default handler should have figured it out and we have no additional information
                //to resolve the situation.
                return null;
            }

            CanonicalPath full = extender.get().toCanonicalPath();

            SegmentType currentType = full.getDepth() >= 0 ? full.getSegment().getElementType() : null;

            SegmentType nextStep = unambiguousPathNextStep(intendedFinalType, currentType, isLast, new HashMap<>());

            if (nextStep != null) {
                return new Segment(nextStep, id);
            }

            return null;
        }

        @Override
        public void finished() {
        }

        private SegmentType unambiguousPathNextStep(SegmentType targetType, SegmentType currentType,
                boolean isLast, Map<SegmentType, Boolean> visitedTypes) {

            if (targetType.equals(currentType)) {
                return targetType;
            }

            Set<SegmentType> ret = new HashSet<>();

            fillPossiblePathsToTarget(targetType, currentType, ret, visitedTypes, true);

            if (ret.size() == 0) {
                return null;
            } else if (ret.size() == 1) {
                return ret.iterator().next();
            } else if (isLast) {
                //there are multiple progressions to the intended type possible, but we're processing the
                //last path segment. So if one of the possible progressions is the intended type itself,
                //we're actually good.
                if (ret.contains(intendedFinalType)) {
                    return intendedFinalType;
                }
            }

            throw new IllegalArgumentException("Cannot unambiguously deduce types of the untyped path" +
                    " segments.");
        }

        private boolean fillPossiblePathsToTarget(SegmentType targetType, SegmentType currentType,
                Set<SegmentType> result, Map<SegmentType, Boolean> visitedTypes, boolean isStart) {

            if (targetType.equals(currentType)) {
                if (isStart) {
                    result.add(currentType);
                }
                return true;
            }

            List<SegmentType> options = CanonicalPath.VALID_PROGRESSIONS.get(currentType);
            if (options == null || options.isEmpty()) {
                return false;
            }

            if (options != null && options.contains(targetType) && isStart) {
                result.add(targetType);
                return true;
            }

            boolean matched = false;

            for (SegmentType option : options) {
                if (!visitedTypes.containsKey(option)) {
                    visitedTypes.put(option, false);

                    if (fillPossiblePathsToTarget(targetType, option, result, visitedTypes, false)) {
                        if (isStart) {
                            result.add(option);
                        }

                        visitedTypes.put(option, true);
                        matched = true;
                    }
                } else {
                    matched |= visitedTypes.get(option);
                }
            }

            return matched;
        }
    }

    abstract static class AbstractBuilder<Impl extends Path> {
        protected final List<Segment> segments;
        protected final Constructor<Impl> constructor;

        AbstractBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            this.segments = segments;
            this.constructor = constructor;
        }

        protected Impl get() {
            return constructor.create(0, segments.size(), segments);
        }
    }

    abstract static class Builder<Impl extends Path,
            TB extends TenantBuilder<Impl, EB, RTB, MTB, OTB, SDB, MPB, FB, RB, MB>,
            EB extends EnvironmentBuilder<Impl, RB, MB, RTB, MTB, OTB, SDB>,
            RTB extends ResourceTypeBuilder<Impl, OTB, SDB>,
            MTB extends MetricTypeBuilder<Impl>,
            RLB extends RelationshipBuilder<Impl>,
            OTB extends OperationTypeBuilder<Impl, SDB>,
            SDB extends StructuredDataBuilder<Impl, SDB>,
            MPB extends MetadataPackBuilder<Impl>,
            FB extends FeedBuilder<Impl, RTB, MTB, RB, MB, OTB, SDB>,
            RB extends ResourceBuilder<Impl, RB, MB, SDB>,
            MB extends MetricBuilder<Impl>> extends AbstractBuilder<Impl> {

        Builder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public TB tenant(String id) {
            segments.add(new Segment(SegmentType.t, id));
            return tenantBuilder(segments);
        }

        public RLB relationship(String id) {
            segments.add(new Segment(SegmentType.rl, id));
            return relationshipBuilder(segments);
        }

        protected abstract TB tenantBuilder(List<Segment> segments);

        protected abstract RLB relationshipBuilder(List<Segment> segments);
    }

    abstract static class RelationshipBuilder<Impl extends Path> extends AbstractBuilder<Impl> {

        RelationshipBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        @Override
        public Impl get() {
            return super.get();
        }
    }

    abstract static class TenantBuilder<Impl extends Path,
            EB extends EnvironmentBuilder<Impl, RB, MB, RTB, MTB, OTB, SDB>,
            RTB extends ResourceTypeBuilder<Impl, OTB, SDB>,
            MTB extends MetricTypeBuilder<Impl>,
            OTB extends OperationTypeBuilder<Impl, SDB>,
            SDB extends StructuredDataBuilder<Impl, SDB>,
            MPB extends MetadataPackBuilder<Impl>,
            FB extends FeedBuilder<Impl, RTB, MTB, RB, MB, OTB, SDB>,
            RB extends ResourceBuilder<Impl, RB, MB, SDB>,
            MB extends MetricBuilder<Impl>>
            extends AbstractBuilder<Impl> {

        TenantBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public FB feed(String id) {
            segments.add(new Segment(SegmentType.f, id));
            return feedBuilder(segments);
        }

        public EB environment(String id) {
            segments.add(new Segment(SegmentType.e, id));
            return environmentBuilder(segments);
        }

        public RTB resourceType(String id) {
            segments.add(new Segment(SegmentType.rt, id));
            return resourceTypeBuilder(segments);
        }

        public MTB metricType(String id) {
            segments.add(new Segment(SegmentType.mt, id));
            return metricTypeBuilder(segments);
        }

        public MPB metadataPack(String id) {
            segments.add(new Segment(SegmentType.mp, id));
            return metadataPackBuilder(segments);
        }

        @Override
        public Impl get() {
            return super.get();
        }

        protected abstract EB environmentBuilder(List<Segment> segments);

        protected abstract FB feedBuilder(List<Segment> segments);

        protected abstract RTB resourceTypeBuilder(List<Segment> segment);

        protected abstract MTB metricTypeBuilder(List<Segment> segments);

        protected abstract MPB metadataPackBuilder(List<Segment> segments);
    }

    abstract static class ResourceTypeBuilder<Impl extends Path,
            OTB extends OperationTypeBuilder<Impl, SDB>,
            SDB extends StructuredDataBuilder<Impl, SDB>>
            extends AbstractBuilder<Impl> {

        ResourceTypeBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public SDB data(DataRole.ResourceType role) {
            segments.add(new Segment(SegmentType.d, role.name()));
            return structuredDataBuilder(segments);
        }

        public OTB operationType(String id) {
            segments.add(new Segment(SegmentType.ot, id));
            return operationTypeBuilder(segments);
        }

        @Override
        public Impl get() {
            return super.get();
        }

        protected abstract OTB operationTypeBuilder(List<Segment> segments);

        protected abstract SDB structuredDataBuilder(List<Segment> segments);
    }

    abstract static class MetricTypeBuilder<Impl extends Path> extends AbstractBuilder<Impl> {
        MetricTypeBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        @Override
        public Impl get() {
            return super.get();
        }
    }

    abstract static class EnvironmentBuilder<Impl extends Path,
            RB extends ResourceBuilder<Impl, RB, MB, SDB>,
            MB extends MetricBuilder<Impl>,
            RTB extends ResourceTypeBuilder<Impl, OTB, SDB>,
            MTB extends MetricTypeBuilder<Impl>,
            OTB extends OperationTypeBuilder<Impl, SDB>,
            SDB extends StructuredDataBuilder<Impl, SDB>>
            extends AbstractBuilder<Impl> {

        EnvironmentBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public RB resource(String id) {
            segments.add(new Segment(SegmentType.r, id));
            return resourceBuilder(segments);
        }

        public MB metric(String id) {
            segments.add(new Segment(SegmentType.m, id));
            return metricBuilder(segments);
        }

        @Override
        public Impl get() {
            return super.get();
        }

        protected abstract RB resourceBuilder(List<Segment> segments);

        protected abstract MB metricBuilder(List<Segment> segments);
    }

    abstract static class FeedBuilder<Impl extends Path,
            RTB extends ResourceTypeBuilder<Impl, OTB, SDB>,
            MTB extends MetricTypeBuilder<Impl>,
            RB extends ResourceBuilder<Impl, RB, MB, SDB>,
            MB extends MetricBuilder<Impl>,
            OTB extends OperationTypeBuilder<Impl, SDB>,
            SDB extends StructuredDataBuilder<Impl, SDB>> extends AbstractBuilder<Impl> {

        FeedBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public RB resource(String id) {
            segments.add(new Segment(SegmentType.r, id));
            return resourceBuilder(segments);
        }

        public MB metric(String id) {
            segments.add(new Segment(SegmentType.m, id));
            return metricBuilder(segments);
        }

        public MTB metricType(String id) {
            segments.add(new Segment(SegmentType.mt, id));
            return metricTypeBuilder(segments);
        }

        public RTB resourceType(String id) {
            segments.add(new Segment(SegmentType.rt, id));
            return resourceTypeBuilder(segments);
        }

        @Override
        public Impl get() {
            return super.get();
        }

        protected abstract RTB resourceTypeBuilder(List<Segment> segments);

        protected abstract MTB metricTypeBuilder(List<Segment> segments);

        protected abstract RB resourceBuilder(List<Segment> segments);

        protected abstract MB metricBuilder(List<Segment> segments);
    }

    abstract static class ResourceBuilder<Impl extends Path,
            This extends ResourceBuilder<Impl, This, MB, SDB>,
            MB extends MetricBuilder<Impl>,
            SDB extends StructuredDataBuilder<Impl, SDB>>
            extends AbstractBuilder<Impl> {

        ResourceBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        @SuppressWarnings("unchecked")
        public This resource(String id) {
            segments.add(new Segment(SegmentType.r, id));
            return (This) this;
        }

        public SDB data(DataRole.Resource role) {
            segments.add(new Segment(SegmentType.d, role.name()));
            return structuredDataBuilder(segments);
        }

        public MB metric(String id) {
            segments.add(new Segment(SegmentType.m, id));
            return metricBuilder(segments);
        }

        @Override
        public Impl get() {
            return super.get();
        }

        protected abstract MB metricBuilder(List<Segment> segments);

        protected abstract SDB structuredDataBuilder(List<Segment> segments);
    }

    abstract static class MetricBuilder<Impl extends Path> extends AbstractBuilder<Impl> {

        MetricBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        @Override
        public Impl get() {
            return super.get();
        }
    }

    abstract static class StructuredDataBuilder<Impl extends Path,
            This extends StructuredDataBuilder<Impl, This>> extends AbstractBuilder<Impl> {

        StructuredDataBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        @SuppressWarnings("unchecked")
        public This key(String name) {
            segments.add(new Segment(SegmentType.sd, name));
            return (This) this;
        }

        @SuppressWarnings("unchecked")
        public This index(int index) {
            segments.add(new Segment(SegmentType.sd, "" + index));
            return (This) this;
        }

        @Override
        public Impl get() {
            return super.get();
        }
    }

    abstract static class OperationTypeBuilder<Impl extends Path,
            SDB extends StructuredDataBuilder<Impl, SDB>> extends AbstractBuilder<Impl> {
        OperationTypeBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        public SDB data(DataRole.OperationType role) {
            segments.add(new Segment(SegmentType.d, role.name()));
            return structuredDataBuilder(segments);
        }

        @Override
        public Impl get() {
            return super.get();
        }

        protected abstract SDB structuredDataBuilder(List<Segment> segments);
    }

    abstract static class MetadataPackBuilder<Impl extends Path> extends AbstractBuilder<Impl> {
        MetadataPackBuilder(List<Segment> segments, Constructor<Impl> constructor) {
            super(segments, constructor);
        }

        @Override
        public Impl get() {
            return super.get();
        }
    }
}
