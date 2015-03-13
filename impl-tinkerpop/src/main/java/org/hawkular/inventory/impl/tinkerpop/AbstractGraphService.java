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

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Arrays;
import java.util.List;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractGraphService {
    protected final InventoryContext context;
    protected final FilterApplicator[] path;

    AbstractGraphService(InventoryContext context, FilterApplicator... path) {
        this.context = context;
        this.path = path;
    }

    protected HawkularPipeline<?, Vertex> source(FilterApplicator... filters) {
        HawkularPipeline<Object, Vertex> ret = new HawkularPipeline<>(new ResettableSingletonPipe<>(context.getGraph()))
                .V();

        for (FilterApplicator fa : path) {
            fa.applyTo(ret);
        }

        for (FilterApplicator fa : filters) {
            fa.applyTo(ret);
        }

        return ret;
    }

    protected FilterApplicator.Builder pathWith(Filter... filters) {
        return pathWith(path, filters);
    }

    public static FilterApplicator.Builder pathWith(FilterApplicator[] path, Filter... filters) {
        return FilterApplicator.from(path).and(FilterApplicator.Type.PATH, filters);
    }

    static String getProperty(Vertex v, Constants.Property property) {
        return v.getProperty(property.name());
    }

    static String getUid(Vertex v) {
        return getProperty(v, Constants.Property.uid);
    }

    static String getUid(Edge e) {
        return e.getProperty(Constants.Property.uid.name());
    }

    static String getType(Vertex v) {
        return getProperty(v, Constants.Property.type);
    }

    protected Vertex convert(Entity e) {
        HawkularPipeline<Object, Vertex> ret = new HawkularPipeline<>(new ResettableSingletonPipe<>(context.getGraph()))
                .V(Constants.Property.uid.name(), e.getId());
        Vertex vertex = null;
        if (ret.hasNext()) {
            vertex = ret.next();
        }
        return vertex;
    }

    static Entity convert(Vertex v) {
        Constants.Type type = Constants.Type.valueOf(getType(v));

        Vertex environmentVertex;

        Entity e;

        switch (type) {
            case environment:
                e = new Environment(getUid(getTenantVertexOf(v)), getUid(v));
                break;
            case feed:
                environmentVertex = getEnvironmentVertexOf(v);
                e = new Feed(getUid(getTenantVertexOf(environmentVertex)), getUid(environmentVertex), getUid(v));
                break;
            case metric:
                environmentVertex = getEnvironmentVertexOf(v);
                Vertex mdv = v.getVertices(Direction.IN, Constants.Relationship.defines.name()).iterator()
                        .next();
                MetricType md = (MetricType) convert(mdv);
                e = new Metric(getUid(getTenantVertexOf(environmentVertex)), getUid(environmentVertex), getUid(v),
                        md);
                break;
            case metricType:
                e = new MetricType(getUid(getTenantVertexOf(v)), getUid(v), MetricUnit.fromDisplayName(
                        getProperty(v, Constants.Property.unit)));
                break;
            case resource:
                environmentVertex = getEnvironmentVertexOf(v);
                Vertex rtv = v.getVertices(Direction.IN, Constants.Relationship.defines.name()).iterator().next();
                ResourceType rt = (ResourceType) convert(rtv);
                e = new Resource(getUid(getTenantVertexOf(environmentVertex)), getUid(environmentVertex), getUid(v),
                        rt);
                break;
            case resourceType:
                e = new ResourceType(getUid(getTenantVertexOf(v)), getUid(v), getProperty(v,
                        Constants.Property.version));
                break;
            case tenant:
                e = new Tenant(getUid(v));
                break;
            default:
                throw new IllegalArgumentException("Unknown type of vertex");
        }

        Entity ret = e;
        List<String> mappedProps = Arrays.asList(type.getMappedProperties());
        v.getPropertyKeys().forEach(k -> {
            if (!mappedProps.contains(k)) {
                ret.getProperties().put(k, v.getProperty(k));
            }
        });
        return ret;
    }

    static Vertex getTenantVertexOf(Vertex entityVertex) {
        Constants.Type type = Constants.Type.valueOf(getType(entityVertex));

        switch (type) {
            case environment:
            case metricType:
            case resourceType:
                return entityVertex.getVertices(Direction.IN, Constants.Relationship.contains.name()).iterator().next();
            case feed:
            case resource:
            case metric:
                return getTenantVertexOf(getEnvironmentVertexOf(entityVertex));
            default:
                return null;
        }
    }

    static Vertex getEnvironmentVertexOf(Vertex entityVertex) {
        Constants.Type type = Constants.Type.valueOf(getType(entityVertex));

        switch (type) {
            case feed:
            case resource:
            case metric:
                return entityVertex.getVertices(Direction.IN, Constants.Relationship.contains.name()).iterator().next();
            default:
                return null;
        }
    }
}
