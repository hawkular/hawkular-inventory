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

/**
 * Inventory stores "resources" which are groupings of measurements and other data. Inventory also stores metadata about
 * the measurements and resources to give them meaning.
 *
 * <p>The resources are organized by tenant (your company) and environments (i.e. testing, development, staging,
 * production, ...).
 *
 * <p>Resources are hierarchical - meaning that one can be a parent of others, recursively. One can also say that a
 * resource can contain other resources. Resources can have other kinds of relationships that are not necessarily
 * tree-like.
 *
 * <p>Resources can have a "resource type" (but they don't have to) which prescribes what kind of data a resource
 * contains. Most prominently a resource can have a list of metrics and a resource type can define what those metrics
 * should be by specifying the set of "metric types".
 *
 * <p>This interface offers a fluent API to compose a "traversal" over the graph of entities stored in the inventory in
 * a strongly typed fashion.
 *
 * <p>The inventory implementations are not required to be thread-safe. Instances should therefore be accessed only by a
 * single thread or serially.
 *
 * <p>Note to implementers:
 *
 * <p>The interfaces composing the inventory API are of 2 kinds:
 * <ol>
 *     <li>CRUD interfaces that provide manipulation of the entities as well as the retrieval of the actual entity
 *     instances (various {@code Read}, {@code ReadWrite} or {@code ReadRelate} interfaces, e.g.
 *     {@link org.hawkular.inventory.api.Environments.ReadWrite}),
 *     <li>browse interfaces that offer further navigation methods to "hop" onto other entities somehow related to the
 *     one(s) in the current position in the inventory traversal. These interfaces are further divided into 2 groups:
 *      <ul>
 *          <li><b>{@code Single}</b> interfaces that provide methods for navigating from a single entity.
 *          These interfaces generally contain methods that enable modification of the entity or its relationships.
 *          See {@link org.hawkular.inventory.api.Environments.Single} for an example.
 *          <li><b>{@code Multiple}</b> interfaces that provide methods for navigating from multiple entities at once.
 *          These interfaces strictly offer only read-only access to the entities, because the semantics of what should
 *          be done when modifying or relating multiple entities at once is not uniformly defined on all types of
 *          entities and therefore would make the API more confusing than necessary. See
 *          {@link org.hawkular.inventory.api.Environments.Multiple} for example.
 *      </ul>
 * </ol>
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface Inventory extends AutoCloseable {

    /**
     * Initializes the inventory from the provided configuration object.
     * @param configuration the configuration to use.
     */
    void initialize(Configuration configuration);

    /**
     * Entry point into the inventory. Select one ({@link org.hawkular.inventory.api.Tenants.ReadWrite#get(String)}) or
     * more ({@link org.hawkular.inventory.api.Tenants.ReadWrite#getAll(org.hawkular.inventory.api.filters.Filter...)})
     * tenants and navigate further to the entities of interest.
     *
     * @return full CRUD and navigation interface to tenants
     */
    Tenants.ReadWrite tenants();
}
