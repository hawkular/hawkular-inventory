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
package org.hawkular.inventory.cdi;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;
import org.hawkular.inventory.impl.tinkerpop.InventoryService;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
public class BasicInventoryProducer {

    @Inject
    private Event<InventoryInitialized> inventoryInitializedEvent;

    @Inject
    private Event<DisposingInventory> disposingInventoryEvent;

    @Produces
    @Singleton
    @Basic
    public Inventory getInventory() {
        Inventory inventory = initInventory();
        inventoryInitializedEvent.fire(new InventoryInitialized(inventory));
        return inventory;
    }

    public void close(@Disposes @Basic Inventory inventory) throws Exception {
        disposingInventoryEvent.fire(new DisposingInventory(inventory));
        dispose(inventory);
    }


    private Inventory initInventory() {
        // TODO this is crude and ties CDI to tinkerpop impl.
        // Once we have a more established way of configuring hawkular components, we can rewrite this to use a more
        // generic approach using ServiceLoader.

        Map<String, String> config = new HashMap<>();
        System.getProperties().forEach((k, v) -> config.put(k.toString(), v == null ? null : v.toString()));

        if (config.get("blueprints.graph") == null) {
            config.put("blueprints.graph", DummyTransactionalGraph.class.getName());
        }

        if (config.get("blueprints.tg.directory") == null) {
            config.put("blueprints.tg.directory", new File(config.get("jboss.server.data.dir"), "hawkular-inventory")
                    .getAbsolutePath());
        }

        InventoryService inventory = new InventoryService();

        inventory.initialize(org.hawkular.inventory.api.Configuration.builder()
                .withFeedIdStrategy(new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
//.withResultFilter(securityIntegration) results filtering not required for the current security model
                .withConfiguration(config).build());

        return inventory;
    }

    private void dispose(Inventory inventory) throws Exception {
        if (inventory != null) {
            inventory.close();
        }
    }

    public static class DummyTransactionalGraph extends TinkerGraph implements TransactionalGraph {
        public DummyTransactionalGraph(org.apache.commons.configuration.Configuration configuration) {
            super(configuration);
        }

        @Override
        public void commit() {
        }

        @Override
        public void stopTransaction(Conclusion conclusion) {
        }

        @Override
        public void rollback() {
        }
    }
}
