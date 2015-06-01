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

import com.tinkerpop.blueprints.TransactionalGraph;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private final InventoryService inventory;
    private final ReadWriteLock lock;

    public InventoryContext(InventoryService inventory, FeedIdStrategy feedIdStrategy, ResultFilter resultFilter,
            TransactionalGraph graph) {
        this.inventory = inventory;
        this.feedIdStrategy = feedIdStrategy;
        this.resultFilter = resultFilter;
        this.graph = graph;
        this.lock = new ReentrantReadWriteLock();
    }

    public InventoryService getInventory() {
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

    /**
     * A ReadWriteLock maintains a pair of associated locks, one for read-only operations and one for writing. The read
     * lock may be held simultaneously by multiple reader threads, so long as there are no writers.
     * The write lock is exclusive.
     */
    public ReadWriteLock getInventoryLock() {
        return lock;
    }
}
