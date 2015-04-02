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
package org.hawkular.inventory.api.test;

import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.observable.Action;
import org.hawkular.inventory.api.observable.Interest;
import org.hawkular.inventory.api.observable.ObservableInventory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class ObservableInventoryTest {

    private ObservableInventory observableInventory;

    @Before
    public void init() {
        InventoryMock.rewire();
        observableInventory = new ObservableInventory(InventoryMock.inventory);
    }

    @Test
    public void testTenants() throws Exception {
        Tenant prototype = new Tenant("kachny");

        when(InventoryMock.tenantsReadWrite.create("kachny")).thenReturn(InventoryMock.tenantsSingle);
        when(InventoryMock.tenantsSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities()).thenReturn(Collections.emptySet());

        runTest(() -> {
            observableInventory.tenants().create("kachny");
            observableInventory.tenants().update(prototype);
            observableInventory.tenants().delete(prototype.getId());
        });
    }

    @Test
    public void testEnvironments() throws Exception {
        Environment prototype = new Environment("t", "e");

        when(InventoryMock.environmentsReadWrite.create("e")).thenReturn(InventoryMock.environmentsSingle);
        when(InventoryMock.environmentsSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Tenant("t"), prototype)));

        runTest(() -> {
            observableInventory.tenants().get("t").environments().create("e");
            observableInventory.tenants().get("t").environments().update(prototype);
            observableInventory.tenants().get("t").environments().delete(prototype.getId());
        });

        observableInventory.observable(Interest.in(Action.copy()).of(Environment.class).build());
    }

    private void runTest(Runnable payload) {
        List<Tenant> createdTenants = new ArrayList<>();
        List<Tenant> updatedTenants = new ArrayList<>();
        List<Tenant> deletedTenants = new ArrayList<>();

        Subscription s1 = observableInventory.observable(Interest.inCreate().of(Tenant.class).build())
                .subscribe(createdTenants::add);

        Subscription s2 = observableInventory.observable(Interest.inUpdate().of(Tenant.class).build())
                .subscribe(updatedTenants::add);

        Subscription s3 = observableInventory.observable(Interest.inDelete().of(Tenant.class).build())
                .subscribe(deletedTenants::add);

        //dummy observer just to check that unsubscription works
        observableInventory.observable(Interest.inCreate().of(Tenant.class).build())
                .subscribe((t) -> {});

        payload.run();

        Assert.assertEquals(1, createdTenants.size());
        Assert.assertEquals(1, updatedTenants.size());
        Assert.assertEquals(1, deletedTenants.size());

        s1.unsubscribe();
        s2.unsubscribe();
        s3.unsubscribe();

        Assert.assertTrue(observableInventory.hasObservers(Interest.inCreate().of(Tenant.class).build()));
        Assert.assertFalse(observableInventory.hasObservers(Interest.inUpdate().of(Tenant.class).build()));
        Assert.assertFalse(observableInventory.hasObservers(Interest.inDelete().of(Tenant.class).build()));
    }
}
