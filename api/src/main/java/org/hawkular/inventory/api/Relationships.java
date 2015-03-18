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
        both
    }

    /**
     * Interface for accessing a single relationship.
     */
    public interface Single extends ResolvableToSingle<Relationship> {
    }

    /**
     * Interface for traversing over a set of relationships.
     *
     * <p/>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(String)} method).
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
