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
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractSourcedGraphService<Single, Multiple, E extends Entity<Blueprint, Update>,
        Blueprint extends Entity.Blueprint, Update extends Entity.Update> extends AbstractGraphService {

    protected final Class<E> entityClass;
    protected final PathContext pathContext;

    AbstractSourcedGraphService(InventoryContext context, Class<E> entityClass, PathContext pathContext) {
        super(context, pathContext.path);
        this.entityClass = entityClass;
        this.pathContext = pathContext;
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

        Iterable<Vertex> check = source(FilterApplicator.fromPath(selectCandidates()).andFilter(With.ids(id)).get());

        if (check.iterator().hasNext()) {
            throw new IllegalArgumentException("Entity with type '" + entityClass.getSimpleName() + " ' and id '" + id
                    + "' already exists.");
        }

        checkProperties(blueprint.getProperties());

        Vertex v = context.getGraph().addVertex(null);
        v.setProperty(Constants.Property.type.name(), Constants.Type.of(entityClass).name());
        v.setProperty(Constants.Property.uid.name(), id);

        if (blueprint.getProperties() != null) {
            for (Map.Entry<String, Object> e : blueprint.getProperties().entrySet()) {
                v.setProperty(e.getKey(), e.getValue());
            }
        }

        try {
            Filter[] path = initNewEntity(v, blueprint);

            context.getGraph().commit();

            return createSingleBrowser(FilterApplicator.fromPath(path).get());
        } catch (Throwable e) {
            context.getGraph().rollback();
            throw e;
        }
    }

    public void update(String id, Update update) {
        checkProperties(update.getProperties());

        Iterator<Vertex> it = source(FilterApplicator.fromPath(selectCandidates()).andPath(With.id(id)).get());

        if (!it.hasNext()) {
            throw new EntityNotFoundException(entityClass, FilterApplicator.filters(pathContext.path));
        }

        Vertex vertex = it.next();

        updateProperties(vertex, update.getProperties(), Constants.Type.of(entityClass).getMappedProperties());
        updateExplicitProperties(update, vertex);

        context.getGraph().commit();
    }

    public void delete(String id) {
        Iterator<Vertex> vs = source(FilterApplicator.fromPath(selectCandidates()).andPath(With.id(id)).get());

        if (!vs.hasNext()) {
            FilterApplicator[] fullPath = FilterApplicator.from(pathContext.path).andPath(selectCandidates())
                    .andPath(With.id(id)).get();

            throw new EntityNotFoundException(entityClass, FilterApplicator.filters(fullPath));
        }

        Vertex v = vs.next();

        Set<Vertex> verticesToBeDeletedThatDefineSomething = new HashSet<>();

        try {
            new HawkularPipeline<>(v).as("start").out(contains).loop("start", (x) -> true, (x) -> true).toList()
                .forEach(c -> {
                    if (c.getEdges(Direction.OUT, defines.name()).iterator().hasNext()) {
                        verticesToBeDeletedThatDefineSomething.add(c);
                    } else {
                        c.remove();
                    }
                });

            if (v.getEdges(Direction.OUT, defines.name()).iterator().hasNext()) {
                verticesToBeDeletedThatDefineSomething.add(v);
            } else {
                v.remove();
            }

            for (Vertex d : verticesToBeDeletedThatDefineSomething) {
                if (d.getEdges(Direction.OUT, defines.name()).iterator().hasNext()) {
                    context.getGraph().rollback();

                    //we avoid the convert() function here because it assumes the containing entities of the passed in
                    //entity exist. This might not be true during the delete because the transitive closure "walks" the
                    //entities from the "top" down the containment chain and the entities are immediately deleted.
                    String rootEntity = "Entity[id=" + getUid(v) + ", type=" + getType(v) + "]";
                    String definingEntity = "Entity[id=" + getUid(d) + ", type=" + getType(d) + "]";

                    throw new IllegalArgumentException("Could not delete entity " + rootEntity + ". The entity " +
                            definingEntity + ", which it (indirectly) contains, acts as a definition for some" +
                            "entities that are not deleted along with it, which would leave them without a " +
                            "definition. This is illegal.");
                } else {
                    d.remove();
                }
            }

            context.getGraph().commit();
        } catch (Exception e) {
            context.getGraph().rollback();
            throw e;
        }
    }

    /**
     * Update vertex properties that are expressed as actual properties on the entity classes.
     *
     * <p/> This method must not do anything with the {@link org.hawkular.inventory.api.model.Entity#getProperties()}
     * which are handled separately in a generic way.
     *
     * <p/> This method must not commit the graph.
     *
     * @param update the updates to the entity
     * @param vertex the corresponding vertex
     */
    protected void updateExplicitProperties(Update update, Vertex vertex) {

    }

    protected Relationship addAssociation(Constants.Type typeInSource, Relationships.WellKnown rel,
                                          Iterable<Vertex> others) {
        //noinspection LoopStatementThatDoesntLoop
        for (Vertex v : source().hasType(typeInSource)) {
            //noinspection LoopStatementThatDoesntLoop
            for (Vertex o : others) {
                for (Edge e : v.getEdges(Direction.OUT, rel.name())) {
                    if (e.getVertex(Direction.IN).equals(o)) {
                        throw new RelationAlreadyExistsException(rel.name(), FilterApplicator.filters(path));
                    }
                }

                Edge e = addEdge(v, rel.name(), o);

                return new Relationship(getUid(e), e.getLabel(), convert(e.getVertex(Direction.OUT)),
                        convert(e.getVertex(Direction.IN)));
            }

            throw new EntityNotFoundException(entityClass,
                    FilterApplicator.filters(FilterApplicator.from(path).andPath(Related.by(rel)).get()));
        }

        throw new EntityNotFoundException(typeInSource.getEntityType(), FilterApplicator.filters(path));
    }

    protected Relationship findAssociation(String targetId, String label) {
        Vertex source = source().next();

        for(Edge e : source.getEdges(Direction.OUT, label)) {
            Vertex target = e.getVertex(Direction.IN);
            if (getUid(target).equals(targetId)) {
                return new Relationship(getUid(e), label, convert(source), convert(target));
            }
        }

        throw new RelationNotFoundException(label, FilterApplicator.filters(path));

    }

    protected Relationship removeAssociation(Constants.Type typeInSource, Relationships.WellKnown rel,
                                     String targetUid) {

        Constants.Type myType = Constants.Type.of(entityClass);

        Iterable<Edge> edges = source().hasType(typeInSource).outE(rel.name())
                .and(new HawkularPipeline<Edge, Object>().inV().hasType(myType).hasUid(targetUid));

        Iterator<Edge> it = edges.iterator();

        if (!it.hasNext()) {
            throw new RelationNotFoundException(typeInSource.getEntityType(), rel.name(),
                    FilterApplicator.filters(path), "Relationship does not exist.", null);
        }

        Edge edge = it.next();
        Relationship ret = new Relationship(getUid(edge), edge.getLabel(), convert(edge.getVertex(Direction.OUT)),
                convert(edge.getVertex(Direction.IN)));
        context.getGraph().removeEdge(edge);
        return ret;
    }

    protected abstract Single createSingleBrowser(FilterApplicator... path);

    protected abstract Multiple createMultiBrowser(FilterApplicator... path);

    protected abstract String getProposedId(Blueprint b);

    protected abstract Filter[] initNewEntity(Vertex newEntity, Blueprint blueprint);

    private void checkProperties(Map<String, Object> properties) {
        Constants.Type type = Constants.Type.of(entityClass);
        checkProperties(properties, type.getMappedProperties());
    }
}
