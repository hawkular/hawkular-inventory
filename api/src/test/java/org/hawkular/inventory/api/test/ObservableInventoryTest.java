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

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hawkular.inventory.api.Action.copied;
import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.Action.updated;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class ObservableInventoryTest {

    private Inventory.Mixin.Observable observableInventory;

    @Before
    public void init() {
        InventoryMock.rewire();
        observableInventory = Inventory.augment(InventoryMock.inventory).observable().get();
    }

    @Test
    public void testTenants() throws Exception {
        Tenant prototype = new Tenant("kachny");

        Tenant.Blueprint blueprint = new Tenant.Blueprint("kachny");

        when(InventoryMock.tenantsReadWrite.create(blueprint))
                .thenReturn(InventoryMock.tenantsSingle);
        when(InventoryMock.tenantsSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities()).thenReturn(Collections.emptySet());

        runTest(Tenant.class, false, () -> {
            observableInventory.tenants().create(blueprint);
            observableInventory.tenants().update(prototype.getId(), Tenant.Update.builder().build());
            observableInventory.tenants().delete(prototype.getId());
        });
    }

    @Test
    public void testEnvironments() throws Exception {
        Environment prototype = new Environment("t", "e");

        Environment.Blueprint blueprint = new Environment.Blueprint("e");

        Environment.Update update = new Environment.Update(null);

        when(InventoryMock.environmentsReadWrite.create(blueprint))
                .thenReturn(InventoryMock.environmentsSingle);
        when(InventoryMock.environmentsSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Tenant("t"), prototype)));

        runTest(Environment.class, true, () -> {
            observableInventory.tenants().get("t").environments().create(blueprint);
            observableInventory.tenants().get("t").environments().update(prototype.getId(), update);
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

        ResourceType.Update update = new ResourceType.Update(null, "2.0.0");

        when(InventoryMock.resourceTypesReadWrite.create(any())).thenReturn(InventoryMock.resourceTypesSingle);
        when(InventoryMock.resourceTypesSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Tenant("t"), prototype)));

        runTest(ResourceType.class, true, () -> {
            observableInventory.tenants().get("t").resourceTypes()
                    .create(new ResourceType.Blueprint("rt", "1.0"));
            observableInventory.tenants().get("t").resourceTypes().update(prototype.getId(), update);
            observableInventory.tenants().get("t").resourceTypes().delete(prototype.getId());
        });

        when(InventoryMock.metricTypesReadAssociate.associate("mt"))
                .thenReturn(new Relationship("rt", "owns", prototype, new MetricType("t", "mt")));

        List<Relationship> createdRelatonships = new ArrayList<>();

        observableInventory.observable(Interest.in(Relationship.class).being(created()))
                .subscribe(createdRelatonships::add);

        observableInventory.tenants().get("t").resourceTypes().get("rt").metricTypes().associate("mt");

        Assert.assertEquals(1, createdRelatonships.size());
    }

    @Test
    public void testMetricTypes() throws Exception {
        MetricType prototype = new MetricType("t", "rt", MetricUnit.BYTE);

        MetricType.Update update = new MetricType.Update(null, MetricUnit.MILLI_SECOND);

        when(InventoryMock.metricTypesReadWrite.create(any())).thenReturn(InventoryMock.metricTypesSingle);
        when(InventoryMock.metricTypesSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Tenant("t"), prototype)));

        runTest(MetricType.class, true, () -> {
            observableInventory.tenants().get("t").metricTypes()
                    .create(new MetricType.Blueprint("rt", MetricUnit.BYTE));
            observableInventory.tenants().get("t").metricTypes().update(prototype.getId(), update);
            observableInventory.tenants().get("t").metricTypes().delete(prototype.getId());
        });
    }

    @Test
    public void testMetrics() throws Exception {
        Metric prototype = new Metric("t", "e", null, "m", new MetricType("t", "mt"));

        Metric.Update update = new Metric.Update(null);

        when(InventoryMock.metricsReadWrite.create(any())).thenReturn(InventoryMock.metricsSingle);
        when(InventoryMock.metricsSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Environment("t", "e"),
                        prototype)));

        runTest(Metric.class, true, () -> {
            observableInventory.tenants().get("t").environments().get("e").feedlessMetrics()
                    .create(new Metric.Blueprint("mt", "m"));
            observableInventory.tenants().get("t").environments().get("e").feedlessMetrics().update(prototype.getId(),
                    update);
            observableInventory.tenants().get("t").environments().get("e").feedlessMetrics().delete(prototype.getId());
        });
    }

    @Test
    public void testResources() throws Exception {
        Resource prototype = new Resource("t", "e", null, "r", new ResourceType("t", "rt", "1.0"));

        Resource.Update update = new Resource.Update(null);

        when(InventoryMock.resourcesReadWrite.create(any())).thenReturn(InventoryMock.resourcesSingle);
        when(InventoryMock.resourcesSingle.entity()).thenReturn(prototype);
        when(InventoryMock.relationshipsMultiple.entities())
                .thenReturn(Collections.singleton(new Relationship("r", "contains", new Environment("t", "e"),
                        prototype)));

        runTest(Resource.class, true, () -> {
            observableInventory.tenants().get("t").environments().get("e").feedlessResources()
                    .create(new Resource.Blueprint("r", "rt"));
            observableInventory.tenants().get("t").environments().get("e").feedlessResources().update(prototype.getId(),
                    update);
            observableInventory.tenants().get("t").environments().get("e").feedlessResources()
                    .delete(prototype.getId());
        });

        when(InventoryMock.metricsReadAssociate.associate("m"))
                .thenReturn(new Relationship("asdf", "owns", prototype,
                        new Metric("t", "e", null, "m", new MetricType("t", "mt"))));

        List<Relationship> createdRelatonships = new ArrayList<>();

        observableInventory.observable(Interest.in(Relationship.class).being(created()))
                .subscribe(createdRelatonships::add);

        observableInventory.tenants().get("t").environments().get("e").feedlessResources().get("rt").metrics()
                .associate("m");

        Assert.assertEquals(1, createdRelatonships.size());
    }

    private <T extends AbstractElement<?, U>, U extends AbstractElement.Update>
        void runTest(Class<T> entityClass, boolean watchRelationships, Runnable payload) {

        List<T> createdEntities = new ArrayList<>();
        List<Action.Update<T, U>> updatedEntities = new ArrayList<>();
        List<T> deletedEntities = new ArrayList<>();
        List<Relationship> createdRelationships = new ArrayList<>();

        Subscription s1 = observableInventory.observable(Interest.in(entityClass).being(created()))
                .subscribe(createdEntities::add);

        Subscription s2 = observableInventory.observable(Interest.in(entityClass).being(updated()))
                .subscribe(updatedEntities::add);

        Subscription s3 = observableInventory.observable(Interest.in(entityClass).being(deleted()))
                .subscribe(deletedEntities::add);

        observableInventory.observable(Interest.in(Relationship.class).being(created()))
                .subscribe(createdRelationships::add);

        //dummy observer just to check that unsubscription works
        observableInventory.observable(Interest.in(entityClass).being(created())).subscribe((t) -> {});

        payload.run();

        Assert.assertEquals(1, createdEntities.size());
        Assert.assertEquals(1, updatedEntities.size());
        Assert.assertEquals(1, deletedEntities.size());
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
