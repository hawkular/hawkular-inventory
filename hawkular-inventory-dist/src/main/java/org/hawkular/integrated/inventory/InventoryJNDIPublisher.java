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

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.cdi.Official;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
@Startup
@Singleton
public class InventoryJNDIPublisher {

    @Inject
    @Official
    private Inventory inventory;

    @PostConstruct
    public void publishInventory() {
        InitialContext ctx = null;
        try {
            ctx = new InitialContext();
            ctx.bind("java:global/Hawkular/Inventory", inventory);
        } catch (NamingException e) {
            throw new IllegalStateException("Could not register inventory in JNDI", e);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    //ignore
                }
            }
        }
    }
}
