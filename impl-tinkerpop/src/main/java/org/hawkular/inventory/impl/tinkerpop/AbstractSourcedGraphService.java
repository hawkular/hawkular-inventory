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

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractSourcedGraphService<Single, Multiple, E extends Entity, Blueprint>
        extends AbstractGraphService {

    protected final Class<E> entityClass;
    protected final PathContext pathContext;

    AbstractSourcedGraphService(TransactionalGraph graph, Class<E> entityClass, PathContext pathContext) {
        super(graph, pathContext.path);
        this.entityClass = entityClass;
        this.pathContext = pathContext;
    }

    protected PathContext pathToHereWithSelect(Filter.Accumulator select) {
        return new PathContext(pathWith().get(), select == null ? null : select.get());
    }

    protected final Filter[] selectCandidates() {
        return pathContext.candidatesFilter;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Multiple getAll(Filter... filters) {
        return createMultiBrowser(pathWith(selectCandidates()).andFilter(filters).get());
    }

    public Single get(String id) {
        return createSingleBrowser(pathWith(selectCandidates()).andPath(With.ids(id)).get());
    }

    public Single create(Blueprint blueprint) {
        String id = getProposedId(blueprint);

        Iterable<Vertex> check = source(pathWith(selectCandidates()).andFilter(With.ids(id)).get());

        if (check.iterator().hasNext()) {
            throw new IllegalArgumentException("Entity with type '" + entityClass.getSimpleName() + " ' and id '" + id
                    + "' already exists.");
        }

        Vertex v = graph.addVertex(id);
        v.setProperty(Constants.Property.type.name(), Constants.Type.of(entityClass).name());
        v.setProperty(Constants.Property.uid.name(), id);

        Filter[] path = initNewEntity(v, blueprint);

        graph.commit();

        return createSingleBrowser(FilterApplicator.fromPath(path).get());
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

    protected abstract Single createSingleBrowser(FilterApplicator... path);

    protected abstract Multiple createMultiBrowser(FilterApplicator... path);

    protected abstract String getProposedId(Blueprint b);

    protected abstract Filter[] initNewEntity(Vertex newEntity, Blueprint blueprint);
}
