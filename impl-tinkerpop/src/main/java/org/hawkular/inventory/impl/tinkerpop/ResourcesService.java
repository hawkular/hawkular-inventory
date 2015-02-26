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
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.Tenant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.environment;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.resourceType;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class ResourcesService
        extends AbstractSourcedGraphService<Resources.Single, Resources.Multiple, Resource, Resource.Blueprint>
        implements Resources.ReadWrite, Resources.Read, Resources.ReadRelate {

    public ResourcesService(InventoryContext context, PathContext ctx) {
        super(context, Resource.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, Resource.Blueprint blueprint) {

        Vertex exampleEnv = null;
        List<Vertex> envs = new ArrayList<>();

        //connect to all environments in the source
        for (Vertex env : source().hasType(environment)) {
            env.addEdge(contains.name(), newEntity);
            envs.add(env);
            exampleEnv = env;
        }

        //connect to the resource type from the blueprint
        for (Vertex rt : new HawkularPipeline<>(envs).in(contains).out(contains).hasType(resourceType)
                .hasUid(blueprint.getType().getId())) {
            rt.addEdge(defines.name(), newEntity);
        }

        Vertex tenant = getTenantVertexOf(exampleEnv);

        return Filter.by(With.type(Tenant.class), With.id(getUid(tenant)), Related.by(contains),
                With.type(Environment.class), With.id(getUid(exampleEnv)), Related.by(contains),
                With.type(Resource.class), With.id(getUid(newEntity))).get();
    }

    @Override
    protected Resources.Single createSingleBrowser(FilterApplicator... path) {
        return ResourceBrowser.single(context, path);
    }

    @Override
    protected Resources.Multiple createMultiBrowser(FilterApplicator... path) {
        return ResourceBrowser.multiple(context, path);
    }

    @Override
    protected String getProposedId(Resource.Blueprint b) {
        return b.getId();
    }

    @Override
    public void update(Resource entity) {
        //TODO implement

    }

    @Override
    public void delete(String id) {
        Iterator<Vertex> vs = context.getGraph().getVertices(Constants.Property.uid.name(), id).iterator();
        if (!vs.hasNext()) {
            throw new IllegalArgumentException("Resource with id " + id + " doesn't not exist");
        }

        Vertex v = vs.next();

        if (vs.hasNext()) {
            throw new IllegalStateException("More than 1 resource with id " + id + " exists.");
        }

        context.getGraph().removeVertex(v);
        context.getGraph().commit();
    }

    @Override
    public void add(String id) {
        // TODO implelent
    }

    @Override
    public void remove(String id) {
        // TODO implelent
    }
}
