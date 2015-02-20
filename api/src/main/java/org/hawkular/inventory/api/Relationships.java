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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.Relationship;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Relationships {
    private Relationships() {

    }

    /**
     * The list of well-known relationships (aka edges) between entities (aka vertices).
     */
    public enum WellKnown {
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
         * of metric definitions. They do not contain it though, because more resources can own a single metric for
         * example.
         */
        owns
    }

    private interface BrowserBase<Tenants, Environments, Feeds, MetricTypes, Metrics, Resources, ResourceTypes> {
        Tenants tenants();

        Environments environments();

        Feeds feeds();

        MetricTypes metricTypes();

        Metrics metrics();

        Resources resources();

        ResourceTypes resourceTypes();
    }

    public interface Single extends SingleEntityBrowser<Relationship>,
            BrowserBase<Tenants.ReadWrite, Environments.ReadWrite, Feeds.ReadAndRegister, MetricTypes.ReadWrite,
                    Metrics.ReadWrite, Resources.ReadWrite, ResourceTypes.ReadWrite> {}

    public interface Multiple extends MultipleEntityBrowser<Relationship>,
            BrowserBase<Tenants.Read, Environments.Read, Feeds.Read, MetricTypes.Read, Metrics.Read,
                    Resources.Read, ResourceTypes.Read> {}

    public interface ReadWrite extends ReadWriteInterface<Relationship, Relationship.Blueprint, Single, Multiple> {
    }

    public interface Read extends ReadInterface<Single, Multiple> {}
}
