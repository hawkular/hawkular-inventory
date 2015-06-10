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

import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

import java.util.ServiceLoader;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class TinkerpopInventory extends BaseInventory<Element> {
    @Override
    protected InventoryBackend<Element> doInitialize(Configuration configuration) {
        InventoryContext<?> context = loadGraph(configuration);
        return new TinkerpopBackend(context);
    }

    private <T extends TransactionalGraph> InventoryContext<T> loadGraph(Configuration configuration) {
        @SuppressWarnings("unchecked")
        GraphProvider<T> gp = ServiceLoader.load(GraphProvider.class).iterator().next();

        T g = ensureIndices(gp, configuration);

        return new InventoryContext<>(this, g, gp);
    }

    private <T extends TransactionalGraph> T ensureIndices(GraphProvider<T> graphProvider, Configuration config) {
        T graph = graphProvider.instantiateGraph(config);

        graphProvider.ensureIndices(graph,
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(Constants.Property.__type.name(), String.class)
                        .withProperty(Constants.Property.__eid.name(), String.class).build(),
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(Constants.Property.__type.name(), String.class).build());

        return graph;
    }
}
