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
package org.hawkular.inventory.api;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class ObservableInventory implements Inventory, Inventory.Mixin.Observable {

    private final Inventory inventory;

    private final ObservableContext context;

    ObservableInventory(Inventory inventory) {
        this.inventory = inventory;
        this.context = new ObservableContext();
    }

    @Override
    public void initialize(Configuration configuration) {
        inventory.initialize(configuration);
    }

    @Override
    public ObservableTenants.ReadWrite tenants() {
        return new ObservableTenants.ReadWrite(inventory.tenants(), context);
    }

    @Override
    public void close() throws Exception {
        inventory.close();
    }

    public <C, E> Observable<C> observable(Interest<C, E> interest) {
        return context.getObservableFor(interest);
    }

    public boolean hasObservers(Interest<?, ?> interest) {
        return context.isObserved(interest);
    }
}
