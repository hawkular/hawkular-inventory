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

package org.hawkular.inventory.impl.tinkerpop.lazy;

import com.tinkerpop.blueprints.TransactionalGraph;
import org.hawkular.inventory.api.FeedIdStrategy;
import org.hawkular.inventory.api.ResultFilter;

/**
 * Data needed by various services. Mostly coming from configuration.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class InventoryContext {

    private final FeedIdStrategy feedIdStrategy;
    private final ResultFilter resultFilter;
    private final TransactionalGraph graph;
    private final TinkerpopInventory inventory;

    public InventoryContext(TinkerpopInventory inventory, FeedIdStrategy feedIdStrategy, ResultFilter resultFilter,
            TransactionalGraph graph) {
        this.inventory = inventory;
        this.feedIdStrategy = feedIdStrategy;
        this.resultFilter = resultFilter;
        this.graph = graph;
    }

    public TinkerpopInventory getInventory() {
        return inventory;
    }

    public FeedIdStrategy getFeedIdStrategy() {
        return feedIdStrategy;
    }

    public ResultFilter getResultFilter() {
        return resultFilter;
    }

    public TransactionalGraph getGraph() {
        return graph;
    }
}
