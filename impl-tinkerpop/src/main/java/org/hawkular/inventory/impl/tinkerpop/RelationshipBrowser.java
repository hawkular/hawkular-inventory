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
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Jirka Kremser
 * @since 1.0
 */
final class RelationshipBrowser<E extends Entity> extends AbstractBrowser<E> {

    private RelationshipBrowser(Class<E> sourceClass, TransactionalGraph graph, FilterApplicator... path) {
        super(graph, sourceClass, path);
    }

    public static Relationships.Single single(String id, HawkularPipeline<?, Vertex> source, Class<? extends Entity>
            sourceClass, TransactionalGraph graph, FilterApplicator... path) {
        if (null == id) {
            throw new NullPointerException("unable to create Relationships.Single without the edge id.");
        }
        RelationshipBrowser b = new RelationshipBrowser(sourceClass, graph, path);

        return new Relationships.Single() {

            @Override
            public Relationship entity() {
                HawkularPipeline<?, Edge> edges = b.source().outE().has("id", id).cast(Edge.class);
                if (!edges.hasNext()) {
                    return null;
                }
                Edge edge = edges.next();
                return new Relationship(edge.getId().toString(), edge.getLabel(), convert(edge.getVertex(Direction
                        .OUT)), convert(edge.getVertex(Direction.IN)));
            }

            @Override
            public Tenants.ReadRelate tenants() {
                return b.tenants(EdgeFilter.ID, id);
            }

            @Override
            public Environments.ReadRelate environments() {
                return (Environments.ReadRelate)b.<EnvironmentsService>getService(EdgeFilter.ID, id, Environment
                        .class, EnvironmentsService.class);
            }

            @Override
            public Feeds.ReadRelate feeds() {
                return (Feeds.ReadRelate)b.<FeedsService>getService(EdgeFilter.ID, id, Feed.class, FeedsService.class);
            }

            @Override
            public MetricTypes.ReadRelate metricTypes() {
                return (MetricTypes.ReadRelate)b.<MetricTypesService>getService(EdgeFilter.ID, id, MetricType.class,
                        MetricTypesService.class);
            }

            @Override
            public Metrics.ReadRelate metrics() {
                return (Metrics.ReadRelate)b.<MetricsService>getService(EdgeFilter.ID, id, Metrics.class,
                        MetricsService.class);
            }

            @Override
            public Resources.ReadRelate resources() {
                return (Resources.ReadRelate)b.<ResourcesService>getService(EdgeFilter.ID, id, Resource.class,
                        ResourcesService.class);
            }

            @Override
            public ResourceTypes.ReadRelate resourceTypes() {
                return (ResourceTypes.ReadRelate)b.<TypesService>getService(EdgeFilter.ID, id, ResourceType.class,
                        TypesService.class);
            }
        };
    }

    public static Relationships.Multiple multiple(String named, HawkularPipeline<?, Vertex> source, Class<? extends
            Entity> sourceClass, TransactionalGraph graph, FilterApplicator... path) {

        RelationshipBrowser b = new RelationshipBrowser(sourceClass, graph, path);

        return new Relationships.Multiple() {
            @Override
            public Set<Relationship> entities() {
                // TODO process filters

                System.out.println("eeeee");
                HawkularPipeline<?, Edge> edges = null == named ? b.source().bothE() : b.source().bothE(named);
                Stream<Relationship> relationshipStream = StreamSupport
                        .stream(edges.spliterator(), false)
                        .map(edge -> new Relationship(edge.getId().toString(), edge.getLabel(),
                                convert(edge.getVertex(Direction.OUT)), convert(edge.getVertex(Direction.IN))));
                return relationshipStream.collect(Collectors.toSet());
            }

            @Override
            public Tenants.Read tenants() {
                // TODO implement
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Environments.Read environments() {
                return (Environments.Read)b.<EnvironmentsService>getService(EdgeFilter.NAMED, named, Environment.class,
                        EnvironmentsService.class);
            }

            @Override
            public Feeds.Read feeds() {
                return (Feeds.Read)b.<FeedsService>getService(EdgeFilter.NAMED, named, Feed.class, FeedsService.class);
            }

            @Override
            public MetricTypes.Read metricTypes() {
                return (MetricTypes.Read)b.<MetricTypesService>getService(EdgeFilter.NAMED, named, MetricType.class,
                        MetricTypesService.class);
            }

            @Override
            public Metrics.Read metrics() {
                return (Metrics.Read)b.<MetricsService>getService(EdgeFilter.NAMED, named, Metrics.class,
                        MetricsService.class);
            }

            @Override
            public Resources.Read resources() {
                return (Resources.Read)b.<ResourcesService>getService(EdgeFilter.NAMED, named, Resource.class,
                        ResourcesService.class);
            }

            @Override
            public ResourceTypes.Read resourceTypes() {
                return (ResourceTypes.Read)b.<TypesService>getService(EdgeFilter.NAMED, named, ResourceType.class,
                        TypesService.class);
            }
        };
    }


    private <S extends AbstractSourcedGraphService> S getService(EdgeFilter filter, String value, Class<? extends
            Entity> clazz1, Class<S> clazz2) {
        Filter.Accumulator acc = Filter.by(EdgeFilter.NAMED == filter ? Related.by(value) : Related.byEdgeWithId
                (value), With.type(clazz1));
        try {
            Constructor<S> constructor = clazz2.getConstructor(TransactionalGraph.class, PathContext.class);
            return constructor.newInstance(graph, pathToHereWithSelect(acc));
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException
                e) {
            throw new IllegalStateException("Unable to create new instance of " + clazz2.getCanonicalName(), e);
        }
    }

    public TenantsService tenants(EdgeFilter filter, String value) {
        //return new TenantsService(graph, pathToHereWithSelect(Filter.by(Related.by(id),
        //        With.type(Tenant.class))));
        // TODO implement
        return null;
    }

    private enum EdgeFilter {
        ID, NAMED;
    }
}