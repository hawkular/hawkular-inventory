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
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Jirka Kremser
 * @since 1.0
 */
final class RelationshipBrowser<E extends Entity> extends AbstractBrowser<E> {

    private RelationshipBrowser(InventoryContext iContext, Class<E> sourceClass,
                                FilterApplicator... path) {
        super(iContext, sourceClass, path);
    }

    public static <T extends Entity> Relationships.Single single(InventoryContext iContext, Class<T>
            sourceClass, Relationships.Direction direction, FilterApplicator[] path, RelationFilter[] filters) {

        final Filter goToEdge = new JumpInOutFilter(direction, false);
        RelationshipBrowser<T> b = new RelationshipBrowser<>(iContext, sourceClass, AbstractGraphService.pathWith
                (path, goToEdge).andFilter(filters).get());
        return new Relationships.Single() {

            @Override
            public Relationship entity() {
                HawkularPipeline<?, Edge> edges = b.source().cast(Edge.class);
                if (!edges.hasNext()) {
                    throw new RelationNotFoundException(sourceClass, FilterApplicator.filters(b.path));
                }
                Edge edge = edges.next();

                Relationship relationship = new Relationship(edge.getId().toString(), edge.getLabel(), convert(edge
                         .getVertex(Direction.OUT)), convert(edge.getVertex(Direction.IN)));
                Map<String, Object> properties = edge.getPropertyKeys().stream().collect(Collectors.toMap(Function
                        .<String>identity(), key -> edge.<Object>getProperty(key)));
                relationship.getProperties().putAll(properties);
                return relationship;
            }
        };
    }

    public static <T extends Entity> Relationships.Multiple multiple(InventoryContext iContext, Class<T>
            sourceClass, Relationships.Direction direction, FilterApplicator[] path, RelationFilter[] filters) {

        final Filter goToEdge = new JumpInOutFilter(direction, false);
        final Filter goFromEdge = new JumpInOutFilter(direction, true);
        RelationshipBrowser<T> b = new RelationshipBrowser<>(iContext, sourceClass, AbstractGraphService.pathWith
                (path, goToEdge).andFilter(filters).get());

        return new Relationships.Multiple() {
            @Override
            public Set<Relationship> entities() {
                HawkularPipeline<?, Edge> edges = b.source().cast(Edge.class);

                Stream<Relationship> relationshipStream = StreamSupport
                        .stream(edges.spliterator(), false)
                        .map(edge -> {
                            Relationship relationship = new Relationship(getUid(edge), edge.getLabel(),
                                    convert(edge.getVertex(Direction.OUT)), convert(edge.getVertex(Direction.IN)));
                            // copy the properties
                            Map<String, Object> properties = edge.getPropertyKeys().stream()
                                    .collect(Collectors.toMap(Function.<String>identity(), edge::<Object>getProperty));

                            relationship.getProperties().putAll(properties);
                            return relationship;
                        });

                return relationshipStream.collect(Collectors.toSet());
            }

            @Override
            public Tenants.Read tenants() {
                Filter.Accumulator acc = Filter.by(goFromEdge, With.type(Tenant.class));
                return new TenantsService(b.context, b.pathToHereWithSelect(acc));
            }

            @Override
            public Environments.Read environments() {
                Filter.Accumulator acc = Filter.by(goFromEdge, With.type(Environment.class));
                return new EnvironmentsService(b.context, b.pathToHereWithSelect(acc));
            }

            @Override
            public Feeds.Read feeds() {
                Filter.Accumulator acc = Filter.by(goFromEdge, With.type(Feed.class));
                return new FeedsService(b.context, b.pathToHereWithSelect(acc));
            }

            @Override
            public MetricTypes.Read metricTypes() {
                Filter.Accumulator acc = Filter.by(goFromEdge, With.type(MetricType.class));
                return new MetricTypesService(b.context, b.pathToHereWithSelect(acc));
            }

            @Override
            public Metrics.Read metrics() {
                Filter.Accumulator acc = Filter.by(goFromEdge, With.type(Metric.class));
                return new MetricsService(b.context, b.pathToHereWithSelect(acc));
            }

            @Override
            public Resources.Read resources() {
                Filter.Accumulator acc = Filter.by(goFromEdge, With.type(Resource.class));
                return new ResourcesService(b.context, b.pathToHereWithSelect(acc));
            }

            @Override
            public ResourceTypes.Read resourceTypes() {
                Filter.Accumulator acc = Filter.by(goFromEdge, With.type(ResourceType.class));
                return new ResourceTypesService(b.context, b.pathToHereWithSelect(acc));
            }
        };
    }

    // filter used internally by the impl for jumping from a vertex to an edge or back
    static class JumpInOutFilter extends Filter {
        private final Relationships.Direction direction;
        private final boolean fromEdge;

        JumpInOutFilter(Relationships.Direction direction, boolean fromEdge) {
            this.direction = direction;
            this.fromEdge = fromEdge;
        }

        public Relationships.Direction getDirection() {
            return direction;
        }

        public boolean isFromEdge() {
            return fromEdge;
        }

        @Override
        public String toString() {
            return "Jump[" + (fromEdge ? "from " : "to ") + direction.name() + " edges]";
        }
    }
}
