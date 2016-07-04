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

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.hawkular.inventory.api.Configuration;
import org.junit.Assert;
import org.junit.Test;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Features;
import com.tinkerpop.blueprints.GraphQuery;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;

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
        TransactionalGraph g = gp.instantiateGraph(null);

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
        TransactionalGraph g = gp.instantiateGraph(null);

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

        @Override public TransactionalGraph instantiateGraph(Configuration configuration) {
            return new DummyTransactionalGraph();
        }

        @Override public void ensureIndices(TransactionalGraph graph, IndexSpec... indexSpecs) {
        }
    }

    private static class DummyTransactionalGraph implements TransactionalGraph {

        @Override public void stopTransaction(Conclusion conclusion) {
        }

        @Override public void commit() {
        }

        @Override public void rollback() {
        }

        @Override public Features getFeatures() {
            return null;
        }

        @Override public Vertex addVertex(Object id) {
            return null;
        }

        @Override public Vertex getVertex(Object id) {
            return null;
        }

        @Override public void removeVertex(Vertex vertex) {
        }

        @Override public Iterable<Vertex> getVertices() {
            return null;
        }

        @Override public Iterable<Vertex> getVertices(String key, Object value) {
            return null;
        }

        @Override public Edge addEdge(Object id, Vertex outVertex, Vertex inVertex, String label) {
            return null;
        }

        @Override public Edge getEdge(Object id) {
            return null;
        }

        @Override public void removeEdge(Edge edge) {
        }

        @Override public Iterable<Edge> getEdges() {
            return null;
        }

        @Override public Iterable<Edge> getEdges(String key, Object value) {
            return null;
        }

        @Override public GraphQuery query() {
            return null;
        }

        @Override public void shutdown() {
        }
    }
}
