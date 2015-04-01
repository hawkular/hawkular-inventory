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

import org.junit.Assert;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.observable.Interest;
import org.hawkular.inventory.api.observable.ObservableInventory;
import org.junit.Before;
import org.junit.Test;
import rx.Subscription;

import java.util.ArrayList;
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

        List<Tenant> createdTenants = new ArrayList<>();
        List<Tenant> updatedTenants = new ArrayList<>();
        List<Tenant> deletedTenants = new ArrayList<>();

        Subscription s1 =observableInventory.observable(Interest.inCreate().of(Tenant.class).build())
                .subscribe(createdTenants::add);

        Subscription s2 = observableInventory.observable(Interest.inUpdate().of(Tenant.class).build())
                .subscribe(updatedTenants::add);

        Subscription s3 = observableInventory.observable(Interest.inDelete().of(Tenant.class).build())
                .subscribe(deletedTenants::add);

        observableInventory.tenants().create("kachny");
        observableInventory.tenants().update(prototype);
        observableInventory.tenants().delete(prototype.getId());

        Assert.assertEquals(1, createdTenants.size());
        Assert.assertEquals(prototype, createdTenants.get(0));
        Assert.assertEquals(1, updatedTenants.size());
        Assert.assertEquals(prototype, updatedTenants.get(0));
        Assert.assertEquals(1, deletedTenants.size());
        Assert.assertEquals(prototype, deletedTenants.get(0));

        //this is just for debugging that the interest map in the ObservableContext gets indeed cleared. There's no
        //direct testing possible as that object is an internal impl. detail of ObservableInventory.
        s1.unsubscribe();
        s2.unsubscribe();
        s3.unsubscribe();
    }
}
