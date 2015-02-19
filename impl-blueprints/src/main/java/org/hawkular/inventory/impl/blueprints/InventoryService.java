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
package org.hawkular.inventory.impl.blueprints;

import com.tinkerpop.blueprints.TransactionalGraph;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Tenants;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class InventoryService implements Inventory {
    private final TransactionalGraph graph;

    public InventoryService(TransactionalGraph graph) {
        this.graph = graph;
    }

    @Override
    public Tenants.ReadWrite tenants() {
        return new TenantsService(graph);
    }

    @Override
    public void close() throws Exception {
    }

}
