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

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.EntityVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Arrays;

import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__unit;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.__version;

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
         * Present on the resource type entity, this is the name of the property that we use to store the version
         * of the resource type represented by the vertex.
         */
        __version,

        /**
         * Present on metric type, this is the name of the propety that we use to store the unit of the metric type
         * represented by the vertex.
         */
        __unit;

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
        resourceType(ResourceType.class, __version), metricType(MetricType.class, __unit), resource(Resource.class),
        metric(Metric.class);

        private final String[] mappedProperties;
        private final Class<? extends Entity> entityType;

        Type(Class<? extends Entity> entityType, Property... mappedProperties) {
            this.entityType = entityType;
            this.mappedProperties = new String[mappedProperties.length + 2];
            Arrays.setAll(this.mappedProperties, i -> i == 0 ? Property.__type.name() :
                    (i == 1 ? Property.__eid.name() : mappedProperties[i - 2].name()));
        }

        public static Type of(Entity<?, ?> e) {
            return e.accept(new EntityVisitor<Type, Void>() {
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
            }, null);
        }

        public static Type of(Class<? extends Entity> ec) {
            if (ec == Tenant.class) {
                return Type.tenant;
            } else if (ec == Environment.class) {
                return Type.environment;
            } else if (ec == Feed.class) {
                return Type.feed;
            } else if (ec == Metric.class) {
                return Type.metric;
            } else if (ec == MetricType.class) {
                return Type.metricType;
            } else if (ec == Resource.class) {
                return Type.resource;
            } else if (ec == ResourceType.class) {
                return Type.resourceType;
            } else {
                throw new IllegalArgumentException("Unsupported entity class " + ec);
            }
        }

        public Class<? extends Entity> getEntityType() {
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
