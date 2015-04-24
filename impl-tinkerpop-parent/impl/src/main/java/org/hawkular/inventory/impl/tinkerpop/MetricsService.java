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
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Arrays;
import java.util.Iterator;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.environment;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.feed;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.metric;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.metricType;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.resource;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class MetricsService
        extends AbstractSourcedGraphService<Metrics.Single, Metrics.Multiple, Metric, Metric.Blueprint, Metric.Update>
        implements Metrics.ReadWrite, Metrics.Read, Metrics.ReadAssociate {

    MetricsService(InventoryContext context, PathContext ctx) {
        super(context, Metric.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, Metric.Blueprint blueprint) {
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
                    "creating a metric under path: " + Arrays.deepToString(FilterApplicator.filters(sourcePaths)));
        }

        if (feedVertex == null && envVertex == null) {
            throw new IllegalArgumentException("No feed or environment found to create the metric under path: " +
                    Arrays.deepToString(FilterApplicator.filters(sourcePaths)));
        }

        //everything seems to be fine, create the edge to the containing vertex
        addEdge(envVertex == null ? feedVertex : envVertex, contains.name(), newEntity);

        Vertex tenant = getTenantVertexOf(envVertex == null ? feedVertex : envVertex);

        //connect to the metric def in the blueprint
        Iterator<Vertex> mds = new HawkularPipeline<>(tenant).out(contains).hasType(metricType)
                .hasEid(blueprint.getMetricTypeId()).cast(Vertex.class);
        if (mds.hasNext()) {
            addEdge(mds.next(), Relationships.WellKnown.defines.name(), newEntity);
        } else {
            throw new IllegalArgumentException("Could not find metric type with id: " + blueprint.getMetricTypeId());
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

        return f.and(Related.by(contains), With.type(Metric.class), With.id(getEid(newEntity))).get();
    }

    @Override
    protected Metrics.Single createSingleBrowser(FilterApplicator.Tree path) {
        return MetricBrowser.single(context, path);
    }

    @Override
    protected String getProposedId(Metric.Blueprint b) {
        return b.getId();
    }

    @Override
    protected Metrics.Multiple createMultiBrowser(FilterApplicator.Tree path) {
        return MetricBrowser.multiple(context, path);
    }

    @Override
    public Relationship associate(String id) {
        //in here I know the source is a resource...
        Iterable<Vertex> vs = source().in(contains) //up from resource to environment (or feed)
                .out(contains).hasType(metric) //down to metrics
                .hasEid(id).cast(Vertex.class);

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
