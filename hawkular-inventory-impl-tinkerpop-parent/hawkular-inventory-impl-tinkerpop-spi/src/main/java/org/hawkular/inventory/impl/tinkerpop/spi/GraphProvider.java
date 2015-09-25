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
package org.hawkular.inventory.impl.tinkerpop.spi;

import org.hawkular.inventory.api.configuration.Configuration;
import org.hawkular.inventory.base.spi.InventoryBackend;

import com.tinkerpop.blueprints.TransactionalGraph;

/**
 * This is a service interface that the Tinkerpop implementation will use to get a configured and initialized instance
 * of a blueprints graph.
 * <p>
 * <p>This level of indirection is needed because many graph databases provide configuration and management features
 * that are not accessible through plain Blueprints API.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface GraphProvider<G extends TransactionalGraph> {

    /**
     * Given provided configuration, tries to instantiate a graph to be used by the inventory.
     *
     * @param configuration the configuration of the graph
     * @return a configured instance of the graph or null if not possible
     */
    G instantiateGraph(Configuration configuration);

    /**
     * Makes sure all the indexes needed for good performance.
     * <p>
     * <p>The provided set of indexes is what the implementation thinks the indices should be. The graph provider
     * is free to make more indexes if they choose so to support the "core" set of indices.
     *
     * @param graph      the graph instance (coming from the
     *                   {@link #instantiateGraph(Configuration)} call) to index
     * @param indexSpecs the core set of indices to define
     */
    void ensureIndices(G graph, IndexSpec... indexSpecs);

    /**
     * Starts a new transaction in the graph.
     * <p>
     * <p>The default implementation returns a new instance of
     * {@link org.hawkular.inventory.base.spi.InventoryBackend.Transaction} with the provided mutating flag.
     *
     * @param graph    the graph to start the transaction in
     * @param mutating whether the transaction will mutate the storage or not
     * @return a transaction handle
     */
    default InventoryBackend.Transaction startTransaction(G graph, boolean mutating) {
        return new InventoryBackend.Transaction(mutating);
    }

    /**
     * Commits the transaction in the graph.
     *
     * <p>The default implementation merely calls {@link TransactionalGraph#commit()}.
     *
     * @param graph the graph to commit the transaction to
     * @param t     the transaction
     */
    default void commit(G graph, InventoryBackend.Transaction t) {
        graph.commit();
    }

    /**
     * Rolls back the transaction in the graph.
     * <p>
     * <p>The default implementation merely calls {@link TransactionalGraph#rollback()}.
     *
     * @param graph the graph to rollback the transaction from
     * @param t     the transaction
     */
    default void rollback(G graph, InventoryBackend.Transaction t) {
        graph.rollback();
    }

    /**
     * Translates the graph specific exception to an inventory exception.
     * <p>
     * <p>The default implementation is an identity function.</p>
     *
     * @param inputException an exception to convert
     * @return converted exception
     */
    default RuntimeException translateException(RuntimeException inputException) {
        return inputException;
    }
}
