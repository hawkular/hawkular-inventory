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
package org.hawkular.inventory.impl.tinkerpop.spi;

import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__contentHash;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__from;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__identityHash;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__metric_data_type;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__metric_interval;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__sourceCp;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__sourceEid;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__sourceType;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__structuredDataIndex;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__structuredDataKey;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__structuredDataType;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__structuredDataValue_b;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__structuredDataValue_f;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__structuredDataValue_i;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__structuredDataValue_s;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__syncHash;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__targetCp;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__targetEid;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__targetType;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__to;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__unit;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.name;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.paths.ElementTypeVisitor;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class Constants {

    private Constants() {
        //no instances, thank you
    }

    /**
     * The vertices in the graph have certain well-known properties. The __foo form is used internally to decrease the
     * chance of collision with any user defined properties. However, for sorting purposes it's quite cumbersome to use
     * it directly, so the property can have also the user-friendly name (sortName).
     */
    public enum Property {
        /**
         * The user-defined human-readable name of the entity. We don't use the "__" prefix here as with the rest of
         * the properties, because this is not really hidden.
         */
        name(String.class),

        /**
         * This is the name of the property that we use to store the type of the entity represented by the vertex
         */
        __type("type", String.class),

        /**
         * This is the name of the property that we use to store the user-defined ID of the entity represented by the
         * vertex. These ID are required to be unique only amongst their "siblings" as determined by the "contains"
         * hierarchy.
         */
        __eid("id", String.class),

        /**
         * Present on metric type, this is the name of the propety that we use to store the unit of the metric type
         * represented by the vertex.
         */
        __unit("unit", String.class),

        /**
         * Property used on metric type to distinguish type of metric e.g. gauge, counter...
         */
        __metric_data_type("metricDataType", String.class),

        /**
         * Property used to store interval in seconds at which metrics are collected
         */
        __metric_interval("collectionInterval", Long.class),

        /**
         * Property used to store the canonical path of an element.
         */
        __cp("path", String.class),

        /**
         * The type of the data stored by the structured data vertex
         */
        __structuredDataType(String.class),

        /**
         * The key using which a structured data value is stored in a map.
         */
        __structuredDataKey(String.class),

        /**
         * The index on which a structured data value is stored in a list.
         */
        __structuredDataIndex(Integer.class),

        /**
         * The name of the property on the structured data vertex that holds the primitive value of that vertex.
         * List and maps don't hold the value directly but instead have edges going out to the child vertices.
         */
        __structuredDataValue_b(Boolean.class),

        __structuredDataValue_i(Long.class),

        __structuredDataValue_f(Double.class),

        __structuredDataValue_s(String.class),

        __sourceType("sourceType", String.class),

        __targetType("targetType", String.class),

        __sourceCp("source", String.class),

        __targetCp("target", String.class),

        __sourceEid(String.class),

        __targetEid(String.class),

        __identityHash("identityHash", String.class),

        __targetIdentityHash(String.class),

        __contentHash("contentHash", String.class),

        __syncHash("syncHash", String.class),

        __from(Long.class),

        __to(Long.class),

        __changeKind(int.class)

        ;

        private final String sortName;
        private final Class<?> propertyType;

        Property(Class<?> type) {
            this(null, type);
        }

        Property(String sortName, Class<?> type) {
            this.sortName = sortName;
            this.propertyType = type;
        }

        public String getSortName() {
            return sortName;
        }

        public Class<?> getPropertyType() {
            return propertyType;
        }

        private static final HashSet<String> MIRRORED_PROPERTIES = new HashSet<>(Arrays.asList(__type.name(),
                __eid.name(), __cp.name()));

        private static Map<String, String> NAME_TO_PROPERTY = new HashMap<>();

        static {
            try {
                NAME_TO_PROPERTY = Collections.unmodifiableMap(Arrays.asList(values()).stream()
                        .filter(prop -> prop.getSortName() != null)
                        .collect(Collectors.toMap(Property::getSortName, Property::name)));
            } catch (Exception e) {
                // this may happen if there is a duplicate key when doing Collectors.toMap -> duplicated sortName
                // it's better to swallow the exception and let the backend initialize properly
                Log.LOG.error("Unable to initialize Constants.Property enum: " + e.getMessage());
            }
        }


        public static String mapUserDefined(String property) {
            if (NAME_TO_PROPERTY.get(property) != null) {
                return NAME_TO_PROPERTY.get(property);
            }
            return property;
        }

        public static boolean isMirroredInEdges(String property) {
            return MIRRORED_PROPERTIES.contains(property);
        }
    }

    /**
     * The type of entities known to Hawkular.
     */
    public enum Type {
        tenant(Tenant.class, name, __contentHash),
        environment(Environment.class, name, __contentHash),
        feed(Feed.class, name, __identityHash, __contentHash, __syncHash),
        resourceType(ResourceType.class, name, __identityHash, __contentHash, __syncHash),
        metricType(MetricType.class, name, __unit, __metric_data_type, __metric_interval, __identityHash, __contentHash,
                __syncHash),
        operationType(OperationType.class, name, __identityHash, __contentHash, __syncHash),
        resource(Resource.class, name, __identityHash, __contentHash, __syncHash),
        metric(Metric.class, name, __metric_interval, __identityHash, __contentHash, __syncHash),
        metadatapack(MetadataPack.class, name),
        relationship(Relationship.class, __sourceType, __targetType, __sourceCp, __targetCp, __sourceEid, __targetEid,
                __from, __to),
        dataEntity(DataEntity.class, name, __identityHash, __contentHash, __syncHash),
        structuredData(StructuredData.class, __structuredDataType,
                __structuredDataValue_b, __structuredDataValue_i, __structuredDataValue_f, __structuredDataValue_s,
                __structuredDataIndex, __structuredDataKey);

        private final String[] mappedProperties;
        private final Class<?> entityType;

        private static final List<String> identityVertexProperties =
                Arrays.asList(Property.__type.name(), Property.__eid.name(), Property.__cp.name());

        Type(Class<?> entityType, Property... mappedProperties) {
            this.entityType = entityType;
            this.mappedProperties = new String[mappedProperties.length + 3];
            Arrays.setAll(this.mappedProperties, i -> {
                switch (i) {
                    case 0:
                        return Property.__type.name();
                    case 1:
                        return Property.__eid.name();
                    case 2:
                        return Property.__cp.name();
                    default:
                        return mappedProperties[i - 3].name();
                }
            });
        }

        public static Type of(AbstractElement<?, ?> e) {
            return e.accept(new ElementVisitor<Type, Void>() {
                @Override
                public Type visitRelationship(Relationship relationship, Void parameter) {
                    return Type.relationship;
                }

                @Override
                public Type visitTenant(Tenant tenant, Void parameter) {
                    return Type.tenant;
                }

                @Override
                public Type visitEnvironment(Environment environment, Void parameter) {
                    return Type.environment;
                }

                @Override
                public Type visitFeed(Feed feed, Void parameter) {
                    return Type.feed;
                }

                @Override
                public Type visitMetric(Metric metric, Void parameter) {
                    return Type.metric;
                }

                @Override
                public Type visitMetricType(MetricType definition, Void parameter) {
                    return Type.metricType;
                }

                @Override
                public Type visitResource(Resource resource, Void parameter) {
                    return Type.resource;
                }

                @Override
                public Type visitResourceType(ResourceType type, Void parameter) {
                    return Type.resourceType;
                }

                @Override
                public Type visitData(DataEntity data, Void parameter) {
                    return Type.dataEntity;
                }

                @Override
                public Type visitOperationType(OperationType operationType, Void parameter) {
                    return Type.operationType;
                }

                @Override
                public Type visitMetadataPack(MetadataPack metadataPack, Void parameter) {
                    return Type.metadatapack;
                }

                @Override
                public Type visitUnknown(Object entity, Void parameter) {
                    return null;
                }
            }, null);
        }

        public static Type of(Class<?> ec) {
            return of(AbstractElement.segmentTypeFromType(ec));
        }

        public static Type of(SegmentType st) {
            return ElementTypeVisitor.accept(st,
                    new ElementTypeVisitor<Type, Void>() {
                        @Override
                        public Type visitTenant(Void parameter) {
                            return tenant;
                        }

                        @Override
                        public Type visitEnvironment(Void parameter) {
                            return environment;
                        }

                        @Override
                        public Type visitFeed(Void parameter) {
                            return feed;
                        }

                        @Override
                        public Type visitMetric(Void parameter) {
                            return metric;
                        }

                        @Override
                        public Type visitMetricType(Void parameter) {
                            return metricType;
                        }

                        @Override
                        public Type visitResource(Void parameter) {
                            return resource;
                        }

                        @Override
                        public Type visitResourceType(Void parameter) {
                            return resourceType;
                        }

                        @Override
                        public Type visitRelationship(Void parameter) {
                            return relationship;
                        }

                        @Override
                        public Type visitData(Void parameter) {
                            return dataEntity;
                        }

                        @Override
                        public Type visitOperationType(Void parameter) {
                            return operationType;
                        }

                        @Override public Type visitMetadataPack(Void parameter) {
                            return Type.metadatapack;
                        }

                        @Override
                        public Type visitUnknown(Void parameter) {
                            if (st == SegmentType.sd) {
                                return structuredData;
                            }
                            throw new IllegalArgumentException("Unsupported entity type " + st);
                        }
                    }, null);
        }

        public Class<?> getEntityType() {
            return entityType;
        }

        /**
         * @return list of properties that are explicitly mapped to entity class properties.
         */
        public String[] getMappedProperties() {
            return mappedProperties;
        }

        public String identityVertexLabel() {
            return name();
        }

        public String stateVertexLabel() {
            return identityVertexLabel() + "_state";
        }

        public static List<String> getIdentityVertexProperties() {
            return identityVertexProperties;
        }
    }

    public enum InternalEdge {
        __withIdentityHash, __containsIdentityHash, __inState
    }

    public enum InternalType {
        __identityHash
    }
}
