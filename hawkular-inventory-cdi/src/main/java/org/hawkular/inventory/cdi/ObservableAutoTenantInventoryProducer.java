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

import org.hawkular.inventory.api.Inventory;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
@Singleton
public class ObservableAutoTenantInventoryProducer {

    @Inject
    @Observable
    private Inventory.Mixin.Observable inventory;

    @Inject
    private Event<AutoTenantInventoryInitialized> autoTenantInventoryInitializedEvent;

    @Inject
    private Event<DisposingAutoTenantInventory> disposingAutoTenantInventoryEvent;

    @Produces
    @Singleton
    @ObservableAutoTenant
    public Inventory.Mixin.AutoTenantAndObservable getInventory() {
        Inventory.Mixin.AutoTenantAndObservable ret = Inventory.augment(inventory).autoTenant().get();
        autoTenantInventoryInitializedEvent.fire(new AutoTenantInventoryInitialized(ret));
        return ret;
    }

    public void close(@Disposes @ObservableAutoTenant Inventory.Mixin.AutoTenantAndObservable inventory)
            throws Exception {
        disposingAutoTenantInventoryEvent.fire(new DisposingAutoTenantInventory(inventory));
    }

}
