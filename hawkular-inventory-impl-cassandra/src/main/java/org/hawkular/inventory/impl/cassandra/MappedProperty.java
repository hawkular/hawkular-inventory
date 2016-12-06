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
package org.hawkular.inventory.impl.cassandra;

import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public enum MappedProperty {
    UNIT("-$unit"),
    METRIC_DATA_TYPE("-$metricDataType"),
    COLLECTION_INTERVAL("-$collectionInterval")
    ;

    private final String name;

    MappedProperty(String name) {
        this.name = name;
    }

    public String propertyName() {
        return name;
    }

    public String toString(Object value) {
        if (value == null) {
            return null;
        }

        switch (this) {
            case UNIT:
                return ((MetricUnit) value).getDisplayName();
            case METRIC_DATA_TYPE:
                return ((MetricDataType) value).getDisplayName();
            case COLLECTION_INTERVAL:
                return value.toString();
            default:
                throw new IllegalStateException("Incomplete MappedProperty#toString implementation. This is a bug.");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T fromString(String value) {
        if (value == null) {
            return null;
        }

        switch (this) {
            case UNIT:
                return (T) MetricUnit.fromDisplayName(value);
            case METRIC_DATA_TYPE:
                return (T) MetricDataType.fromDisplayName(value);
            case COLLECTION_INTERVAL:
                return (T) Long.valueOf(value);
            default:
                throw new IllegalStateException("Incomplete MappedProperty#fromString implementation. This is a bug.");
        }
    }
}
