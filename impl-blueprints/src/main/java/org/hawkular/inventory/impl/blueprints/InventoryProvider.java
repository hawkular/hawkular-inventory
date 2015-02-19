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
package org.hawkular.inventory.impl.blueprints;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.wrapped.WrappedGraph;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Tenants;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class InventoryProvider implements Inventory {

    private final InventoryService service;
    private final TransactionalGraph graph;

    public InventoryProvider() {
        //TODO make this configurable

        graph = new DummyTransactionalGraph(new TinkerGraph());
        service = new InventoryService(graph);
    }

    @Override
    public Tenants.ReadWrite tenants() {
        return service.tenants();
    }

    @Override
    public void close() throws Exception {
        service.close();
        graph.shutdown();
    }

    private static class DummyTransactionalGraph extends WrappedGraph<TinkerGraph> implements TransactionalGraph {

        public DummyTransactionalGraph(TinkerGraph baseGraph) {
            super(baseGraph);
        }

        @Override
        @Deprecated
        public void stopTransaction(Conclusion conclusion) {
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }
}
