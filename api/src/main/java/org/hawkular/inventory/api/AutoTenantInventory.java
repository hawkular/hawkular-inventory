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

import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Tenant;

/**
 * This is an adapter of {@link Inventory} that makes the creation of tenants transparent and at the same time
 * doesn't support reading all tenants.
 *
 * <p>This class exists to support the multi-tenant deployments of inventory where each tenant should be isolated. As
 * such the user of this API is able to obtain a tenant for some given ID and is able to delete or update a tenant of
 * some given ID but is not able to receive the IDs of the existing tenants.
 *
 * <p>This therefore assumes that tenant IDs are stored outside of the scope of inventory and some external security
 * model is making sure that only correct users are able to access concrete tenant IDs.
 *
 * @author Lukas Krejci
 * @since 0.0.2
 */
final class AutoTenantInventory implements Inventory, Inventory.Mixin.AutoTenant {

    private final Inventory inventory;

    public AutoTenantInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * @return a "write-only" access to the tenants. The
     * {@link org.hawkular.inventory.api.Tenants.ReadWrite#getAll(Filter...)} method always returns empty results,
     * {@link org.hawkular.inventory.api.EmptyInventory.TenantsReadWrite#get(String)} will always return a tenant,
     * creating one if needed. The rest of the methods behaves normally.
     */
    public Tenants.ReadWrite tenants() {
        return new Tenants.ReadWrite() {

            @Override
            public Tenants.Single create(Tenant.Blueprint blueprint) throws EntityAlreadyExistsException {
                return inventory.tenants().create(blueprint);
            }

            @Override
            public void update(String id, Tenant.Update update) throws EntityNotFoundException {
                inventory.tenants().update(id, update);
            }

            @Override
            public void delete(String id) throws EntityNotFoundException {
                inventory.tenants().delete(id);
            }

            @Override
            public Tenants.Single get(String id) throws EntityNotFoundException {
                Tenants.Single ret = inventory.tenants().get(id);
                if (ret.exists()) {
                    return ret;
                } else {
                    inventory.tenants().create(Tenant.Blueprint.builder().withId(id).build());
                    return ret;
                }
            }

            @Override
            public Tenants.Multiple getAll(Filter... filters) {
                return new EmptyInventory.TenantsMultiple();
            }
        };
    }

    public void initialize(Configuration configuration) {
        inventory.initialize(configuration);
    }

    public void close() throws Exception {
        inventory.close();
    }
}
