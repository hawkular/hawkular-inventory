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

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.tenant;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class TypesService extends
        AbstractSourcedGraphService<ResourceTypes.Single, ResourceTypes.Multiple, ResourceType, ResourceType.Blueprint>
        implements ResourceTypes.ReadWrite, ResourceTypes.Read {

    TypesService(TransactionalGraph graph, PathContext ctx) {
        super(graph, ResourceType.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, ResourceType.Blueprint blueprint) {
        Vertex exampleTnt = null;
        for (Vertex t : source().hasType(tenant)) {
            t.addEdge(contains.name(), newEntity);
            exampleTnt = t;
        }

        newEntity.setProperty(Constants.Property.version.name(), blueprint.getVersion().toString());

        return Filter.by(With.type(Tenant.class), With.id(getUid(exampleTnt)), With.type(ResourceType.class),
                With.id(blueprint.getId())).get();
    }

    @Override
    protected ResourceTypes.Single createSingleBrowser(FilterApplicator... path) {
        return TypeBrowser.single(graph, path);
    }

    @Override
    protected ResourceTypes.Multiple createMultiBrowser(FilterApplicator... path) {
        return TypeBrowser.multiple(graph, path);
    }

    @Override
    protected String getProposedId(ResourceType.Blueprint b) {
        return b.getId();
    }

    @Override
    public void update(ResourceType entity) {
        //TODO implement

    }

    @Override
    public void delete(String id) {
        //TODO implement

    }
}
