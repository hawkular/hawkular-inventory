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
package org.hawkular.inventory.impl.tinkerpop;

import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Tenant;

/**
* @author Lukas Krejci
* @since 1.0
*/
final class TenantsService extends AbstractSourcedGraphService<Tenants.Single, Tenants.Multiple, Tenant,
        Tenant.Blueprint> implements Tenants.ReadWrite, Tenants.Read {

    public TenantsService(InventoryContext context) {
        super(context, Tenant.class, new PathContext(FilterApplicator.fromPath().get(),
                Filter.by(With.type(Tenant.class)).get()));
    }

    TenantsService(InventoryContext context, PathContext pathContext) {
        super(context, Tenant.class, pathContext);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, Tenant.Blueprint blueprint) {
        return Filter.by(With.type(Tenant.class), With.id(blueprint.getId())).get();
    }

    @Override
    protected Tenants.Single createSingleBrowser(FilterApplicator... path) {
        return TenantBrowser.single(context, path);
    }

    @Override
    protected Tenants.Multiple createMultiBrowser(FilterApplicator... path) {
        return TenantBrowser.multiple(context, path);
    }

    @Override
    protected String getProposedId(Tenant.Blueprint b) {
        return b.getId();
    }
}
