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

import static org.hawkular.inventory.api.Action.created;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.PartiallyApplied;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.cdi.InventoryInitialized;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
@ApplicationScoped
public class TemporaryHacks {

    public void install(@Observes InventoryInitialized event) {
        Inventory inventory = event.getInventory();

        Observable<Tenant> tenantCreation = inventory.observable(Interest.in(Tenant.class).being(created()));
        tenantCreation.subscribe(PartiallyApplied.method(this::createTenantMetadata).second(inventory),
                Log.LOGGER::failedToAutoCreateEntities);
        tenantCreation.subscribe(PartiallyApplied.method(this::createTestEnvironment).second(inventory),
                Log.LOGGER::failedToAutoCreateEntities);
    }

    private void createTestEnvironment(Tenant tenant, Inventory inventory) {
        inventory.inspect(tenant).environments().create(Environment.Blueprint.builder().withId("test").build());
        Log.LOGGER.autoCreatedEntity("environment", "test", tenant.getId());
    }

    private void createTenantMetadata(Tenant tenant, Inventory inventory) {
        inventory.inspect(tenant).feedlessResourceTypes().create(ResourceType.Blueprint.builder().withId("URL")
            .build());
        Log.LOGGER.autoCreatedEntity("resource type", "URL", tenant.getId());

        inventory.inspect(tenant).feedlessMetricTypes()
                .create(MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("status.code.type")
                .withUnit(MetricUnit.NONE).build());
        Log.LOGGER.autoCreatedEntity("metric type", "status.code.type", tenant.getId());

        inventory.inspect(tenant).feedlessMetricTypes()
                .create(MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("status.duration.type")
                .withUnit(MetricUnit.MILLI_SECOND).build());
        Log.LOGGER.autoCreatedEntity("metric type", "status.duration.type", tenant.getId());
    }

}
