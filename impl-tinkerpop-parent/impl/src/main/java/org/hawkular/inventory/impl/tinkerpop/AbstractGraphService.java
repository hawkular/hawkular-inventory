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
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.EntityVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.impl.tinkerpop.Constants.Type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.environment;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.feed;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractGraphService {
    protected final InventoryContext context;
    protected final FilterApplicator.Tree sourcePaths;

    AbstractGraphService(InventoryContext context, FilterApplicator.Tree sourcePaths) {
        this.context = context;
        this.sourcePaths = sourcePaths;
    }

    protected HawkularPipeline<?, Vertex> source() {
        return source(null);
    }

    protected HawkularPipeline<?, Vertex> source(FilterApplicator.Tree filters) {
        HawkularPipeline<Object, Vertex> ret = new HawkularPipeline<>(context.getGraph()).V();

        FilterApplicator.applyAll(sourcePaths, ret);

        FilterApplicator.applyAll(filters, ret);

        return ret;
    }

    protected FilterApplicator.SymmetricTreeExtender pathWith(Filter... filters) {
        Filter[][] fs = new Filter[1][];
        fs[0] = filters;
        return pathWith(sourcePaths, fs);
    }

    protected FilterApplicator.SymmetricTreeExtender pathWith(Filter[][] filters) {
        return pathWith(sourcePaths, filters);
    }

    public static FilterApplicator.SymmetricTreeExtender pathWith(FilterApplicator.Tree sourcePaths, Filter[][] filters) {
        return FilterApplicator.from(sourcePaths).and(FilterApplicator.Type.PATH, filters);
    }

    public static FilterApplicator.SymmetricTreeExtender pathWith(FilterApplicator.Tree sourcePaths, Filter... filters) {
        Filter[][] fs = new Filter[1][];
        fs[0] = filters;
        return FilterApplicator.from(sourcePaths).and(FilterApplicator.Type.PATH, fs);
    }

    static String getProperty(Vertex v, Constants.Property property) {
        return v.getProperty(property.name());
    }

    static String getEid(Vertex v) {
        return getProperty(v, Constants.Property.__eid);
    }

    static String getEid(Edge e) {
        return e.getProperty(Constants.Property.__eid.name());
    }

    static String getType(Vertex v) {
        return getProperty(v, Constants.Property.__type);
    }

    protected Edge addEdge(Vertex source, String label, Vertex target) {
        Edge e = source.addEdge(label, target);
        e.setProperty(Constants.Property.__eid.name(), e.getId());
        return e;
    }

    protected Vertex convert(Entity<?, ?> e) {
        HawkularPipeline<Object, Vertex> ret = new HawkularPipeline<>(context.getGraph())
                .V();
        HawkularPipeline<?, ? extends Element> vs =
                e.accept(new EntityVisitor<HawkularPipeline<?, ? extends Element>, Void>() {

                    @Override
                    public HawkularPipeline<?, ? extends Element> visitTenant(Tenant tenant, Void ignored) {
                        return ret.hasType(Type.tenant);
                    }

                    @Override
                    public HawkularPipeline<?, ? extends Element> visitEnvironment(Environment environment,
                                                                                   Void ignored) {
                        return ret.hasType(Type.tenant).hasEid(environment.getTenantId()).out(contains)
                                .hasType(Type.environment);
                    }

                    @Override
                    public HawkularPipeline<?, ? extends Element> visitFeed(Feed feed, Void ignored) {
                        return ret.hasType(Type.tenant).hasEid(feed.getTenantId()).out(contains)
                                .hasType(environment).hasEid(feed.getEnvironmentId()).out(contains)
                                .hasType(Type.feed);
                    }

                    @Override
                    public HawkularPipeline<?, ? extends Element> visitMetric(Metric metric, Void ignored) {
                        if (metric.getFeedId() == null) {
                            return ret.hasType(Type.tenant).hasEid(metric.getTenantId()).out(contains)
                                    .hasType(environment).hasEid(metric.getEnvironmentId()).out(contains)
                                    .hasType(Type.metric);
                        } else {
                            return ret.hasType(Type.tenant).hasEid(metric.getTenantId()).out(contains)
                                    .hasType(environment).hasEid(metric.getEnvironmentId()).out(contains)
                                    .hasType(feed).hasEid(metric.getFeedId()).hasType(Type.metric);
                        }
                    }

                    @Override
                    public HawkularPipeline<?, ? extends Element> visitMetricType(MetricType type, Void ignored) {
                        return ret.hasType(Type.tenant).hasEid(type.getTenantId()).out(contains)
                                .hasType(Type.metricType);
                    }

                    @Override
                    public HawkularPipeline<?, ? extends Element> visitResource(Resource resource, Void ignored) {
                        if (resource.getFeedId() == null) {
                            return ret.hasType(Type.tenant).hasEid(resource.getTenantId()).out(contains)
                                    .hasType(environment).hasEid(resource.getEnvironmentId()).out(contains)
                                    .hasType(Type.resource);
                        } else {
                            return ret.hasType(Type.tenant).hasEid(resource.getTenantId()).out(contains)
                                    .hasType(environment).hasEid(resource.getEnvironmentId()).out(contains)
                                    .hasType(feed).hasEid(resource.getFeedId()).hasType(Type.resource);
                        }
                    }

                    @Override
                    public HawkularPipeline<?, ? extends Element> visitResourceType(ResourceType type, Void ignored) {
                        return ret.hasType(Type.tenant).hasEid(type.getTenantId()).out(contains)
                                .hasType(Type.resourceType);
                    }
                }, null);

        vs = vs.hasEid(e.getId());

        return vs.hasNext() ? vs.cast(Vertex.class).next() : null;
    }

    static Entity<?, ?> convert(Vertex v) {
        Type type = Type.valueOf(getType(v));

        Vertex environmentVertex;
        Vertex feedVertex;

        Entity<?, ?> e;

        switch (type) {
            case environment:
                e = new Environment(getEid(getTenantVertexOf(v)), getEid(v));
                break;
            case feed:
                environmentVertex = getEnvironmentVertexOf(v);
                e = new Feed(getEid(getTenantVertexOf(environmentVertex)), getEid(environmentVertex), getEid(v));
                break;
            case metric:
                environmentVertex = getEnvironmentVertexOrNull(v);
                feedVertex = getFeedVertexOrNull(v);
                if (environmentVertex == null) {
                    environmentVertex = getEnvironmentVertexOf(feedVertex);
                }
                Vertex mdv = v.getVertices(Direction.IN, Relationships.WellKnown.defines.name()).iterator()
                        .next();
                MetricType md = (MetricType) convert(mdv);
                e = new Metric(getEid(getTenantVertexOf(environmentVertex)), getEid(environmentVertex),
                        feedVertex == null ? null : getEid(feedVertex), getEid(v),md);
                break;
            case metricType:
                e = new MetricType(getEid(getTenantVertexOf(v)), getEid(v), MetricUnit.fromDisplayName(
                        getProperty(v, Constants.Property.__unit)));
                break;
            case resource:
                environmentVertex = getEnvironmentVertexOrNull(v);
                feedVertex = getFeedVertexOrNull(v);
                if (environmentVertex == null) {
                    environmentVertex = getEnvironmentVertexOf(feedVertex);
                }
                Vertex rtv = v.getVertices(Direction.IN, Relationships.WellKnown.defines.name()).iterator().next();
                ResourceType rt = (ResourceType) convert(rtv);
                e = new Resource(getEid(getTenantVertexOf(environmentVertex)), getEid(environmentVertex),
                        feedVertex == null ? null : getEid(feedVertex), getEid(v), rt);
                break;
            case resourceType:
                e = new ResourceType(getEid(getTenantVertexOf(v)), getEid(v), getProperty(v,
                        Constants.Property.__version));
                break;
            case tenant:
                e = new Tenant(getEid(v));
                break;
            default:
                throw new IllegalArgumentException("Unknown type of vertex");
        }

        List<String> mappedProps = Arrays.asList(type.getMappedProperties());
        Map<String, Object> filteredProperties = new HashMap<>();
        v.getPropertyKeys().forEach(k -> {
            if (!mappedProps.contains(k)) {
                filteredProperties.put(k, v.getProperty(k));
            }
        });

        return e.accept(new EntityVisitor<Entity<?, ?>, Void>() {
            @Override
            public Entity<?, ?> visitTenant(Tenant tenant, Void ignored) {
                return tenant.update().with(Tenant.Update.builder().withProperties(filteredProperties).build());
            }

            @Override
            public Entity<?, ?> visitEnvironment(Environment environment, Void ignored) {
                return environment.update().with(Environment.Update.builder().withProperties(filteredProperties)
                        .build());
            }

            @Override
            public Entity<?, ?> visitFeed(Feed feed, Void ignored) {
                return feed.update().with(Feed.Update.builder().withProperties(filteredProperties).build());
            }

            @Override
            public Entity<?, ?> visitMetric(Metric metric, Void ignored) {
                return metric.update().with(Metric.Update.builder().withProperties(filteredProperties).build());
            }

            @Override
            public Entity<?, ?> visitMetricType(MetricType metricType, Void ignored) {
                return metricType.update().with(MetricType.Update.builder().withProperties(filteredProperties).build());
            }

            @Override
            public Entity<?, ?> visitResource(Resource resource, Void ignored) {
                return resource.update().with(Resource.Update.builder().withProperties(filteredProperties).build());
            }

            @Override
            public Entity<?, ?> visitResourceType(ResourceType type, Void ignored) {
                return type.update().with(ResourceType.Update.builder().withProperties(filteredProperties).build());
            }
        }, null);
    }

    static boolean matches(Vertex v, Entity e) {
        return Type.valueOf(getType(v)) == Type.of(e)
                && getEid(v).equals(e.getId());
    }

    static Vertex getTenantVertexOf(Vertex entityVertex) {
        Type type = Type.valueOf(getType(entityVertex));

        switch (type) {
            case environment:
            case metricType:
            case resourceType:
                return entityVertex.getVertices(Direction.IN, Relationships.WellKnown.contains.name()).iterator()
                        .next();
            case feed:
            case resource:
            case metric:
                return getTenantVertexOf(getEnvironmentVertexOf(entityVertex));
            default:
                return null;
        }
    }

    static Vertex getEnvironmentVertexOf(Vertex entityVertex) {
        Type type = Type.valueOf(getType(entityVertex));

        switch (type) {
            case feed:
            case resource:
            case metric:
                return new HawkularPipeline<>(entityVertex).in(contains).hasType(environment).iterator().next();
            default:
                return null;
        }
    }

    static Vertex getEnvironmentVertexOrNull(Vertex entityVertex) {
        Type type = Type.valueOf(getType(entityVertex));

        switch (type) {
            case feed:
            case resource:
            case metric:
                Iterator<Vertex> envs = new HawkularPipeline<>(entityVertex).in(contains).hasType(environment)
                        .iterator();
                if (envs.hasNext()) {
                    return envs.next();
                }
                return null;
            default:
                return null;
        }
    }

    static Vertex getFeedVertexOrNull(Vertex entityVertex) {
        Type type = Type.valueOf(getType(entityVertex));

        switch (type) {
            case resource:
            case metric:
                Iterator<Vertex> feeds = new HawkularPipeline<>(entityVertex).in(contains).hasType(feed).iterator();
                if (feeds.hasNext()) {
                    return feeds.next();
                }
                return null;
            default:
                return null;
        }
    }

    protected PathContext pathToHereWithSelect(Filter.Accumulator select) {
        Filter[][] selects = null;
        if (select != null) {
            selects = new Filter[1][];
            selects[0] = select.get();
        }
        return new PathContext(pathWith().get(), selects);
    }

    protected PathContext pathToHereWithSelects(Filter.Accumulator... selects) {
        Filter[][] sa = new Filter[selects.length][];
        for (int i = 0; i < selects.length; ++i) {
            sa[i] = selects[i].get();
        }

        return new PathContext(pathWith().get(), sa);
    }

    protected static void updateProperties(Element e, Map<String, Object> properties, String[] disallowedProperties) {
        Set<String> disallowed = new HashSet<>(Arrays.asList(disallowedProperties));

        //remove all non-mapped properties, that are not in the update
        String[] toRemove = e.getPropertyKeys().stream()
                .filter((p) -> !disallowed.contains(p) && !properties.containsKey(p)).toArray(String[]::new);

        for(String p : toRemove) {
            e.removeProperty(p);
        }

        //update and add new the properties
        properties.forEach((p, v) -> {
            if (!disallowed.contains(p)) {
                e.setProperty(p, v);
            }
        });
    }

    protected static void checkProperties(Map<String, Object> properties, String[] disallowedProperties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        HashSet<String> disallowed = new HashSet<>(properties.keySet());
        disallowed.retainAll(Arrays.asList(disallowedProperties));

        if (!disallowed.isEmpty()) {
            throw new IllegalArgumentException("The following properties are reserved for this type of entity: "
                    + Arrays.asList(disallowedProperties));
        }
    }
}
