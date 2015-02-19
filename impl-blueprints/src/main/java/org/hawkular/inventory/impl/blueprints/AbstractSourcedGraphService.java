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

package org.hawkular.inventory.impl.blueprints;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractSourcedGraphService<Browser, E extends Entity, Blueprint> extends AbstractGraphService {
    protected final Class<E> entityClass;
    private final PathContext pathContext;

    AbstractSourcedGraphService(TransactionalGraph graph, Class<E> entityClass, PathContext pathContext) {
        super(graph, pathContext.path);
        this.entityClass = entityClass;
        this.pathContext = pathContext;
    }

    protected PathContext pathToHereWithSelect(Filter.Accumulator select) {
        return new PathContext(filterBy().get(), select == null ? null : select.get());
    }

    protected final Filter[] selectCandidates() {
        return pathContext.candidatesFilter;
    }

    public Set<String> getAllIds(Filter... filters) {
        return getAll(filters).stream().map(Entity::getId).collect(Collectors.toSet());
    }

    public Set<E> getAll(Filter... filters) {
        HawkularPipeline<?, Vertex> q = source(selectCandidates());

        applyFilters(q, new FilterVisitor<>(), filters);

        HashSet<E> ret = new HashSet<>();
        q.forEach(v -> ret.add(entityClass.cast(convert(v))));

        return ret;
    }

    public Browser get(String id) {
        return createBrowser(filterBy(selectCandidates()).and(With.ids(id)).get());
    }

    public Browser create(Blueprint blueprint) {
        String id = getProposedId(blueprint);

        Iterable<Vertex> check = source(filterBy(selectCandidates()).and(With.ids(id)).get());

        if (check.iterator().hasNext()) {
            throw new IllegalArgumentException("Entity with type '" + entityClass.getSimpleName() + " ' and id '" + id
                    + "' already exists.");
        }

        Vertex v = graph.addVertex(id);
        v.setProperty(Constants.Property.type.name(), Constants.Type.of(entityClass).name());
        v.setProperty(Constants.Property.uid.name(), id);

        Filter[] path = initNewEntity(v, blueprint);

        graph.commit();

        return createBrowser(path);
    }

    protected void addRelationship(Constants.Type typeInSource, Relationships.WellKnown rel, Iterable<Vertex> others) {
        for (Vertex v : source().hasType(typeInSource)) {
            for (Vertex o : others) {
                v.addEdge(rel.name(), o);
            }
        }
    }

    protected void removeRelationship(Constants.Type typeInSource, Relationships.WellKnown rel,
                                      String targetUid) {

        Constants.Type myType = Constants.Type.of(entityClass);

        Iterable<Edge> edges = source().hasType(typeInSource).outE(rel.name())
                .and(new HawkularPipeline<Edge, Object>().inV().hasType(myType).hasUid(targetUid));

        edges.forEach(graph::removeEdge);
    }

    protected abstract Browser createBrowser(Filter... path);

    protected abstract String getProposedId(Blueprint b);

    protected abstract Filter[] initNewEntity(Vertex newEntity, Blueprint blueprint);
}
