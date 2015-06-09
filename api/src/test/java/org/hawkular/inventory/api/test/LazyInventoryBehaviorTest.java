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

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.LazyInventory;
import org.hawkular.inventory.lazy.spi.LazyInventoryBackend;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public class LazyInventoryBehaviorTest {

    private Inventory inventory;
    private LazyInventoryBackend<String> backend;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        backend = Mockito.mock(LazyInventoryBackend.class);

        inventory = new LazyInventory<String>() {
            @Override
            protected LazyInventoryBackend<String> doInitialize(Configuration configuration) {
                return backend;
            }
        };
        inventory.initialize(new Configuration(null, null, Collections.emptyMap()));
    }

    @Test
    public void testSuccessfulCreateTenant() {
        when(backend.query(any(), eq(Pager.single())))
                //impl needs to check there is no entity prior to creating it
                .thenReturn(new Page<>(Collections.emptyList(), Pager.single(), 0))
                        //fake the backend succeeded in saving it
                .thenReturn(new Page<>(Collections.singletonList("kachna"), Pager.single(), 1));

        //mock the conversion
        when(backend.convert(eq("kachna"), eq(Tenant.class)))
                .thenReturn(new Tenant("kachna"));

        Tenant t = inventory.tenants().create(Tenant.Blueprint.builder().withId("kachna").build()).entity();

        assertEquals("kachna", t.getId());
    }

    @Test(expected = EntityNotFoundException.class)
    public void testUnsuccessfulCreateTenant() {
        when(backend.query(any(), eq(Pager.single())))
                //impl needs to check there is no entity prior to creating it
                //the second time query is called is to check whether there is an entity - this will then fail
                .thenReturn(new Page<>(Collections.emptyList(), Pager.single(), 0));

        Tenant t = inventory.tenants().create(Tenant.Blueprint.builder().withId("kachna").build()).entity();
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void testCreatingExistingTenantFails() {
        when(backend.query(any(), eq(Pager.single())))
                //impl needs to check there is no entity prior to creating it. This should make it fail.
                .thenReturn(new Page<>(Collections.singletonList("kachna"), Pager.single(), 1));

        Tenant t = inventory.tenants().create(Tenant.Blueprint.builder().withId("kachna").build()).entity();
    }
}
