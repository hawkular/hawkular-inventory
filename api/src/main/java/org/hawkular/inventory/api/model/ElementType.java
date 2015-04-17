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

/**
 * Enumerates all the possible types of elements
 */
public enum ElementType {
    TENANT(Tenant.class, "t"),
    RESOURCE_TYPE(ResourceType.class, "rt"),
    METRIC_TYPE(MetricType.class, "mt"),
    ENVIRONMENT(Environment.class, "e"),
    FEED(Feed.class, "f"),
    METRIC(Metric.class, "m"),
    RESOURCE(Resource.class, "r"),
    RELATIONSHIP(Relationship.class, "rl");

    private final Class<? extends AbstractElement<?, ?>> type;
    private final String shortString;

    ElementType(Class<? extends AbstractElement<?, ?>> type, String shortString) {
        this.type = type;
        this.shortString = shortString;
    }

    public static ElementType fromShortString(String shortString) {
        for (ElementType t : values()) {
            if (t.getShortString().equals(shortString)) {
                return t;
            }
        }

        return null;
    }

    public static ElementType of(Class<? extends AbstractElement<?, ?>> type) {
        for (ElementType e : values()) {
            if (e.getType().equals(type)) {
                return e;
            }
        }

        return null;
    }

    public Class<? extends AbstractElement<?, ?>> getType() {
        return type;
    }

    /**
     * A short string representation of the element type used as a type specifier in paths.
     *
     * @return the short string representing the element type
     */
    public String getShortString() {
        return shortString;
    }
}
