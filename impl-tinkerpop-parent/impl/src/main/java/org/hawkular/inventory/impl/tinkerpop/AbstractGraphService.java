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
import org.hawkular.inventory.api.ResultFilter;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
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
 * Abstract base class providing the basic context and utility methods needed to translate the inventory traversals into
 * gremlin queries and the conversion of the model entities into vertices and edges and back.
 *
 * <p>An instance is initialized to operate on a particular position in the inventory traversal, represented
 * by the {@link #sourcePaths} field.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
abstract class AbstractGraphService {
    protected final InventoryContext context;

    /**
     * Represents the position in the inventory traversal this instance is operating on. The position is actually not
     * a single "point" in the traversal but a set of them (represented by the paths from the root to all the leaves
     * in the tree). When extending this position, all leaves are modified in the same way using
     * {@link org.hawkular.inventory.impl.tinkerpop.FilterApplicator.SymmetricTreeExtender}.
     */
    protected final FilterApplicator.Tree sourcePaths;

    AbstractGraphService(InventoryContext context, FilterApplicator.Tree sourcePaths) {
        this.context = context;
        this.sourcePaths = sourcePaths;
    }

    /**
     * Convenience overload for {@link #source(FilterApplicator.Tree)}, calling it with a {@code null}.
     */
    protected HawkularPipeline<?, Vertex> source() {
        return source(null);
    }

    /**
     * Creates a query that will resolve all elements in the {@link #sourcePaths} and then will filter the result
     * using the provided {@code filters}.
     *
     * @param filters the filters to apply to the elements on the {@link #sourcePaths} or null if no filtering required
     * @return a new instance of a Gremlin query corresponding to the traversal
     */
    protected HawkularPipeline<?, Vertex> source(FilterApplicator.Tree filters) {
        HawkularPipeline<Object, Vertex> ret = new HawkularPipeline<>(context.getGraph()).V();

        FilterApplicator.applyAll(sourcePaths, ret);

        FilterApplicator.applyAll(filters, ret);

        return ret;
    }

    /**
     * Converts the provided filters into 1 element 2-dimensional array and calls {@link #pathWith(Filter[][])}.
     *
     * @param filters the filters to extend the path with
     * @return the tree extender that can be used to further modify the path tree
     * @see #pathWith(Filter[][])
     */
    protected FilterApplicator.SymmetricTreeExtender pathWith(Filter... filters) {
        Filter[][] fs = new Filter[1][];
        fs[0] = filters;
        return pathWith(sourcePaths, fs);
    }

    /**
     * Takes the {@link #sourcePaths} and creates a new tree extender from it, extending each of the leaves of the tree
     * with the provided list of filters (each filter creates a new branch in the tree).
     *
     * @param filters the set of filters to extend the path with
     * @return the tree extender that can be used to further modify the path tree
     */
    protected FilterApplicator.SymmetricTreeExtender pathWith(Filter[][] filters) {
        return pathWith(sourcePaths, filters);
    }

    /**
     * Takes the {@code sourcePaths} and creates a new tree extender from it, extending each of the leaves of the tree
     * with the provided list of filters (each filter creates a new branch in the tree).
     *
     * @param sourcePaths the path tree to extend
     * @param filters     the set of filters to extend the path with
     * @return the tree extender that can be used to further modify the path tree
     */
    public static FilterApplicator.SymmetricTreeExtender pathWith(FilterApplicator.Tree sourcePaths,
            Filter[][] filters) {
        return FilterApplicator.from(sourcePaths).and(FilterApplicator.Type.PATH, filters);
    }

    /**
     * Converts the provided {@code filters} into 1-element 2-dimensional array and calls
     * {@link #pathWith(FilterApplicator.Tree, Filter[][])}.
     *
     * @param sourcePaths the path tree to extend
     * @param filters     the filters to apply
     * @return the tree extender that can be used to further modify the path tree
     */
    public static FilterApplicator.SymmetricTreeExtender pathWith(FilterApplicator.Tree sourcePaths,
            Filter... filters) {
        Filter[][] fs = new Filter[1][];
        fs[0] = filters;
        return FilterApplicator.from(sourcePaths).and(FilterApplicator.Type.PATH, fs);
    }

    protected boolean isApplicable(AbstractElement<?, ?> result) {
        ResultFilter filter = context.getResultFilter();
        return filter == null ? true : filter.isApplicable(result);
    }

    /**
     * Gets the value of the property from the vertex
     */
    static String getProperty(Vertex v, Constants.Property property) {
        return v.getProperty(property.name());
    }

    /**
     * Gets the user-assigned entity ID from the vertex.
     */
    static String getEid(Vertex v) {
        return getProperty(v, Constants.Property.__eid);
    }

    /**
     * Gets the generated relationship ID from the edge.
     */
    static String getEid(Edge e) {
        return e.getProperty(Constants.Property.__eid.name());
    }

    /**
     * Gets the type of the entity that the provided vertex represents.
     */
    static String getType(Vertex v) {
        return getProperty(v, Constants.Property.__type);
    }

    /**
     * Adds an edge with the provided label from the provided source to the provided target.
     * Makes sure that the edge ID is properly stored (some of the tinkerpop impls cannot query by edge id, which we
     * need, and so we have to work around that limitation by storing the edge's unique ID as an additional property
     * on the edge).
     *
     * <p>Note that this does NOT commit the changes to the graph!
     *
     * @param source the source vertex
     * @param label  the label of the edge
     * @param target the target of the edge
     * @return the created edge
     */
    protected Edge addEdge(Vertex source, String label, Vertex target) {
        Edge e = source.addEdge(label, target);
        e.setProperty(Constants.Property.__eid.name(), e.getId());
        return e;
    }

    /**
     * Finds the provided entity in the graph and returns the pre-existing vertex representing it.
     *
     * @param e the entity to convert
     * @return the vertex representing the entity
     */
    protected Vertex convert(Entity<?, ?> e) {
        HawkularPipeline<Object, Vertex> ret = new HawkularPipeline<>(context.getGraph())
                .V();
        HawkularPipeline<?, ? extends Element> vs =
                e.accept(new ElementVisitor.Simple<HawkularPipeline<?, ? extends Element>, Void>() {

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

    /**
     * Converts the vertex into an entity with all fields and properties initialized according to the data from the
     * vertex.
     *
     * @param v the vertex to convert
     * @return the entity corresponding to the vertex
     */
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
                        feedVertex == null ? null : getEid(feedVertex), getEid(v), md);
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

        return e.accept(new ElementVisitor.Simple<Entity<?, ?>, Void>() {
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

    /**
     * Returns the vertex of the tenant of the entity represented by the provided vertex or null if not applicable.
     */
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

    /**
     * Returns a new path context with the current {@link #sourcePaths} extended by the provided "select" filter.
     *
     * @param select the select to advance the path further
     * @return a new path context instance
     */
    protected PathContext pathToHereWithSelect(Filter.Accumulator select) {
        Filter[][] selects = null;
        if (select != null) {
            selects = new Filter[1][];
            selects[0] = select.get();
        }
        return new PathContext(pathWith().get(), selects);
    }

    /**
     * Return a new path context with the current {@link #sourcePaths} extended by all the provided select filters.
     * This essentially creates branches in the traversal.
     *
     * @param selects the selects to branch the source path with
     * @return a new path context instance
     */
    protected PathContext pathToHereWithSelects(Filter.Accumulator... selects) {
        Filter[][] sa = new Filter[selects.length][];
        for (int i = 0; i < selects.length; ++i) {
            sa[i] = selects[i].get();
        }

        return new PathContext(pathWith().get(), sa);
    }

    /**
     * Updates the properties of the element, disregarding any changes of the disallowed properties
     *
     * <p> The list of the disallowed properties will usually come from {@link Type#getMappedProperties()}.
     *
     * @param e                    the element to update properties of
     * @param properties           the properties to update
     * @param disallowedProperties the list of properties that are not allowed to change.
     */
    protected static void updateProperties(Element e, Map<String, Object> properties, String[] disallowedProperties) {
        Set<String> disallowed = new HashSet<>(Arrays.asList(disallowedProperties));

        //remove all non-mapped properties, that are not in the update
        String[] toRemove = e.getPropertyKeys().stream()
                .filter((p) -> !disallowed.contains(p) && !properties.containsKey(p)).toArray(String[]::new);

        for (String p : toRemove) {
            e.removeProperty(p);
        }

        //update and add new the properties
        properties.forEach((p, v) -> {
            if (!disallowed.contains(p)) {
                e.setProperty(p, v);
            }
        });
    }

    /**
     * If the properties map contains a key from the disallowed properties, throw an exception.
     *
     * @param properties           the properties to check
     * @param disallowedProperties the list of property names that cannot appear in the provided map
     * @throws IllegalArgumentException if the map contains one or more disallowed keys
     */
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
