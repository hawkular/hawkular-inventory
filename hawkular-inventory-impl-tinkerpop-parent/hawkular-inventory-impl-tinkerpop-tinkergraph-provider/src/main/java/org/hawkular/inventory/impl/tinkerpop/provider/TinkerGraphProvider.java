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
package org.hawkular.inventory.impl.tinkerpop.provider;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.util.AbstractTransaction;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedGraph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class TinkerGraphProvider implements GraphProvider {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final boolean prefersBigTxs;

    public TinkerGraphProvider() {
        String val = System.getProperty("TinkerGraphProvider.prefersBigTxs");
        prefersBigTxs = val == null || Boolean.parseBoolean(val);
    }

    @Override public boolean isUniqueIndexSupported() {
        return false;
    }

    @Override public boolean needsDraining() {
        return false;
    }

    @Override public boolean isPreferringBigTransactions() {
        //just for testing purposes... Otherwise tinkergraph doesn't actually care about this because it doesn't
        //support transactions anyway.
        return prefersBigTxs;
    }

    @Override
    public WrappedTinkerGraph instantiateGraph(Configuration configuration) {
        return new WrappedTinkerGraph(new MapConfiguration(
                configuration.getImplementationConfiguration(
                        Collections.singleton(PropertyKey.DIRECTORY_NAME))));
    }

    @Override
    public void ensureIndices(Graph graph, IndexSpec... indexSpecs) {
        //don't bother with this for a demo graph
    }

    @Override
    public Graph startTransaction(Graph graph) {
        GraphProvider.super.startTransaction(graph);
        lock.writeLock().lock();
        return graph;
    }

    @Override
    public void commit(Graph graph) {
        GraphProvider.super.commit(graph);
        lock.writeLock().unlock();
    }

    @Override
    public void rollback(Graph graph) {
        GraphProvider.super.rollback(graph);
        lock.writeLock().unlock();
    }

    private static final class WrappedTinkerGraph implements Graph, WrappedGraph<TinkerGraph> {

        private final TinkerGraph graph;

        WrappedTinkerGraph(org.apache.commons.configuration.Configuration configuration) {
            graph = TinkerGraph.open(configuration);
        }

        @Override public TinkerGraph getBaseGraph() {
            return graph;
        }

        public <E extends Element> void createIndex(String key,
                                                    Class<E> elementClass) {
            graph.createIndex(key, elementClass);
        }

        public <E extends Element> void dropIndex(String key,
                                                  Class<E> elementClass) {
            graph.dropIndex(key, elementClass);
        }

        public static TinkerGraph open() {
            return TinkerGraph.open();
        }

        public void clear() {
            graph.clear();
        }

        public static TinkerGraph open(org.apache.commons.configuration.Configuration configuration) {
            return TinkerGraph.open(configuration);
        }

        public <E extends Element> Set<String> getIndexedKeys(
                Class<E> elementClass) {
            return graph.getIndexedKeys(elementClass);
        }

        @Override public Vertex addVertex(Object... keyValues) {
            return graph.addVertex(keyValues);
        }

        @Override public void close() {
            graph.close();
        }

        @Override public GraphComputer compute() {
            return graph.compute();
        }

        @Override public <C extends GraphComputer> C compute(
                Class<C> graphComputerClass) {
            return graph.compute(graphComputerClass);
        }

        @Override public org.apache.commons.configuration.Configuration configuration() {
            return graph.configuration();
        }

        @Override public Iterator<Edge> edges(Object... edgeIds) {
            return graph.edges(edgeIds);
        }

        @Override public Features features() {
            return graph.features();
        }

        @Override public String toString() {
            return graph.toString();
        }

        @Override public Transaction tx() {
            return new AbstractTransaction(this) {
                private boolean open;

                @Override protected void doOpen() {
                    open = true;
                }

                @Override protected void doCommit() throws TransactionException {
                    open = false;
                }

                @Override protected void doRollback() throws TransactionException {
                    open = false;
                }

                @Override public boolean isOpen() {
                    return open;
                }
            };
        }

        @Override public Variables variables() {
            return graph.variables();
        }

        @Override public Iterator<Vertex> vertices(Object... vertexIds) {
            return graph.vertices(vertexIds);
        }

        @Override public Vertex addVertex(String label) {
            return graph.addVertex(label);
        }

        @Override public <I extends Io> I io(
                Io.Builder<I> builder) {
            return graph.io(builder);
        }

        @Override public GraphTraversalSource traversal() {
            return graph.traversal();
        }

        @Override public <C extends TraversalSource> C traversal(
                TraversalSource.Builder<C> sourceBuilder) {
            return graph.traversal(sourceBuilder);
        }
    }

    private enum PropertyKey implements Configuration.Property {
        DIRECTORY_NAME("blueprints.tg.directory", "blueprints.tg.directory", null);

        private final String propertyName;
        private final List<String> sysPropName;
        private final List<String> envVarName;

        PropertyKey(String propertyName, String sysPropName, String envVarName) {
            this.envVarName = envVarName == null ? Collections.emptyList() : Collections.singletonList(envVarName);
            this.propertyName = propertyName;
            this.sysPropName = sysPropName == null ? Collections.emptyList() : Collections.singletonList(sysPropName);
        }


        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public List<String> getSystemPropertyNames() {
            return sysPropName;
        }

        @Override
        public List<String> getEnvironmentVariableNames() {
            return envVarName;
        }
    }
}
