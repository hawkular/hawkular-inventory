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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.Path.Segment;

/**
 * An enumeration of all possible {@link Path} {@link Segment} types.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public enum SegmentType {
    /** Tenant */
    t("Tenant"),
    /** Environment */
    e("Environment"),
    /** Feed */
    f("Feed"),
    /** Metric */
    m("Metric"),
    /** Resource */
    r("Resource"),
    /** ResourceType */
    rt("ResourceType"),
    /** MetricType */
    mt("MetricType"),
    /** Relationship */
    rl("Relationship"),
    /** DataEntity */
    d("DataEntity"),
    /** OperationType */
    ot("OperationType"),
    /** MetadataPack */
    mp("MetadataPack"),
    /** StructuredData */
    sd("StructuredData") {
        @Override
        public boolean isSerializable() {
            return false;
        }
    },
    up("Up", "..") {
        @Override
        public boolean isAllowedInCanonical() {
            return false;
        }
    };

    private static final Map<String, SegmentType> entriesByShortName;
    private static final Map<String, SegmentType> entriesBySimpleName;
    private static final Set<String> canonicalShortNames;
    private static final Set<SegmentType> relativeShortNames;

    /**
     * Per convention, the {@code null} value is interpreted as any subclass of
     * {@code org.hawkular.inventory.api.model.Entity}
     */
    public static final SegmentType ANY_ENTITY = null;

    static {
        Map<String, SegmentType> tmpByShortName = new HashMap<>();
        Map<String, SegmentType> tmpBySimpleName = new HashMap<>();
        Set<String> canonicalTmp = new HashSet<>();
        Set<SegmentType> relativeTmp = new HashSet<>();
        for (SegmentType segmentType : values()) {
            tmpByShortName.put(segmentType.name(), segmentType);
            tmpBySimpleName.put(segmentType.getSimpleName(), segmentType);
            if (segmentType.isAllowedInCanonical()) {
                canonicalTmp.add(segmentType.name());
            }
            relativeTmp.add(segmentType);
        }
        tmpByShortName.put(up.serialized, up);
        entriesByShortName = Collections.unmodifiableMap(tmpByShortName);
        entriesBySimpleName = Collections.unmodifiableMap(tmpBySimpleName);
        canonicalShortNames = Collections.unmodifiableSet(canonicalTmp);
        relativeShortNames = Collections.unmodifiableSet(relativeTmp);
    }

    /** The simple class name of the related Inventory model element */
    private final String simpleName;
    private final String serialized;

    private SegmentType(String simpleName) {
        this.simpleName = simpleName;
        this.serialized = name();
    }

    private SegmentType(String simpleName, String serialized) {
        this.simpleName = simpleName;
        this.serialized = serialized;
    }

    /**
     * A static {@link HashMap} based and {@code null}-tolerant alternative to {@link #valueOf(String)}.
     *
     * @param type the short name such as {@code "e"} or {@code "f"} to map to a {@link SegmentType}
     * @return the {@link SegmentType} corresponding to the given {@code type} or <code>null</code> of there is no
     *         corresponding {@link SegmentType}.
     */
    public static SegmentType fastValueOf(String type) {
        return entriesByShortName.get(type);
    }

    /**
     * Returns a {@link SegmentType} corresponding to the given {@code elementType} or {@code null} if no corresponding
     * type can be found.
     * <p>
     * Performance note: this implementation looks up the given {@code elementType} using its
     * {@code elementType.getSimpleName()}. Because {@link Class#getSimpleName()} invokes {@link String#substring(int)}
     * this implementation may suboptimal in many use cases.
     * <p>
     * Therefore, the usage of this method should be avoided in all situations where there is a better alternative, such
     * as using {@code SEGMENT_TYPE} constants defined in the subclasses of
     * {@code org.hawkular.inventory.api.model.Entity} or
     * {@code org.hawkular.inventory.api.model.Entity.segmentTypeFromType(Class)}
     *
     * @param elementType the type to map to a {@link SegmentType}
     * @return the {@link SegmentType} corresponding to the given {@code elementType} or {@code null} if no
     *         corresponding type can be found
     */
    public static SegmentType fromElementType(Class<?> elementType) {
        return elementType == null ? null : entriesBySimpleName.get(elementType.getSimpleName());
    }

    /**
     * @return a {@link Set} of short names that may occur in canonical paths
     */
    public static Set<String> getCanonicalShortNames() {
        return canonicalShortNames;
    }

    /**
     * @return a {@link Set} of short names that may occur in relative paths
     */
    public static Set<SegmentType> getRelativeShortNames() {
        return relativeShortNames;
    }

    /**
     * All except for {@link #up} are allowed in {@link CanonicalPath}.
     *
     * @return {@code true} if this type of {@link Segment} may occur in a {@link CanonicalPath}, otherwise
     *         {@code false}
     */
    public boolean isAllowedInCanonical() {
        return true;
    }

    /**
     * We do not serialize {@link #sd}s with a {@code "sd;"} prefix.
     *
     * @return {@code true} unless this is a {@link #sd}, otherwise {@code false}
     */
    public boolean isSerializable() {
        return true;
    }

    /**
     * Returns the representation of this {@link SegmentType} suitable for serialization. In most cases the result is
     * equal to {@link #name()} with the notable exception of {@link #up}, the serialized form of which is {@code ".."}.
     *
     * @return the representation of this {@link SegmentType} suitable for serialization
     */
    public String getSerialized() {
        return serialized;
    }

    /**
     * @return the simple name of the corresponding class in {@code org.hawkular.inventory.api.model} package.
     */
    public String getSimpleName() {
        return simpleName;
    }
}