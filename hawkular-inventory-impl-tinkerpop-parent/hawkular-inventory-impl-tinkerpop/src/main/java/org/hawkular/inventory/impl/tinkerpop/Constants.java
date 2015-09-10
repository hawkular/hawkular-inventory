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
package org.hawkular.inventory.impl.tinkerpop;

import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__metric_data_type;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__structuredDataIndex;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__structuredDataKey;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__structuredDataType;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__structuredDataValue;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__unit;

import java.util.Arrays;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementTypeVisitor;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class Constants {

    /**
     * The vertices in the graph have certain well-known properties.
     */
    enum Property {
        /**
         * This is the name of the property that we use to store the type of the entity represented by the vertex
         */
        __type,

        /**
         * This is the name of the property that we use to store the user-defined ID of the entity represented by the
         * vertex. These ID are required to be unique only amongst their "siblings" as determined by the "contains"
         * hierarchy.
         */
        __eid,

        /**
         * Present on metric type, this is the name of the propety that we use to store the unit of the metric type
         * represented by the vertex.
         */
        __unit,

        /**
         * Property used on metric type to distinguish type of metric e.g. gauge, counter...
         */
        __metric_data_type,

        /**
         * Property used to store the canonical path of an element.
         */
        __cp,

        /**
         * The type of the data stored by the structured data vertex
         */
        __structuredDataType,

        /**
         * The key using which a structured data value is stored in a map.
         */
        __structuredDataKey,

        /**
         * The index on which a structured data value is stored in a list.
         */
        __structuredDataIndex,

        /**
         * The name of the property on the structured data vertex that holds the primitive value of that vertex.
         * List and maps don't hold the value directly but instead have edges going out to the child vertices.
         */
        __structuredDataValue;


        public static String mapUserDefined(String property) {
            if (AbstractElement.ID_PROPERTY.equals(property)) {
                return __eid.name();
            } else {
                return property;
            }
        }
    }

    /**
     * The type of entities known to Hawkular.
     */
    enum Type {
        tenant(Tenant.class), environment(Environment.class), feed(Feed.class),
        resourceType(ResourceType.class), metricType(MetricType.class, __unit, __metric_data_type),
        operationType(OperationType.class), resource(Resource.class), metric(Metric.class),
        relationship(Relationship.class), dataEntity(DataEntity.class),
        structuredData(StructuredData.class, __structuredDataType, __structuredDataValue, __structuredDataIndex,
                __structuredDataKey);

        private final String[] mappedProperties;
        private final Class<?> entityType;

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
                public Type visitUnknown(Object entity, Void parameter) {
                    return null;
                }
            }, null);
        }

        public static Type of(Class<?> ec) {
            return ElementTypeVisitor.accept(ec, new ElementTypeVisitor<Type, Void>() {
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

                @Override
                public Type visitUnknown(Void parameter) {
                    if (StructuredData.class.equals(ec)) {
                        return structuredData;
                    }
                    throw new IllegalArgumentException("Unsupported entity class " + ec);
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
    }

    private Constants() {
        //no instances, thank you
    }
}
