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
import org.hawkular.inventory.api.MetricDefinitions;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.MetricDefinition;
import org.hawkular.inventory.api.model.Tenant;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.metricDefinition;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.resourceType;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.tenant;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class MetricDefinitionsService
        extends AbstractSourcedGraphService<MetricDefinitions.Single, MetricDefinitions.Multiple,
        MetricDefinition, MetricDefinition.Blueprint>
        implements MetricDefinitions.ReadWrite, MetricDefinitions.ReadRelate {

    MetricDefinitionsService(TransactionalGraph graph, PathContext ctx) {
        super(graph, MetricDefinition.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, MetricDefinition.Blueprint blueprint) {
        Vertex tnt = null;
        //connect to tenants in the source
        for (Vertex t : source().hasType(tenant)) {
            t.addEdge(contains.name(), newEntity);
            tnt = t;
        }

        newEntity.setProperty(Constants.Property.unit.name(), blueprint.getUnit().getDisplayName());

        return Filter.by(With.type(Tenant.class), With.id(getUid(tnt)), With.type(MetricDefinition.class),
                With.id(blueprint.getId())).get();
    }

    @Override
    protected MetricDefinitions.Single createSingleBrowser(FilterApplicator... path) {
        return new MetricDefinitionBrowser(graph, path);
    }

    @Override
    protected MetricDefinitions.Multiple createMultiBrowser(FilterApplicator... path) {
        return new MetricDefinitionBrowser(graph, path);
    }

    @Override
    protected String getProposedId(MetricDefinition.Blueprint b) {
        return b.getId();
    }

    @Override
    public void update(MetricDefinition entity) {
        //TODO implement

    }

    @Override
    public void delete(String id) {
        //TODO implement

    }


    @Override
    public void add(String id) {
        //in here I know the source is a resource type...
        Iterable<Vertex> vs = source().in(contains) //up from resource type to tenant
                .out(contains).hasType(metricDefinition) //down to metric definitions
                .hasUid(id);

        super.addRelationship(Constants.Type.resourceType, owns, vs);
    }

    @Override
    public void remove(String id) {
        removeRelationship(resourceType, owns, id);
    }
}
