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

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.hawkular.inventory.api.Configuration;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.17.2
 */
public class GraphProviderTest {

    @Test
    public void testConcurrentTransactionsAllowed() throws Exception {
        //lock the commits of the 2 concurrent threads on this latch to ensure that we
        //truly do have 2 concurrent transactions on a single graph
        final CountDownLatch startLatch = new CountDownLatch(2);
        final CountDownLatch finishLatch = new CountDownLatch(1);

        boolean[] statuses = new boolean[2];

        GraphProvider gp = new DummyGraphProvider();
        Graph g = gp.instantiateGraph(null);

        Consumer<Integer> payload = (idx) -> {
            gp.startTransaction(g);
            startLatch.countDown();
            try {
                finishLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("The test graph should not be forcefully interrupted during test.");
            }
            gp.commit(g);

            statuses[idx] = true;
        };

        Thread t1 = new Thread(() -> payload.accept(0));
        Thread t2 = new Thread(() -> payload.accept(1));

        t1.start();
        t2.start();

        startLatch.await();
        finishLatch.countDown();

        t1.join();
        t2.join();

        Assert.assertTrue(statuses[0]);
        Assert.assertTrue(statuses[1]);
    }

    @Test(expected = IllegalStateException.class)
    public void testNestedTransactionsDisallowed() throws Exception {
        GraphProvider gp = new DummyGraphProvider();
        Graph g = gp.instantiateGraph(null);

        gp.startTransaction(g);

        gp.startTransaction(g);
    }

    private static class DummyGraphProvider implements GraphProvider {

        @Override public boolean isPreferringBigTransactions() {
            return false;
        }

        @Override public boolean needsDraining() {
            return false;
        }

        @Override public boolean isUniqueIndexSupported() {
            return false;
        }

        @Override public Graph instantiateGraph(Configuration configuration) {
            return new DummyGraph();
        }

        @Override public void ensureIndices(Graph graph, IndexSpec... indexSpecs) {
        }
    }

    private static class DummyGraph implements Graph {

        @Override public Vertex addVertex(Object... keyValues) {
            return null;
        }

        @Override public <C extends GraphComputer> C compute(Class<C> graphComputerClass)
                throws IllegalArgumentException {
            return null;
        }

        @Override public GraphComputer compute() throws IllegalArgumentException {
            return null;
        }

        @Override public Iterator<Vertex> vertices(Object... vertexIds) {
            return null;
        }

        @Override public Iterator<Edge> edges(Object... edgeIds) {
            return null;
        }

        @Override public Transaction tx() {
            return new DummyTx();
        }

        @Override public void close() throws Exception {
        }

        @Override public Variables variables() {
            return null;
        }

        @Override public org.apache.commons.configuration.Configuration configuration() {
            return null;
        }
    }

    private static class DummyTx implements Transaction {

        @Override public void open() {
        }

        @Override public void commit() {
        }

        @Override public void rollback() {
        }

        @Override public <R> Workload<R> submit(Function<Graph, R> work) {
            return null;
        }

        @Override public <G extends Graph> G createThreadedTx() {
            return null;
        }

        @Override public boolean isOpen() {
            return false;
        }

        @Override public void readWrite() {
        }

        @Override public void close() {
        }

        @Override public Transaction onReadWrite(Consumer<Transaction> consumer) {
            return null;
        }

        @Override public Transaction onClose(Consumer<Transaction> consumer) {
            return null;
        }

        @Override public void addTransactionListener(Consumer<Status> listener) {
        }

        @Override public void removeTransactionListener(Consumer<Status> listener) {
        }

        @Override public void clearTransactionListeners() {
        }
    }
}
