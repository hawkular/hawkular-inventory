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

package org.hawkular.inventory.impl.tinkerpop;

import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;

import com.tinkerpop.blueprints.TransactionalGraph;

/**
 * Data needed by various services. Mostly coming from configuration.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class InventoryContext<G extends TransactionalGraph> {

    private final G graph;
    private final TinkerpopInventory inventory;
    private final GraphProvider<G> graphProvider;

    public InventoryContext(TinkerpopInventory inventory, G graph, GraphProvider<G> graphProvider) {
        this.inventory = inventory;
        this.graph = graph;
        this.graphProvider = graphProvider;
    }

    public TinkerpopInventory getInventory() {
        return inventory;
    }

    public TransactionalGraph getGraph() {
        return graph;
    }

    public InventoryBackend.Transaction startTransaction(boolean mutating) {
        return graphProvider.startTransaction(graph, mutating);
    }

    public void commit(InventoryBackend.Transaction t) {
        graphProvider.commit(graph, t);
    }

    public void rollback(InventoryBackend.Transaction t) {
        graphProvider.rollback(graph, t);
    }

    public RuntimeException translateException(RuntimeException inputException) {
        return graphProvider.translateException(inputException);
    }
}
