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
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

import java.util.ServiceLoader;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class InventoryService implements Inventory {
    private InventoryContext context;

    @Override
    @SuppressWarnings("unchecked")
    public void initialize(Configuration configuration) {
        GraphProvider gp = ServiceLoader.load(GraphProvider.class).iterator().next();

        TransactionalGraph graph = gp.instantiateGraph(configuration);

        gp.ensureIndices(graph,
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(Constants.Property.__type.name(), String.class)
                        .withProperty(Constants.Property.__eid.name(), String.class).build(),
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(Constants.Property.__type.name(), String.class).build());

        context = new InventoryContext(this, configuration.getFeedIdStrategy(), graph);
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
