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

import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.unit;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Property.version;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class Constants {

    /**
     * The vertices in the graph have certain well-known properties.
     */
    enum Property {
        type, uid, version, unit
    }

    /**
     * The type of entities known to Hawkular.
     */
    enum Type {
        tenant(Tenant.class), environment(Environment.class), feed(Feed.class),
        resourceType(ResourceType.class, version), metricType(MetricType.class, unit), resource(Resource.class),
        metric(Metric.class);

        private final String[] mappedProperties;
        private final Class<? extends Entity> entityType;

        private Type(Class<? extends Entity> entityType, Property... mappedProperties) {
            this.entityType = entityType;
            this.mappedProperties = new String[mappedProperties.length + 2];
            Arrays.setAll(this.mappedProperties, i -> i == 0 ? Property.type.name() :
                    (i == 1 ? Property.uid.name() : mappedProperties[i - 2].name()));
        }

        public static Type of(Entity e) {
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

        public String[] getMappedProperties() {
            return mappedProperties;
        }
    }

    /**
     * The list of well-known relationships (aka edges) between entities (aka vertices).
     */
    enum Relationship {
        /**
         * Expresses encapsulation of a set of entities in another entity.
         * Used for example to express the relationship between a tenant and the set of its environments.
         */
        contains,

        /**
         * Expresses "instantiation" of some entity based on the definition provided by "source" entity.
         * For example, there is a defines relationship between a metric definition and all metrics that
         * conform to it.
         */
        defines,

        /**
         * Expresses ownership. For example a resource owns a set of metrics, or a resource type owns a set
         * of metric definitions.
         */
        owns
    }

    private Constants() {
        //no instances, thank you
    }
}
