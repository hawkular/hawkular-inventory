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
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;

import java.util.ArrayList;
import java.util.List;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.environment;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.metric;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.metricType;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.resource;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class MetricsService
        extends AbstractSourcedGraphService<Metrics.Single, Metrics.Multiple, Metric, Metric.Blueprint>
        implements Metrics.ReadWrite, Metrics.Read, Metrics.ReadAssociate {

    MetricsService(InventoryContext context, PathContext ctx) {
        super(context, Metric.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, Metric.Blueprint blueprint) {
        Vertex exampleEnv = null;
        List<Vertex> envs = new ArrayList<>();

        //connect to all environments in the source
        for (Vertex e : source().hasType(environment)) {
            e.addEdge(contains.name(), newEntity);
            envs.add(e);
            exampleEnv = e;
        }

        //connect to the metric def in the blueprint
        HawkularPipeline<?, Vertex> mds = new HawkularPipeline<>(envs) //from environments we're in
                .in(contains) //up to tenants
                .out(contains).hasType(metricType) //down to metric defs
                .hasUid(blueprint.getMetricTypeId()) //filter on our id
                .cast(Vertex.class);

        for (Vertex md : mds) {
            md.addEdge(Relationships.WellKnown.defines.name(), newEntity);
        }

        Vertex tenant = getTenantVertexOf(exampleEnv);

        return Filter.by(With.type(Tenant.class), With.id(getUid(tenant)), Related.by(contains),
                With.type(Environment.class), With.id(getUid(exampleEnv)), Related.by(contains),
                With.type(Metric.class), With.id(getUid(newEntity))).get();
    }

    @Override
    protected Metrics.Single createSingleBrowser(FilterApplicator... path) {
        return MetricBrowser.single(context, path);
    }

    @Override
    protected String getProposedId(Metric.Blueprint b) {
        return b.getId();
    }

    @Override
    protected Metrics.Multiple createMultiBrowser(FilterApplicator... path) {
        return MetricBrowser.multiple(context, path);
    }

    @Override
    public Relationship associate(String id) {
        //in here I know the source is a resource...
        Iterable<Vertex> vs = source().in(contains) //up from resource to environment
                .out(contains).hasType(metric) //down to metrics
                .hasUid(id).cast(Vertex.class);

        return super.addAssociation(Constants.Type.resource, owns, vs);
    }

    @Override
    public Relationship disassociate(String id) {
        return removeAssociation(resource, owns, id);
    }

    @Override
    public Relationship associationWith(String id) throws RelationNotFoundException {
        return findAssociation(id, owns.name());
    }
}
