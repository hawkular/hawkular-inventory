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
package org.hawkular.inventory.api.filters;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A filter that merely marks a position in the filter chain with a label. This is currently only used in
 * the {@link org.hawkular.inventory.api.filters.With.RelativePaths} filter to mark a position against which a
 * relative path should be resolved.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class Marker extends Filter {
    private static final AtomicInteger cnt = new AtomicInteger();

    private final String label;

    public static Marker next() {
        return new Marker();
    }

    public Marker() {
        this.label = "marker-" + cnt.getAndIncrement();
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Marker)) return false;

        Marker marker = (Marker) o;

        return label.equals(marker.label);

    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }

    @Override
    public String toString() {
        return "Marker[" + "label='" + label + '\'' + ']';
    }
}
