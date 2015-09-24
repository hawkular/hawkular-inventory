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

import java.util.ServiceLoader;

import org.hawkular.inventory.api.configuration.Configuration;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class TinkerpopInventory extends BaseInventory<Element> {
    public static final Configuration.Property GRAPH_PROVIDER_IMPL_CLASS = Configuration.Property.builder()
            .withPropertyNameAndSystemProperty("hawkular.inventory.tinkerpop.graph-provider-impl")
            .withEnvironmentVariables("HAWKULAR_INVENTORY_TINKERPOP_GRAPH_PROVIDER_IMPL").build();

    @Override
    protected InventoryBackend<Element> doInitialize(Configuration configuration) {
        InventoryContext<?> context = loadGraph(configuration);
        return new TinkerpopBackend(context);
    }

    private <T extends TransactionalGraph> InventoryContext<T> loadGraph(Configuration configuration) {
        @SuppressWarnings("unchecked")
        GraphProvider<T> gp = (GraphProvider<T>) instantiateGraphProvider(configuration);

        Log.LOG.iUsingGraphProvider(gp.getClass().getName());

        T g = ensureIndices(gp, configuration);

        return new InventoryContext<>(this, g, gp);
    }

    private <T extends TransactionalGraph> T ensureIndices(GraphProvider<T> graphProvider, Configuration config) {
        T graph = graphProvider.instantiateGraph(config);

        graphProvider.ensureIndices(graph,
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(IndexSpec.Property.builder()
                                .withName(Constants.Property.__cp.name())
                                .withType(String.class)
                                .withUnique(true)
                                        // this breaks the resource-metric association in REST (not sure why)
//                                .withLabelIndex(Relationships.WellKnown.contains.name())
                                .build())
                        .withUnique(true)
                        .build(),
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(IndexSpec.Property.builder()
                                .withName(Constants.Property.__eid.name())
                                .withType(String.class)
                                .withUnique(true)
                                .build())
                        .build(),
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(IndexSpec.Property.builder()
                                .withName(Constants.Property.__type.name())
                                .withType(String.class)
//                                .withUnique(true)
                                .build())
                        .withProperty(IndexSpec.Property.builder()
                                .withName(Constants.Property.__eid.name())
                                .withType(String.class)
                                .build())
                        .build(),
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(IndexSpec.Property.builder()
                                .withName(Constants.Property.__type.name())
                                .withType(String.class)
                                .build())
                        .build(),
                IndexSpec.builder()
                        .withElementType(Vertex.class)
                        .withProperty(IndexSpec.Property.builder()
                                .withName(Constants.Property.__type.name())
                                .withType(String.class)
                                .build())
                        .withProperty(IndexSpec.Property.builder()
                                .withName(Constants.Property.__metric_data_type.name())
                                .withType(String.class)
//                                .withUnique(true)
                                .build())
                        .build());
        return graph;
    }

    private GraphProvider<?> instantiateGraphProvider(Configuration config) {
        String implClass = config.getProperty(GRAPH_PROVIDER_IMPL_CLASS, null);
        if (implClass != null) {
            try {
                return (GraphProvider<?>) Class.forName(implClass).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                throw new IllegalStateException("Could not instantiate graph provider class '" + implClass + "'.", e);
            }
        } else {
            return ServiceLoader.load(GraphProvider.class).iterator().next();
        }
    }
}
