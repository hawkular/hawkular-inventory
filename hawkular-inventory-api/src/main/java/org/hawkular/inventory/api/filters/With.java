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
package org.hawkular.inventory.api.filters;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class With {
    private With() {

    }

    public static Ids id(String id) {
        return new Ids(id);
    }

    public static Ids ids(String... ids) {
        return new Ids(ids);
    }

    @SafeVarargs
    public static Types types(Class<? extends Entity<?, ?>>... types) {
        return new Types(types);
    }

    public static Types type(Class<? extends Entity<?, ?>> type) {
        return new Types(type);
    }

    public static Types types(SegmentType... types) {
        @SuppressWarnings("unchecked")
        Class<? extends Entity<?, ?>>[] clss = Stream.of(types).map(With::convertAsEntityType)
                .toArray(Class[]::new);

        return new Types(clss);
    }

    public static Types type(SegmentType type) {
        return new Types(convertAsEntityType(type));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Entity<?, ?>> convertAsEntityType(SegmentType type) {
        Class<? extends AbstractElement<?, ?>> cls = AbstractElement.toElementClass(type);
        if (Relationship.class.isAssignableFrom(cls)) {
            throw new IllegalArgumentException("Type filter not applicable to relationships.");
        }

        return (Class<? extends Entity<?, ?>>) cls;
    }

    public static CanonicalPaths path(CanonicalPath path) {
        return new CanonicalPaths(path);
    }

    public static CanonicalPaths paths(CanonicalPath... paths) {
        return new CanonicalPaths(paths);
    }

    /**
     * Constructs a new filter to only contain elements at the current position in the graph traversal that
     * can also be reached using the provided relative path.
     *
     * <p>If the label is null, the relative path is applied directly to the current position in the traversal.
     *
     * @param markerLabel the filter chain this filter is being added to can contain a {@link Marker} with this
     *                    label that marks the position against which the relative path would be resolved against
     * @param path        the relative path of the elements.
     * @return a new filter
     */
    public static RelativePaths relativePath(String markerLabel, RelativePath path) {
        return new RelativePaths(markerLabel, path);
    }

    /**
     * Constructs a new filter to only contain elements at the current position in the graph traversal that
     * can also be reached using at least one of the provided relative paths.
     *
     * <p>If the label is null, the relative path is applied directly to the current position in the traversal.
     *
     * @param markerLabel the filter chain this filter is being added to can contain a {@link Marker} with this
     *                    label that marks the position against which the relative path would be resolved against
     * @param paths       the relative paths of the elements.
     * @return a new filter
     * @see #relativePath(String, RelativePath)
     */
    public static RelativePaths relativePaths(String markerLabel, RelativePath... paths) {
        return new RelativePaths(markerLabel, paths);
    }

    /**
     * Filters for entities that have a property of given name regardless of its value.
     *
     * @param name the name of the property
     * @return the filter
     */
    public static PropertyValues property(String name) {
        return new PropertyValues(name);
    }

    /**
     * Filters for entities that have a property of given name equal to given value
     *
     * @param name  the name of the property
     * @param value the desired value
     * @return the filter
     */
    public static PropertyValues propertyValue(String name, Object value) {
        return new PropertyValues(name, value);
    }

    /**
     * Filters for entities that have a property of given name equal to one of the provided values.
     *
     * @param name   the name of the property
     * @param values the possible values
     * @return the filter
     */
    public static PropertyValues propertyValues(String name, Object... values) {
        return new PropertyValues(name, values);
    }

    /**
     * Checks whether there is data on the provided relative path. This filter is only really applicable
     * on the {@link org.hawkular.inventory.api.model.DataEntity}. I.e. the relative path is always resolved
     * against the current position in the traversal, which is assumed to resolve to a data entity.
     *
     * @param dataPath the relative path to the data
     * @return the filter
     */
    public static DataAt dataAt(RelativePath dataPath) {
        return new DataAt(dataPath);
    }

    /**
     * Filters for structured data with exactly the specified value.
     *
     * @param value the value to look for
     * @return the filter
     */
    public static DataValued dataValue(Serializable value) {
        return new DataValued(value);
    }

    /**
     * Filters for structured data with one of the provided types.
     *
     * @param types the types to filter the structured data with
     * @return the filter
     * @see StructuredData#getType()
     */
    public static DataOfTypes dataOfTypes(StructuredData.Type... types) {
        return new DataOfTypes(types);
    }

    /**
     * Looks for resource or metric types that have the same identity hash as the entity/ies on the current position
     * in the filter chain.
     *
     * @return the filter
     */
    public static SameIdentityHash sameIdentityHash() {
        return SameIdentityHash.INSTANCE;
    }

    public static Names name(String name) {
        return names(name);
    }

    public static Names names(String... names) {
        return new Names(names);
    }

    public static final class Ids extends Filter {

        private final String[] ids;

        public Ids(String... ids) {
            this.ids = ids;
        }

        public String[] getIds() {
            return ids;
        }

        @Override
        public String toString() {
            return  "Ids" + Arrays.asList(ids).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Ids)) return false;

            Ids other = (Ids) o;

            return Arrays.equals(ids, other.ids);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ids);
        }
    }

    public static final class Types extends Filter {
        private final Class<? extends Entity<?, ?>>[] types;

        @SafeVarargs
        public Types(Class<? extends Entity<?, ?>>... types) {
            this.types = types;
        }

        public Class<? extends Entity<?, ?>>[] getTypes() {
            return types;
        }

        @JsonIgnore
        public SegmentType[] getSegmentTypes() {
            return Arrays.stream(types).map(AbstractElement::segmentTypeFromType).toArray(SegmentType[]::new);
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder("Types[");
            if (types.length > 0) {
                ret.append(types[0].getSimpleName());

                for(int i = 1; i < types.length; ++i) {
                    ret.append(", ").append(types[i].getSimpleName());
                }
            }
            ret.append("]");
            return ret.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Types)) return false;

            Types other = (Types) o;

            return Arrays.equals(types, other.types);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(types);
        }
    }

    public static final class CanonicalPaths extends Filter {
        private final CanonicalPath[] paths;

        public CanonicalPaths(CanonicalPath... paths) {
            this.paths = paths;
        }

        public CanonicalPath[] getPaths() {
            return paths;
        }

        @Override
        public String toString() {
            return "CanonicalPaths" + Arrays.asList(paths).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CanonicalPaths)) return false;

            CanonicalPaths other = (CanonicalPaths) o;

            return Arrays.equals(paths, other.paths);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(paths);
        }
    }

    public static final class RelativePaths extends Filter {
        private final String markerLabel;
        private final RelativePath[] paths;

        public RelativePaths(String markerLabel, RelativePath... paths) {
            this.markerLabel = markerLabel;
            this.paths = paths;
        }

        public String getMarkerLabel() {
            return markerLabel;
        }

        public RelativePath[] getPaths() {
            return paths;
        }

        @Override
        public String toString() {
            return "RelativePaths" + Arrays.asList(paths).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RelativePaths)) return false;

            RelativePaths other = (RelativePaths) o;

            return Arrays.equals(paths, other.paths);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(paths);
        }
    }

    public static final class PropertyValues extends Filter {
        private final String name;
        private final Object[] values;

        public PropertyValues(String name, Object... values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public Object[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            return "PropertyValues[" + "name='" + name + '\'' + ", values=" + Arrays.toString(values) + ']';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PropertyValues)) return false;

            PropertyValues that = (PropertyValues) o;

            return name.equals(that.name) && Arrays.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(values);
            return result;
        }
    }

    public static final class DataValued extends Filter {
        private final Serializable value;

        public DataValued(Serializable value) {
            this.value = value;
        }

        public Serializable getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "DataValued[" + "value=" + value + ']';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DataValued)) return false;

            DataValued that = (DataValued) o;

            return Objects.equals(value, that.value);

        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }

    public static final class DataAt extends Filter {
        private final RelativePath dataPath;

        public DataAt(RelativePath dataPath) {
            this.dataPath = dataPath;
        }

        public RelativePath getDataPath() {
            return dataPath;
        }

        @Override
        public String toString() {
            return "DataAt[" + "dataPath=" + dataPath + ']';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DataAt)) return false;

            DataAt dataAt = (DataAt) o;

            return dataPath.equals(dataAt.dataPath);
        }

        @Override
        public int hashCode() {
            return dataPath.hashCode();
        }
    }

    public static final class DataOfTypes extends Filter {
        private final StructuredData.Type[] types;

        public DataOfTypes(StructuredData.Type... types) {
            this.types = types;
        }

        public StructuredData.Type[] getTypes() {
            return types;
        }

        @Override
        public String toString() {
            return "DataOfTypes[" + "types=" + Arrays.toString(types) + ']';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DataOfTypes)) return false;

            DataOfTypes that = (DataOfTypes) o;

            return Arrays.equals(types, that.types);

        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(types);
        }
    }

    public static final class SameIdentityHash extends Filter {
        public static final SameIdentityHash INSTANCE = new SameIdentityHash();

        public SameIdentityHash() {
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || (obj instanceof SameIdentityHash);
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "SameIdentityHash";
        }
    }

    public static final class Names extends Filter {
        private final String[] names;

        public Names(String... names) {
            this.names = names;
        }

        public String[] getNames() {
            return names;
        }

        @Override
        public String toString() {
            return "Names[names=" + Arrays.toString(names) + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Names)) return false;

            Names that = (Names) o;

            return Arrays.equals(names, that.names);

        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(names);
        }
    }
}
