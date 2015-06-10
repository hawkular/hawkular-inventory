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
package org.hawkular.inventory.impl.tinkerpop.provider;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.wrapped.WrappedGraph;
import org.apache.commons.configuration.MapConfiguration;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class TinkerGraphProvider implements GraphProvider<TinkerGraphProvider.WrappedTinkerGraph> {

    private final WeakHashMap<WrappedTinkerGraph, ReentrantReadWriteLock> transactionLocks = new WeakHashMap<>();
    @Override
    public WrappedTinkerGraph instantiateGraph(Configuration configuration) {
        return new WrappedTinkerGraph(new MapConfiguration(configuration.getImplementationConfiguration()));
    }

    @Override
    public void ensureIndices(WrappedTinkerGraph graph, IndexSpec... indexSpecs) {
        //don't bother with this for a demo graph
    }

    @Override
    public InventoryBackend.Transaction startTransaction(WrappedTinkerGraph graph, boolean mutating) {
        ReentrantReadWriteLock lock;
        synchronized (transactionLocks) {
            lock = transactionLocks.get(graph);
            if (lock == null) {
                lock = new ReentrantReadWriteLock();
                transactionLocks.put(graph, lock);
            }
        }

        if (mutating) {
            lock.writeLock().lock();
        } else {
            lock.readLock().lock();
        }

        return new SimulatedSerializedTransaction(mutating, lock);
    }

    @Override
    public void commit(WrappedTinkerGraph graph, InventoryBackend.Transaction t) {
        graph.commit();
        unlock(t);
    }

    @Override
    public void rollback(WrappedTinkerGraph graph, InventoryBackend.Transaction t) {
        graph.rollback();
        unlock(t);
    }

    private void unlock(InventoryBackend.Transaction t) {
        ReentrantReadWriteLock lock = ((SimulatedSerializedTransaction) t).lock;
        if (t.isMutating()) {
            lock.writeLock().unlock();
        } else {
            lock.readLock().unlock();
        }
    }

    static final class WrappedTinkerGraph extends WrappedGraph<TinkerGraph> implements TransactionalGraph {

        public WrappedTinkerGraph(org.apache.commons.configuration.Configuration configuration) {
            super(new TinkerGraph(configuration));
        }

        @Override
        @SuppressWarnings("deprecation")
        public void stopTransaction(Conclusion conclusion) {
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }

    private static final class SimulatedSerializedTransaction extends InventoryBackend.Transaction {
        private final ReentrantReadWriteLock lock;

        public SimulatedSerializedTransaction(boolean mutating, ReentrantReadWriteLock lock) {
            super(mutating);
            this.lock = lock;
        }
    }
}
