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
 * @since 0.0.1
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
         *
         * <p>Note that entities that are contained within another entity (by the virtue of there being this
         * relationship between them) are deleted along with it.
         *
         * <p>Note also that it is prohibited to create loops in the contains relationships, i.e. an entity cannot
         * (indirectly) contain itself. Also, containment is unique and therefore 1 entity cannot be contained in 2 or
         * more other entities.
         */
        contains,

        /**
         * Expresses "instantiation" of some entity based on the definition provided by "source" entity.
         * For example, there is a defines relationship between a metric definition and all metrics that
         * conform to it.
         *
         * <p>Note that as long as an entity defines other entities, it cannot be deleted.
         */
        defines,

        /**
         * Expresses inclusion. For example a resource incorporates a set of metrics, or a resource type incorporates a
         * set of metric definitions. They do not contain it though, because more resources can incorporate a single
         * metric for example.
         */
        incorporates,

        /**
         * Used to express hierarchy of resources.
         *
         * <p> There are 2 separate concepts that need to be understood when examining resource hierarchy:
         * <ol>
         *     <li>The embedding of resources into each other - i.e. the child resources are inseparable from their
         *     parent, because they are its parts - e. g. memory subsystem of JVM. These are modelled using 2 separate
         *     relationships in inventory. The containment aspect is modelled by the {@link #contains} relationship and
         *     the hierarchy aspect is modelled by the {@code isParentOf} relationship. When a {@link #contains}
         *     relationship is established between 2 resources, the {@code isParentOf} is automagically added by the
         *     inventory.
         *
         *     <li>Custom hierarchies can be defined between arbitrary resources, regardless of their containment in
         *     other resources. These can be useful to compose ad-hoc "groupings" of resources
         * </ol>
         *
         * <p>This relationship cannot form loops (similarly to {@link #contains}) but allows for 1 resource having more
         * than 1 parent. This is allowed so that custom/parallel resource hierarchies can be created that share the
         * same resources (the most obvious example of this is that a resource that has been discovered and
         * "hierarchized" by a feed can also be put "under" a custom, user-defined, resource).
         *
         *
         * <p>When deleting a resource, all its contained child resources are deleted along with it as mandated by the
         * contract of the {@link #contains} relationship.
         */
        isParentOf,

        /**
         * A relationship from a resource to a structural data that contains the configuration of that resource.
         *
         * <p>The relationship is 1-to-1. There is no sharing of configuration objects between different resources.
         */
        isConfiguredBy,

        /**
         * A relationship from a resource to a structural data that stores info about how the feed should connect to
         * the resource.
         *
         * <p>The relationship is 1-to-1. There is no sharing of configuration objects between different resources.
         */
        connectsUsing,

        /**
         * This relationship is used to link the {@link org.hawkular.inventory.api.model.DataEntity} to its data.
         * This relationship is invisible to the API users, because one cannot obtain a {@link Relationship} object
         * of it using the API but it is specified here nevertheless because it is a part of the contract of the API
         * with the backend storage.
         */
        hasData
    }

    /**
     * The list of possible relationship (aka edges) direction. Relationships are not bidirectional.
     */
    public enum Direction {
        /**
         * Relative to the current position in the inventory traversal, this value expresses such relationships
         * that has me (the entity(ies) on the current pos) as a source(s).
         */
        outgoing,

        /**
         * Relative to the current position in the inventory traversal, this value expresses such relationships
         * that has me (the entity(ies) on the current pos) as a target(s).
         */
        incoming,

        /**
         * Relative to the current position in the inventory traversal, this value expresses all the relationships
         * I (the entity(ies) on the current pos) have with other entity(ies).
         */
        both;

        public Direction opposite() {
            switch (this) {
                case outgoing:
                    return incoming;
                case incoming:
                    return outgoing;
                case both:
                    return both;
                default:
                    throw new AssertionError("Incomplete opposite direction mapping.");
            }
        }
    }

    /**
     * Interface for accessing a single relationship.
     */
    public interface Single extends ResolvableToSingle<Relationship, Relationship.Update> {
    }

    /**
     * Interface for traversing over a set of relationships.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(Object)} method).
     */
    public interface Multiple extends ResolvableToMany<Relationship> {
        Tenants.Read tenants();

        Environments.Read environments();

        Feeds.Read feeds();

        MetricTypes.Read metricTypes();

        Metrics.Read metrics();

        Resources.Read resources();

        ResourceTypes.Read resourceTypes();
    }

    /**
     * Provides read-write access to relationships.
     */
    public interface ReadWrite extends ReadWriteRelationshipsInterface<Single, Multiple> {
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
