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
 * Units of a metric
 *
 * TODO this is limited and probably will be replaced by a dedicated entity type in the future (so that it is possible
 * to create new units). Possibly we should also look at some more elaborate measurement unit solution like JSR-363.
 *
 * @author Heiko W. Rupp
 */
public enum MetricUnit {


    NONE(""),
    MILLI_SECOND("ms"),
    SECONDS("s"),
    MINUTE("min"),
    BYTE("b"),
    KILO_BYTE("kb");

    private final String displayName;

    MetricUnit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MetricUnit fromDisplayName(String displayName) {
        for (MetricUnit mu : values()) {
            if (mu.displayName.equals(displayName)) {
                return mu;
            }
        }

        throw new IllegalArgumentException("No such unit: " + displayName);
    }
}
