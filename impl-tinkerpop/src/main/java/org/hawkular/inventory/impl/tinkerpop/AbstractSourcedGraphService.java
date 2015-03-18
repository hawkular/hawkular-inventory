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
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractSourcedGraphService<Single, Multiple, E extends Entity, Blueprint>
        extends AbstractGraphService {

    protected final Class<E> entityClass;
    protected final PathContext pathContext;

    AbstractSourcedGraphService(InventoryContext context, Class<E> entityClass, PathContext pathContext) {
        super(context, pathContext.path);
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

        Vertex v = context.getGraph().addVertex(id);
        v.setProperty(Constants.Property.type.name(), Constants.Type.of(entityClass).name());
        v.setProperty(Constants.Property.uid.name(), id);

        Filter[] path = initNewEntity(v, blueprint);

        context.getGraph().commit();

        return createSingleBrowser(FilterApplicator.fromPath(path).get());
    }

    public final void update(E entity) {
        Constants.Type type = Constants.Type.of(entity);
        List<String> mappedProperties = Arrays.asList(type.getMappedProperties());

        entity.getProperties().keySet().forEach(k -> {
            if (mappedProperties.contains(k)) {
                throw new IllegalArgumentException("Property '" + k + "' is reserved. Cannot set it to a custom value");
            }
        });

        Vertex vertex = convert(entity);
        if (vertex == null) {
            throw new EntityNotFoundException(entity.getClass(), FilterApplicator.filters(pathContext.path));
        }

        Set<String> toRemove = vertex.getPropertyKeys();
        toRemove.removeAll(entity.getProperties().keySet());

        toRemove.forEach(vertex::removeProperty);
        entity.getProperties().forEach(vertex::setProperty);

        updateExplicitProperties(entity, vertex);

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
     * @param entity the entity being updated
     * @param vertex the corresponding vertex
     */
    protected void updateExplicitProperties(E entity, Vertex vertex) {

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

        edges.forEach(context.getGraph()::removeEdge);
    }

    protected abstract Single createSingleBrowser(FilterApplicator... path);

    protected abstract Multiple createMultiBrowser(FilterApplicator... path);

    protected abstract String getProposedId(Blueprint b);

    protected abstract Filter[] initNewEntity(Vertex newEntity, Blueprint blueprint);
}
