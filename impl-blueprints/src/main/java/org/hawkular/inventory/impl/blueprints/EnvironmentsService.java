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
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Tenant;

import static org.hawkular.inventory.impl.blueprints.Constants.Type.tenant;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class EnvironmentsService extends AbstractSourcedGraphService<EnvironmentBrowser, Environment, String>
        implements Environments.ReadWrite {

    public EnvironmentsService(TransactionalGraph graph, PathContext ctx) {
        super(graph, Environment.class, ctx);
    }

    @Override
    public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
        //TODO implement
        throw new UnsupportedOperationException();
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, String blueprint) {
        String tenantId = null;
        for (Vertex sourceTenant : source().hasType(tenant)) {
            tenantId = getUid(sourceTenant);
            sourceTenant.addEdge(Relationships.WellKnown.contains.name(), newEntity);
        }

        return Filter.by(With.type(Tenant.class), With.id(tenantId)).get();
    }

    @Override
    protected EnvironmentBrowser createBrowser(Filter... path) {
        return new EnvironmentBrowser(graph, path);
    }

    @Override
    protected String getProposedId(String b) {
        return b;
    }

    @Override
    public void update(Environment entity) {
        //TODO implement

    }

    @Override
    public void delete(String id) {
        //TODO implement

    }
}
