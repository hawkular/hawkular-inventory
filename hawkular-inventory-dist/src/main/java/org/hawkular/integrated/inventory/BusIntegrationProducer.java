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
package org.hawkular.integrated.inventory;

import java.util.IdentityHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.jms.JMSException;
import javax.naming.NamingException;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.bus.BusIntegration;
import org.hawkular.inventory.bus.Configuration;
import org.hawkular.inventory.cdi.DisposingInventory;
import org.hawkular.inventory.cdi.InventoryInitialized;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
@ApplicationScoped
public class BusIntegrationProducer {

    private final IdentityHashMap<Inventory, BusIntegration> integrations = new IdentityHashMap<>();

    public void install(@Observes InventoryInitialized event) throws JMSException, NamingException {
        if ("true".equals(System.getProperty("inventory.bus.integration", "true"))) {
            BusIntegration integration = integrations.get(event.getInventory());
            if (integration == null) {
                integration = newIntegration(event.getInventory());
                integrations.put(event.getInventory(), integration);
            }
        }
    }

    public void close(@Observes DisposingInventory event) throws NamingException {
        BusIntegration integration = integrations.remove(event.getInventory());
        if (integration != null) {
            integration.stop();
        }
    }

    private BusIntegration newIntegration(Inventory inventory) {
        BusIntegration ret = new BusIntegration(inventory);
        // TODO load this from somewhere
        ret.configure(Configuration.getDefaultConfiguration());

        try {
            ret.start();
            Log.LOGGER.busInitializationSuccess();
        } catch (NamingException | JMSException e) {
            Log.LOGGER.busInitializationFailed(e);
        }

        return ret;
    }
}
