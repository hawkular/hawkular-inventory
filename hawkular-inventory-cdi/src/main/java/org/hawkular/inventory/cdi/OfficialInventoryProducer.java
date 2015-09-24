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
import org.hawkular.inventory.api.configuration.Configuration;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
@Singleton
public class OfficialInventoryProducer {

    private static final Configuration.Property IMPL_PROPERTY = Configuration.Property.builder()
            .withPropertyNameAndSystemProperty("hawkular.inventory.impl")
            .withEnvironmentVariables("HAWKULAR_INVENTORY_IMPL").build();

    @Inject
    private Event<InventoryInitialized> inventoryInitializedEvent;

    @Inject
    private Event<DisposingInventory> disposingInventoryEvent;

    @Inject
    private Instance<InventoryConfigurationData> configData;

    @Produces
    @Singleton
    @Official
    public Inventory getInventory() throws InterruptedException {
        Inventory inventory = initInventory();
        inventoryInitializedEvent.fire(new InventoryInitialized(inventory));
        return inventory;
    }

    public void close(@Disposes @Official Inventory inventory) throws Exception {
        disposingInventoryEvent.fire(new DisposingInventory(inventory));
        dispose(inventory);
    }

    private Inventory initInventory() throws InterruptedException {
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

        Configuration cfg = Configuration.builder()
                .withFeedIdStrategy(new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
                        //.withResultFilter(securityIntegration) results filtering not required for the current
                        // security model
                .withConfiguration(config).build();

        Inventory inventory = instantiateNew(cfg);

        LOG.iUsingImplementation(inventory.getClass().getName());

        int failures = 0;
        int maxFailures = 5;
        boolean initialized = false;
        while (!initialized && failures++ < maxFailures) {
            try {
                inventory.initialize(cfg);

                initialized = true;
            } catch (Exception e) {
                LOG.debugf("Unable to initialize inventory, exception thrown: ", e);
                LOG.wInitializationFailure(failures, maxFailures);
                Thread.sleep(1000);
            }
        }

        if (!initialized) {
            throw new IllegalStateException("Could not initialize inventory.");
        }

        LOG.iInitialized();

        return inventory;
    }

    private void dispose(Inventory inventory) throws Exception {
        if (inventory != null) {
            inventory.close();
        }
    }

    private Inventory instantiateNew(Configuration config) {
        String implClass = config.getProperty(IMPL_PROPERTY, null);
        if (implClass != null) {
            try {
                return (Inventory) Class.forName(implClass).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                throw new IllegalStateException("Failed to instantiate inventory using class '" + implClass + "'.", e);
            }
        } else {
            return ServiceLoader.load(Inventory.class).iterator().next();
        }
    }
}
