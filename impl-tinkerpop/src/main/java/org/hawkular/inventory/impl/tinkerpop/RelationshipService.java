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
import org.hawkular.inventory.api.model.Relationship;

import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author Lukas Krejci
 * @author Jiri Kremser
 * @since 1.0
 */
final class RelationshipService implements Relationships.ReadWrite, Relationships.Read {
    private final TransactionalGraph graph;
    private final Vertex startVertex;


    RelationshipService(TransactionalGraph graph, Vertex startVertex) {
        this.graph = graph;
        this.startVertex = startVertex;
    }

    @Override
    public Relationships.Single get(String id) {
        // todo: refactor, the code is currently too wet, but want's to be DRY
        Relationships.Single single = new Relationships.Single() {

            @Override
            public Relationship entity() {
                Iterator<Edge> iterator = startVertex.getEdges(Direction.BOTH, id).iterator();
                // todo: getEdges returns edges by their name, not by their ids... in the current graph impl the ids are
                // set to null. Proper filtering by id would probably require to iterate over all edges and check the
                // id, or use the gremlin/hawkular pipe with st. like .E("id", id)
                if (!iterator.hasNext()) return null;
                Edge edge = iterator.next();
                return new Relationship(AbstractGraphService.getUid(edge), edge.getLabel(), AbstractGraphService
                        .convert(edge.getVertex(Direction.OUT)), AbstractGraphService
                        .convert(edge.getVertex(Direction.IN)));
            }

            @Override
            public Tenants.ReadRelate tenants() {
                return null;
            }

            @Override
            public Environments.ReadRelate environments() {
                return null;
            }

            @Override
            public Feeds.ReadRelate feeds() {
                return null;
            }

            @Override
            public MetricTypes.ReadRelate metricTypes() {
                return null;
            }

            @Override
            public Metrics.ReadRelate metrics() {
                return null;
            }

            @Override
            public Resources.ReadRelate resources() {
                return null;
            }

            @Override
            public ResourceTypes.ReadRelate resourceTypes() {
                return null;
            }
        };
        return single;
    }

    @Override
    public Relationships.Multiple getAll(Filter... filters) {
        //TODO implement
        return null;
    }

    @Override
    public Relationships.Single create(Relationship.Blueprint blueprint) {
        //TODO implement
        return null;
    }

    @Override
    public void update(Relationship entity) {
        //TODO implement

    }

    @Override
    public void delete(String id) {
        //TODO implement

    }

    @Override
    public Relationships.Multiple named(String name) {
        Relationships.Multiple multiple = new Relationships.Multiple() {

            @Override
            public Set<Relationship> entities() {
                Spliterator<Edge> spliterator = startVertex.getEdges(Direction.BOTH, name).spliterator();
                return StreamSupport.stream(spliterator, false).map(edge ->
                        new Relationship(AbstractGraphService.getUid(edge), edge.getLabel(), AbstractGraphService
                        .convert(edge.getVertex(Direction.OUT)), AbstractGraphService
                        .convert(edge.getVertex(Direction.IN))))
                        .collect(Collectors.toSet());
            }

            @Override
            public Tenants.Read tenants() {
                return null;
            }

            @Override
            public Environments.Read environments() {
                return null;
            }

            @Override
            public Feeds.Read feeds() {
                return null;
            }

            @Override
            public MetricTypes.Read metricTypes() {
                return null;
            }

            @Override
            public Metrics.Read metrics() {
                return null;
            }

            @Override
            public Resources.Read resources() {
                return null;
            }

            @Override
            public ResourceTypes.Read resourceTypes() {
                return null;
            }
        };
        return multiple;
    }
}
