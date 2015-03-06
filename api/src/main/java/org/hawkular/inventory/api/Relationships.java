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
 * This is a wrapper class to hold various interfaces defining available functionality on relationships.
 *
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


    /**
     * The list of possible relationship (aka edges) direction. Relationships are not bidirectional.
     */
    public enum Direction {
        /**
         * Expresses encapsulation of a set of entities in another entity.
         * Used for example to express the relationship between a tenant and the set of its environments.
         */
        outgoing,

        /**
         * Expresses "instantiation" of some entity based on the definition provided by "source" entity.
         * For example, there is a defines relationship between a metric definition and all metrics that
         * conform to it.
         */
        incoming,

        /**
         * Expresses ownership. For example a resource owns a set of metrics, or a resource type owns a set
         * of metric definitions. They do not contain it though, because more resources can own a single metric for
         * example.
         */
        both
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

    /**
     * Interface for accessing a single relationship in a writable manner
     */
    public interface Single extends ResolvableToSingle<Relationship>,
            BrowserBase<Tenants.ReadRelate, Environments.ReadRelate, Feeds.ReadRelate, MetricTypes.ReadRelate,
                    Metrics.ReadRelate, Resources.ReadRelate, ResourceTypes.ReadRelate> {}

    /**
     * Interface for traversing over a set of relationships.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(String)} method).
     */
    public interface Multiple extends ResolvableToMany<Relationship>,
            BrowserBase<Tenants.Read, Environments.Read, Feeds.Read, MetricTypes.Read, Metrics.Read,
                    Resources.Read, ResourceTypes.Read> {}

    /**
     * Provides read-write access to relationships.
     */
    public interface ReadWrite extends ReadWriteRelationshipsInterface<Relationship, Relationship.Blueprint, Single,
            Multiple> {
        Multiple named(String name);
        Multiple named(WellKnown name);
    }

    /**
     * Provides read access to relationships.
     */
    public interface Read extends ReadRelationshipsInterface<Single, Multiple> {
        Multiple named(String name);
        Multiple named(WellKnown name);
    }
}
