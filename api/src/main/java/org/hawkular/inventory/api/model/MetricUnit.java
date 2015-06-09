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

    // Simple Metric Types - Absolute and Relative
    NONE(""),
    PERCENTAGE("%"),

    // Absolute Sizes in Bytes (utilization)
    BYTES("B"),
    KILOBYTES("KB"),
    MEGABYTES("MB"),
    GIGABYTES("GB"),
    TERABYTES("TB"),
    PETABYTES("PB"),

    // Absolute Sizes in Bits (throughput)
    BITS("b"),
    KILOBITS("Kb"),
    MEGABITS("Mb"),
    GIGABITS("Gb"),
    TERABITS("Tb"),
    PETABITS("Pb"),

    // Absolute Time - no display, only hints to the UI how to display
    EPOCH_MILLISECONDS(""),
    EPOCH_SECONDS(""),

    // Relative Time
    JIFFYS("j"),
    NANOSECONDS("ns"),
    MICROSECONDS("us"),
    MILLISECONDS("ms"),
    SECONDS("s"),
    MINUTES("m"),
    HOURS("h"),
    DAYS("d"),

    // Rate
    PER_JIFFY("/j"),
    PER_NANOSECOND("/ns"),
    PER_MICROSECOND("/us"),
    PER_MILLISECOND("/ms"),
    PER_SECOND("/s"),
    PER_MINUTE("/m"),
    PER_HOUR("/h"),
    PER_DAY("/d"),

    // Temperature
    CELSIUS("C"),
    KELVIN("K"),
    FAHRENHEIGHT("F");

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
