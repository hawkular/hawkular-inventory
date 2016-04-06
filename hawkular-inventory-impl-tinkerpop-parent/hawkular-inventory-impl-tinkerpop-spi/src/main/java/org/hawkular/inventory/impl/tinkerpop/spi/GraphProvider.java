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
package org.hawkular.inventory.impl.tinkerpop.spi;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.paths.CanonicalPath;

import com.tinkerpop.blueprints.ThreadedTransactionalGraph;
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
public interface GraphProvider {

    boolean isPreferringBigTransactions();

    /**
     * Some implementations need the pipeline to be fully drained in order to be able to reclaim backend resources.
     *
     * @return true if the underlying graph needs pipelines drained in order to clean up.
     */
    boolean needsDraining();

    /**
     * @see org.hawkular.inventory.base.spi.InventoryBackend#isUniqueIndexSupported()
     */
    boolean isUniqueIndexSupported();

    /**
     * Given provided configuration, tries to instantiate a graph to be used by the inventory.
     *
     * @param configuration the configuration of the graph
     * @return a configured instance of the graph or null if not possible
     */
    TransactionalGraph instantiateGraph(Configuration configuration);

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
    void ensureIndices(TransactionalGraph graph, IndexSpec... indexSpecs);

    /**
     * Initializes new transaction for use with given graph.
     *
     * @param graph    the graph to start the transaction in
     * @return a new transactional graph that is bound to a new transaction
     */
    default TransactionalGraph startTransaction(TransactionalGraph graph) {
        return graph instanceof ThreadedTransactionalGraph
                ? ((ThreadedTransactionalGraph) graph).newTransaction()
                : graph;
    }

    /**
     * Commits the transaction in the graph.
     *
     * <p>The default implementation merely calls {@link TransactionalGraph#commit()}.
     *
     * @param graph the graph to commit the transaction to
     */
    default void commit(TransactionalGraph graph) {
        graph.commit();
    }

    /**
     * Rolls back the transaction in the graph.
     * <p>
     * <p>The default implementation merely calls {@link TransactionalGraph#rollback()}.
     *
     * @param graph the graph to rollback the transaction from
     */
    default void rollback(TransactionalGraph graph) {
        graph.rollback();
    }

    /**
     * Translates the graph specific exception to an inventory exception.
     * <p>
     * <p>The default implementation is an identity function.</p>
     *
     * @param inputException an exception to convert
     * @param affectedPath
     * @return converted exception
     */
    default RuntimeException translateException(RuntimeException inputException, CanonicalPath affectedPath) {
        return inputException;
    }
}
