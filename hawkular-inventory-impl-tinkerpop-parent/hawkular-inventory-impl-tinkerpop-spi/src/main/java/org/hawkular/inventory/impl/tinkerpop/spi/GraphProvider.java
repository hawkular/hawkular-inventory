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

import static org.hawkular.inventory.impl.tinkerpop.spi.Log.LOG;

import java.util.HashMap;
import java.util.Map;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.paths.CanonicalPath;

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
    Graph instantiateGraph(Configuration configuration);

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
    void ensureIndices(Graph graph, IndexSpec... indexSpecs);

    /**
     * Initializes new transaction for use with given graph.
     *
     * @param graph    the graph to start the transaction in
     * @return a new transactional graph that is bound to a new transaction
     */
    default Graph startTransaction(Graph graph) {
        boolean tracing = LOG.isTraceEnabled();

        Throwable previousTransactionAllocation = TransactionTracker.transactionStart.get().get(this);

        if (previousTransactionAllocation != null) {
            if (tracing) {
                LOG.trace("FAILURE TO START THE TX.");
                LOG.trace("THERE'S A TX ALREADY ACTIVE:", previousTransactionAllocation);
            }
            throw new IllegalStateException("Transaction already open.", previousTransactionAllocation);
        }

        boolean failed = false;
        try {
            graph.tx().open();
            return graph;
        } catch (Throwable t) {
            failed = true;
            throw t;
        } finally {
            if (!failed) {
                if (tracing) {
                    TransactionTracker.transactionStart.get()
                            .put(this, new Exception());
                    LOG.trace("+++++++ TX STARTED ON GRAPH: " + graph);
                } else {
                    TransactionTracker.transactionStart.get()
                            .put(this, NoRecordedStacktrace.INSTANCE);
                }
            }
        }
    }

    /**
     * Commits the transaction in the graph.
     *
     * <p>The default implementation merely calls {@link org.apache.tinkerpop.gremlin.structure.Transaction#commit()}.
     *
     * @param graph the graph to commit the transaction to
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    default void commit(Graph graph) {
        try {
            Transaction tx = graph.tx();
            tx.commit();
            tx.close();

            if (Log.LOG.isTraceEnabled()) {
                Log.LOG.trace("------- TX COMMITTED ON GRAPH: " + graph);
            }
        } catch (Throwable t) {
            if (Log.LOG.isTraceEnabled()) {
                LOG.trace("FAILURE TO COMMIT TX:", t);
            }
            throw t;
        } finally {
            TransactionTracker.transactionStart.get().remove(this);
        }
    }

    /**
     * Rolls back the transaction in the graph.
     * <p>
     * <p>The default implementation merely calls {@link org.apache.tinkerpop.gremlin.structure.Transaction#rollback()}.
     *
     * @param graph the graph to rollback the transaction from
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    default void rollback(Graph graph) {
        try {
            Transaction tx = graph.tx();
            tx.rollback();
            tx.close();

            if (Log.LOG.isTraceEnabled()) {
                Log.LOG.trace("------- TX ROLLED BACK ON GRAPH: " + graph);
            }
        } catch (Throwable t) {
            if (Log.LOG.isTraceEnabled()) {
                LOG.trace("FAILURE TO ROLLBACK TX:", t);
            }
            throw t;
        } finally {
            TransactionTracker.transactionStart.get().remove(this);
        }
    }

    /**
     * Translates the graph specific exception to an inventory exception.
     * <p>
     * <p>The default implementation is an identity function.</p>
     *
     * @param inputException an exception to convert
     * @param affectedPath the canonical path of the entity affected by the exception
     * @return converted exception
     */
    default RuntimeException translateException(RuntimeException inputException, CanonicalPath affectedPath) {
        return inputException;
    }

    /**
     * Checks the exception thrown during the commit and returns true if the backend requires explicit rollback after
     * such failure occured or false if the failure caused the transaction to close itself automatically.
     *
     * @param t the exception thrown during commit
     * @return true to explictly roll back or false if the exception already caused the transaction to close
     */
    default boolean requiresRollbackAfterFailure(Throwable t) {
        return true;
    }

    /**
     * Tries to determine if a transaction retry has a chance of recovering from a condition signified by the provided
     * throwable.
     *
     *
     * @param graph
     * @param t a throwable that caused a transaction payload to fail.
     * @return true if the transaction should be retried, false otherwise
     */
    default boolean isTransactionRetryWarranted(Graph graph, Throwable t) {
        return false;
    }
}

/**
 * We use the smaller overhead of a thread local hash map to ensure none of our codebase tries to spawn
 * nested transactions. Concurrent transactions from different threads are OK.
 */
class TransactionTracker {
    static ThreadLocal<Map<GraphProvider, Throwable>> transactionStart =
            new ThreadLocal<Map<GraphProvider, Throwable>>() {
        @Override protected Map<GraphProvider, Throwable> initialValue() {
            //usually we don't have more than 1 graph provider active, so let's keep this small for starts
            return new HashMap<>(1);
        }
    };
}

/**
 * A trick to have an exception with no stacktrace. This is used in transaction tracker when the log level is not
 * TRACE to save big time on execution time (because no stacktrace allocation needs to happen).
 */
class NoRecordedStacktrace extends Throwable {
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    static final NoRecordedStacktrace INSTANCE = new NoRecordedStacktrace();
    @Override public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
