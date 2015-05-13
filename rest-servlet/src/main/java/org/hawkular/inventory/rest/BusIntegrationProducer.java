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
package org.hawkular.inventory.rest;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;
import org.hawkular.inventory.bus.BusIntegration;
import org.hawkular.inventory.bus.Configuration;
import org.hawkular.inventory.impl.tinkerpop.InventoryService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.naming.NamingException;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.hawkular.inventory.rest.RestApiLogger.LOGGER;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
@ApplicationScoped
public class BusIntegrationProducer {

    @Inject
    private Security security;

    @Produces @ApplicationScoped @ForRest
    public InventoryWithBus getBusIntegration() throws JMSException, NamingException {
        InventoryWithBus ret = new InventoryWithBus();

        SecurityIntegration securityIntegration = new SecurityIntegration(security);
        Inventory.Mixin.AutoTenantAndObservable inventory = Inventory.augment(instantiateInventory(securityIntegration))
                .autoTenant().observable().get();
        BusIntegration busIntegration = instantiateIntegration(inventory);

        securityIntegration.start(inventory);

        ret.setInventory(inventory);
        ret.setBusIntegration(busIntegration);
        ret.setSecurityIntegration(securityIntegration);

        return ret;
    }

    public void closeBusIntegration(@Disposes @ForRest InventoryWithBus integration) throws Exception {
        try {
            integration.getBusIntegration().stop();
        } finally {
            try {
                integration.getSecurityIntegration().stop();
            } finally {
                integration.getInventory().close();
            }
        }
    }

    private BusIntegration instantiateIntegration(Inventory.Mixin.Observable inventory) {
        BusIntegration ret = new BusIntegration(inventory);
        // TODO load this from somewhere
        ret.configure(Configuration.getDefaultConfiguration());

        try {
            ret.start();
        } catch (NamingException | JMSException e) {
            LOGGER.busInitializationFailed(e.getMessage());
        }

        return ret;
    }

    private Inventory instantiateInventory(SecurityIntegration securityIntegration) {
        // TODO this is crude and ties REST API to tinkerpop impl.
        // Once we have a more established way of configuring hawkular components, we can rewrite this to use a more
        // generic approach using ServiceLoader.

        Map<String, String> config = new HashMap<>();
        System.getProperties().forEach((k,v) -> config.put(k.toString(), v == null ? null : v.toString()));

        if (config.get("blueprints.graph") == null) {
            config.put("blueprints.graph", DummyTransactionalGraph.class.getName());
        }

        if (config.get("blueprints.tg.directory") == null) {
            config.put("blueprints.tg.directory", new File(config.get("jboss.server.data.dir"), "hawkular-inventory")
                    .getAbsolutePath());
        }

        Inventory i =new InventoryService();

        i.initialize(org.hawkular.inventory.api.Configuration.builder()
                .withFeedIdStrategy(new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
//.withResultFilter(securityIntegration) results filtering not required for the current security model
                .withConfiguration(config).build());

        return i;
    }

    public static class InventoryWithBus {
        private BusIntegration busIntegration;
        private SecurityIntegration securityIntegration;
        private Inventory.Mixin.AutoTenantAndObservable inventory;

        public BusIntegration getBusIntegration() {
            return busIntegration;
        }

        public SecurityIntegration getSecurityIntegration() {
            return securityIntegration;
        }

        public Inventory.Mixin.AutoTenantAndObservable getInventory() {
            return inventory;
        }

        private void setBusIntegration(BusIntegration busIntegration) {
            this.busIntegration = busIntegration;
        }

        private void setInventory(Inventory.Mixin.AutoTenantAndObservable inventory) {
            this.inventory = inventory;
        }

        private void setSecurityIntegration(SecurityIntegration securityIntegration) {
            this.securityIntegration = securityIntegration;
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
