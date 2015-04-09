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
import com.tinkerpop.blueprints.util.ElementHelper;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.metricType;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.resourceType;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.tenant;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class MetricTypesService
        extends AbstractSourcedGraphService<MetricTypes.Single, MetricTypes.Multiple,
        MetricType, MetricType.Blueprint> implements MetricTypes.ReadWrite, MetricTypes.ReadAssociate {

    MetricTypesService(InventoryContext context, PathContext ctx) {
        super(context, MetricType.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, MetricType.Blueprint blueprint) {
        Vertex tnt = null;
        //connect to tenants in the source
        for (Vertex t : source().hasType(tenant)) {
            t.addEdge(contains.name(), newEntity);
            tnt = t;
        }

        newEntity.setProperty(Constants.Property.unit.name(), blueprint.getUnit().getDisplayName());
        // copy the properties
        ElementHelper.setProperties(newEntity, blueprint.getProperties());

        return Filter.by(With.type(Tenant.class), With.id(getUid(tnt)), Related.by(contains),
                With.type(MetricType.class), With.id(getUid(newEntity))).get();
    }

    @Override
    protected MetricTypes.Single createSingleBrowser(FilterApplicator... path) {
        return MetricTypeBrowser.single(context, path);
    }

    @Override
    protected MetricTypes.Multiple createMultiBrowser(FilterApplicator... path) {
        return MetricTypeBrowser.multiple(context, path);
    }

    @Override
    protected String getProposedId(MetricType.Blueprint b) {
        return b.getId();
    }

    @Override
    protected void updateExplicitProperties(MetricType entity, Vertex vertex) {
        vertex.setProperty(Constants.Property.unit.name(), entity.getUnit().getDisplayName());
    }

    @Override
    public Relationship associate(String id) {
        //in here I know the source is a resource type...
        Iterable<Vertex> vs = source().in(contains) //up from resource type to tenant
                .out(contains).hasType(metricType) //down to metric definitions
                .hasUid(id).cast(Vertex.class);

        return super.addAssociation(Constants.Type.resourceType, owns, vs);
    }

    @Override
    public Relationship disassociate(String id) {
        return removeAssociation(resourceType, owns, id);
    }

    @Override
    public Relationship associationWith(String id) throws RelationNotFoundException {
        return findAssociation(id, owns.name());
    }
}
