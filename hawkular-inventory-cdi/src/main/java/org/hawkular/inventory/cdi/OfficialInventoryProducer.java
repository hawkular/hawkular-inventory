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

import static org.hawkular.inventory.cdi.Log.LOG;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
@Singleton
public class OfficialInventoryProducer {

    @Inject
    private Event<InventoryInitialized> inventoryInitializedEvent;

    @Inject
    private Event<DisposingInventory> disposingInventoryEvent;

    @Inject
    private Instance<InventoryConfigurationData> configData;

    @Produces
    @Singleton
    @Official
    public Inventory getInventory() {
        Inventory inventory = initInventory();
        inventoryInitializedEvent.fire(new InventoryInitialized(inventory));
        return inventory;
    }

    public void close(@Disposes @Official Inventory inventory) throws Exception {
        disposingInventoryEvent.fire(new DisposingInventory(inventory));
        dispose(inventory);
    }

    private Inventory initInventory() {
        Map<String, String> config = new HashMap<>();

        if (!configData.isUnsatisfied()) {
            Properties conf = new Properties();

            try (Reader rdr = configData.get().open()) {
                conf.load(rdr);
            } catch (IOException e) {
                LOG.wCannotReadConfigurationFile(null, e);
            }

            conf.forEach((k, v) -> config.put(k.toString(), v == null ? null : v.toString()));
        }

        Inventory inventory = ServiceLoader.load(Inventory.class).iterator().next();

        int failures = 0;
        int maxFailures = 5;
        boolean initialized = false;
        while (failures++ < maxFailures) {
            try {
                inventory.initialize(org.hawkular.inventory.api.Configuration.builder()
                        .withFeedIdStrategy(new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
                                //.withResultFilter(securityIntegration) results filtering not required for the current
                                // security model
                        .withConfiguration(config).build());

                initialized = true;
            } catch (Exception e) {
                Log.LOG.wInitializationFailure(failures, maxFailures);
            }
        }

        if (!initialized) {
            throw new IllegalStateException("Could not initialize inventory.");
        }

        return inventory;
    }

    private void dispose(Inventory inventory) throws Exception {
        if (inventory != null) {
            inventory.close();
        }
    }
}
