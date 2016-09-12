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
package org.hawkular.inventory.impl.tinkerpop;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.paths.CanonicalPath;

/**
 * Data needed by various services. Mostly coming from configuration.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class InventoryContext {

    private final Graph graph;
    private final TinkerpopInventory inventory;
    private final GraphProvider graphProvider;

    public InventoryContext(TinkerpopInventory inventory, Graph graph, GraphProvider graphProvider) {
        this.inventory = inventory;
        this.graph = graph;
        this.graphProvider = graphProvider;
    }

    public InventoryContext cloneWith(Graph graph) {
        return new InventoryContext(inventory, graph, graphProvider);
    }

    public TinkerpopInventory getInventory() {
        return inventory;
    }

    public Graph getGraph() {
        return graph;
    }

    public Graph startTransaction() {
        return graphProvider.startTransaction(graph);
    }

    public void commit() {
        graphProvider.commit(graph);
    }

    public void rollback() {
        graphProvider.rollback(graph);
    }

    public boolean isUniqueIndexSupported() {
        return graphProvider.isUniqueIndexSupported();
    }

    public boolean needsDraining() {
        return graphProvider.needsDraining();
    }

    public boolean isPreferringBigTransactions() {
        return graphProvider.isPreferringBigTransactions();
    }

    public RuntimeException translateException(RuntimeException inputException, CanonicalPath affectedPath) {
        return graphProvider.translateException(inputException, affectedPath);
    }

    public boolean requiresRollbackAfterFailure(Throwable t) {
        return graphProvider.requiresRollbackAfterFailure(t);
    }

}
