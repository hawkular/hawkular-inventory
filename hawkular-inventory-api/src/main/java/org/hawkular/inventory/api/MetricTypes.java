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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.paths.Path;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on metric types.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class MetricTypes {

    private MetricTypes() {

    }

    private interface BrowserBase {

        /**
         * @return metrics defined by the metric type(s)
         */
        Metrics.Read metrics();

        /**
         * @return metric types that are identical to this metric type
         */
        MetricTypes.Read identical();
    }

    /**
     * An interface implemented by Single/Multiple interfaces of entities that can contain metric types.
     * @param <Access> the type of access to metric types
     */
    public interface Container<Access> {
        Access metricTypes();
    }

    /**
     * Interface for accessing a single metric type in a writable manner.
     */
    public interface Single
            extends Synced.SingleWithRelationships<MetricType, MetricType.Blueprint, MetricType.Update>,
            BrowserBase {
    }

    /**
     * Interface for traversing over a set of metric types.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(Object)} method).
     */
    public interface Multiple extends ResolvableToManyWithRelationships<MetricType>, BrowserBase {
    }

    /**
     * Provides read-write access to metric types.
     */
    public interface ReadWrite
            extends ReadWriteInterface<MetricType.Update, MetricType.Blueprint, Single, Multiple, String> {}

    /**
     * Provides read-only access to metric types.
     */
    public interface ReadContained extends ReadInterface<Single, Multiple, String> {}

    public interface Read extends ReadInterface<Single, Multiple, Path> {}

    /**
     * Provides read-only access to metric types with the additional ability to relate the metric types to the current
     * position in the inventory traversal.
     */
    public interface ReadAssociate extends Read, AssociationInterface {}
}
