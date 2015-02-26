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

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.GraphFactory;
import com.tinkerpop.blueprints.TransactionalGraph;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Tenants;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class InventoryService implements Inventory {
    private InventoryContext context;

    @Override
    public void initialize(Configuration configuration) {
        Graph graph = GraphFactory.open(configuration.getImplementationConfiguration());
        if (!(graph instanceof TransactionalGraph)) {
            throw new IllegalArgumentException("Hawkular inventory requires a transactional graph implementation.");
        }

        context = new InventoryContext(this, configuration.getFeedIdStrategy(), (TransactionalGraph) graph);
    }

    @Override
    public Tenants.ReadWrite tenants() {
        return new TenantsService(context);
    }

    @Override
    public void close() throws Exception {
        context.getGraph().shutdown();
    }

    /**
     * Mainly for testing purposes.
     */
    public TransactionalGraph getGraph() {
        return context.getGraph();
    }
}
