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
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Arrays;
import java.util.Iterator;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.environment;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.feed;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.resourceType;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class ResourcesService
        extends AbstractSourcedGraphService<Resources.Single, Resources.Multiple, Resource, Resource.Blueprint,
        Resource.Update> implements Resources.ReadWrite, Resources.Read {

    public ResourcesService(InventoryContext context, PathContext ctx) {
        super(context, Resource.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, Resource.Blueprint blueprint) {
        //find an environment in the source, of which there should be at most one.
        Vertex envVertex = null;
        Iterator<Vertex> envs = source().hasType(environment);
        if (envs.hasNext()) {
            envVertex = envs.next();
        }

        //find a feed in the source, of which there should be at most one.
        Vertex feedVertex = null;
        Iterator<Vertex> fs = source().hasType(feed);
        if (fs.hasNext()) {
            feedVertex = fs.next();
        }

        if (feedVertex != null && envVertex != null) {
            throw new IllegalStateException("Found both a feed and an environment as a containing entity when " +
                    "creating a resource under path: " + Arrays.toString(FilterApplicator.filters(path)));
        }

        if (feedVertex == null && envVertex == null) {
            throw new IllegalArgumentException("No feed or environment found to create the resource under path: " +
                    Arrays.toString(FilterApplicator.filters(path)));
        }

        //everything seems to be fine, create the edge to the containing vertex
        addEdge(envVertex == null ? feedVertex : envVertex, contains.name(), newEntity);

        Vertex tenant = getTenantVertexOf(envVertex == null ? feedVertex : envVertex);

        //connect to the resource type from the blueprint
        Iterator<Vertex> rts = new HawkularPipeline<>(tenant).out(contains).hasType(resourceType)
                .hasEid(blueprint.getResourceTypeId()).cast(Vertex.class);
        if (rts.hasNext()) {
            addEdge(rts.next(), defines.name(), newEntity);
        } else {
            throw new IllegalArgumentException("Could not find resource type with id: " +
                    blueprint.getResourceTypeId());
        }

        //return the path to the new resource
        Filter.Accumulator f = Filter.by(With.type(Tenant.class), With.id(getEid(tenant)), Related.by(contains),
                With.type(Environment.class));

        if (feedVertex == null) {
            f.and(With.id(getEid(envVertex)));
        } else {
            Vertex env = getEnvironmentVertexOf(feedVertex);
            f.and(With.id(getEid(env)), Related.by(contains), With.type(Feed.class), With.id(getEid(feedVertex)));
        }

        return f.and(Related.by(contains), With.type(Resource.class), With.id(getEid(newEntity))).get();
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
}
