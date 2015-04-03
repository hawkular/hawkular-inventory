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
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.model.Version;
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

import static org.hawkular.inventory.api.observable.Action.copied;
import static org.hawkular.inventory.api.observable.Action.created;
import static org.hawkular.inventory.api.observable.Action.deleted;
import static org.hawkular.inventory.api.observable.Action.updated;
import static org.mockito.Matchers.any;
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

        runTest(Tenant.class, false, () -> {
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

        runTest(Environment.class, true, () -> {
            observableInventory.tenants().get("t").environments().create("e");
            observableInventory.tenants().get("t").environments().update(prototype);
            observableInventory.tenants().get("t").environments().delete(prototype.getId());
        });

        List<Action.EnvironmentCopy> copied = new ArrayList<>();

        observableInventory.observable(Interest.in(Environment.class).being(copied())).subscribe(copied::add);

        observableInventory.tenants().get("t").environments().copy("1", "2");

        Assert.assertEquals(1, copied.size());
    }

    @Test
    public void testResourceTypes() throws Exception {
        ResourceType prototype = new ResourceType("t", "rt", "1.0.0");

        when(InventoryMock.resourceTypesReadWrite.create(any())).thenReturn(InventoryMock.resourceTypesSingle);
        when(InventoryMock.resourceTypesSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Tenant("t"), prototype)));

        runTest(ResourceType.class, true, () -> {
            observableInventory.tenants().get("t").resourceTypes()
                    .create(new ResourceType.Blueprint("rt", new Version("1.0")));
            observableInventory.tenants().get("t").resourceTypes().update(prototype);
            observableInventory.tenants().get("t").resourceTypes().delete(prototype.getId());
        });

        when(InventoryMock.metricTypesReadAssociate.associate("mt"))
                .thenReturn(new Relationship("rt", "owns", prototype, new MetricType("t", "mt")));

        List<Relationship> createdRelatonships = new ArrayList<>();

        observableInventory.observable(Interest.in(Relationship.class).<Relationship>being(created()))
                .subscribe(createdRelatonships::add);

        observableInventory.tenants().get("t").resourceTypes().get("rt").metricTypes().associate("mt");

        Assert.assertEquals(1, createdRelatonships.size());
    }

    @Test
    public void testMetricTypes() throws Exception {
        MetricType prototype = new MetricType("t", "rt", MetricUnit.BYTE);

        when(InventoryMock.metricTypesReadWrite.create(any())).thenReturn(InventoryMock.metricTypesSingle);
        when(InventoryMock.metricTypesSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Tenant("t"), prototype)));

        runTest(MetricType.class, true, () -> {
            observableInventory.tenants().get("t").metricTypes()
                    .create(new MetricType.Blueprint("rt", MetricUnit.BYTE));
            observableInventory.tenants().get("t").metricTypes().update(prototype);
            observableInventory.tenants().get("t").metricTypes().delete(prototype.getId());
        });
    }

    @Test
    public void testMetrics() throws Exception {
        Metric prototype = new Metric("t", "e", "m", new MetricType("t", "mt"));

        when(InventoryMock.metricsReadWrite.create(any())).thenReturn(InventoryMock.metricsSingle);
        when(InventoryMock.metricsSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Environment("t", "e"),
                        prototype)));

        runTest(Metric.class, true, () -> {
            observableInventory.tenants().get("t").environments().get("e").metrics()
                    .create(new Metric.Blueprint(new MetricType("t", "mt"), "m"));
            observableInventory.tenants().get("t").environments().get("e").metrics().update(prototype);
            observableInventory.tenants().get("t").environments().get("e").metrics().delete(prototype.getId());
        });
    }

    @Test
    public void testResources() throws Exception {
        Resource prototype = new Resource("t", "e", "r", new ResourceType("t", "rt", "1.0"));

        when(InventoryMock.resourcesReadWrite.create(any())).thenReturn(InventoryMock.resourcesSingle);
        when(InventoryMock.resourcesSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Environment("t", "e"), prototype)));

        runTest(Resource.class, true, () -> {
            observableInventory.tenants().get("t").environments().get("e").resources()
                    .create(new Resource.Blueprint("r", new ResourceType("t", "rt", "1.0")));
            observableInventory.tenants().get("t").environments().get("e").resources().update(prototype);
            observableInventory.tenants().get("t").environments().get("e").resources().delete(prototype.getId());
        });

        when(InventoryMock.metricsReadAssociate.associate("m"))
                .thenReturn(new Relationship("asdf", "owns", prototype,
                        new Metric("t", "e", "mt", new MetricType("t", "mt"))));

        List<Relationship> createdRelatonships = new ArrayList<>();

        observableInventory.observable(Interest.in(Relationship.class).<Relationship>being(created()))
                .subscribe(createdRelatonships::add);

        observableInventory.tenants().get("t").environments().get("e").resources().get("rt").metrics().associate("m");

        Assert.assertEquals(1, createdRelatonships.size());
    }

    private <T> void runTest(Class<T> entityClass, boolean watchRelationships, Runnable payload) {
        List<T> createdTenants = new ArrayList<>();
        List<T> updatedTenants = new ArrayList<>();
        List<T> deletedTenants = new ArrayList<>();
        List<Relationship> createdRelationships = new ArrayList<>();

        Subscription s1 = observableInventory.observable(Interest.in(entityClass).<T>being(created()))
                .subscribe(createdTenants::add);

        Subscription s2 = observableInventory.observable(Interest.in(entityClass).<T>being(updated()))
                .subscribe(updatedTenants::add);

        Subscription s3 = observableInventory.observable(Interest.in(entityClass).<T>being(deleted()))
                .subscribe(deletedTenants::add);

        observableInventory.observable(Interest.in(Relationship.class).<Relationship>being(created()))
                .subscribe(createdRelationships::add);

        //dummy observer just to check that unsubscription works
        observableInventory.observable(Interest.in(entityClass).<T>being(created())).subscribe((t) -> {});

        payload.run();

        Assert.assertEquals(1, createdTenants.size());
        Assert.assertEquals(1, updatedTenants.size());
        Assert.assertEquals(1, deletedTenants.size());
        if (watchRelationships) {
            Assert.assertEquals(1, createdRelationships.size());
        }

        s1.unsubscribe();
        s2.unsubscribe();
        s3.unsubscribe();

        Assert.assertTrue(observableInventory.hasObservers(Interest.in(entityClass).being(created())));
        Assert.assertFalse(observableInventory.hasObservers(Interest.in(entityClass).being(updated())));
        Assert.assertFalse(observableInventory.hasObservers(Interest.in(entityClass).being(deleted())));
    }
}
