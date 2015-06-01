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
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;

/**
 * An abstract base class for filtering services - a dual to {@link AbstractBrowser} which is a base class for browser
 * implementations.
 *
 * @param <Single> the browser interface for a single entity
 * @param <Multiple> the browser interface for multiple entities at once
 * @param <E> the type of the entity
 * @param <Blueprint> the blueprint type used for creating new entities
 * @param <Update> the update type used for updating existing entities
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
abstract class AbstractSourcedGraphService<Single, Multiple, E extends Entity<Blueprint, Update>,
        Blueprint extends Entity.Blueprint, Update extends Entity.Update> extends AbstractGraphService {

    protected final Class<E> entityClass;
    protected final PathContext pathContext;

    AbstractSourcedGraphService(InventoryContext context, Class<E> entityClass, PathContext pathContext) {
        super(context, pathContext.sourcePath);
        this.entityClass = entityClass;
        this.pathContext = pathContext;
    }

    protected final Filter[][] selectCandidates() {
        return pathContext.candidatesFilters;
    }

    /**
     * The implementation of the {@link org.hawkular.inventory.api.ResolvingToMultiple#getAll(Filter...)} method.
     *
     * <p>Even though this class doesn't implement that interface it provides a default implementation of the method for
     * the inheriting classes.
     *
     * @param filters the set of filters to limit the results with.
     * @return the browser interface for the found entities
     */
    @SuppressWarnings("UnusedDeclaration")
    public Multiple getAll(Filter... filters) {
        return createMultiBrowser(pathWith(selectCandidates()).andFilter(filters).get());
    }

    /**
     * The implementation of the {@link org.hawkular.inventory.api.ResolvingToSingle#get(String)} method.
     *
     * <p>Even though this class doesn't implement that interface it provides a default implementation of the method for
     * the inheriting classes.
     *
     * @param id the id of the entity to return browser of
     * @return the browser interface for the found entity
     */
    public Single get(String id) {
        return createSingleBrowser(pathWith(selectCandidates()).andPath(With.ids(id)).get());
    }

    /**
     * A default implementation of the {@link org.hawkular.inventory.api.WriteInterface#create(Entity.Blueprint)}
     * method.
     *
     * @param blueprint the blueprint to create the entity with
     * @return browser interface for the newly created entity
     */
    public Single create(Blueprint blueprint) {
        try {
            context.getInventoryLock().writeLock().lock();
            String id = getProposedId(blueprint);

            FilterApplicator.Tree checkPath = FilterApplicator.fromPath(selectCandidates()).andFilter(With.ids(id)).get();

            Iterable<Vertex> check = source(checkPath);

            if (check.iterator().hasNext()) {
                throw new EntityAlreadyExistsException(id, FilterApplicator.filters(checkPath));
            }

            checkProperties(blueprint.getProperties());

            Vertex v = context.getGraph().addVertex(null);
            v.setProperty(Constants.Property.__type.name(), Constants.Type.of(entityClass).name());
            v.setProperty(Constants.Property.__eid.name(), id);

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
        } finally {
            context.getInventoryLock().writeLock().unlock();
        }
    }

    /**
     * A default implementation of the {@link org.hawkular.inventory.api.WriteInterface#update(String, Object)} method.
     *
     * @param id     the id of the entity to update (must be amongst the {@link #selectCandidates()}).
     * @param update the object with the updates to the entity
     */
    public void update(String id, Update update) {
        checkProperties(update.getProperties());

        try {
            context.getInventoryLock().writeLock().lock();
            Iterator<Vertex> it = source(FilterApplicator.fromPath(selectCandidates()).andPath(With.id(id)).get());

            if (!it.hasNext()) {
                throw new EntityNotFoundException(entityClass, FilterApplicator.filters(pathContext.sourcePath));
            }

            Vertex vertex = it.next();

            updateProperties(vertex, update.getProperties(), Constants.Type.of(entityClass).getMappedProperties());
            updateExplicitProperties(update, vertex);

            context.getGraph().commit();
        } finally {
            context.getInventoryLock().writeLock().unlock();
        }
    }

    /**
     * Default implementation of the {@link org.hawkular.inventory.api.WriteInterface#delete(String)} method.
     *
     * @param id the id of the entity to delete (must be amongst the {@link #selectCandidates()}).
     */
    public void delete(String id) {
        try {
            context.getInventoryLock().writeLock().lock();

            Iterator<Vertex> vs = source(FilterApplicator.fromPath(selectCandidates()).andPath(With.id(id)).get());

            if (!vs.hasNext()) {
                FilterApplicator.Tree fullPath = FilterApplicator.from(pathContext.sourcePath)
                        .andPath(selectCandidates()).andPath(With.id(id)).get();

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

                        //we avoid the convert() function here because it assumes the containing entities of the
                        //passed in entity exist. This might not be true during the delete because the transitive
                        // closure "walks" the entities from the "top" down the containment chain and the entities
                        // are immediately deleted.
                        String rootEntity = "Entity[id=" + getEid(v) + ", type=" + getType(v) + "]";
                        String definingEntity = "Entity[id=" + getEid(d) + ", type=" + getType(d) + "]";

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
        } finally {
            context.getInventoryLock().writeLock().unlock();
        }
    }

    /**
     * Update vertex properties that are expressed as actual properties on the entity classes.
     *
     * <p> This method must not do anything with the {@link org.hawkular.inventory.api.model.Entity#getProperties()}
     * which are handled separately in a generic way.
     *
     * <p> This method must not commit the graph.
     *
     * @param update the updates to the entity
     * @param vertex the corresponding vertex
     */
    protected void updateExplicitProperties(Update update, Vertex vertex) {

    }

    /**
     * A helper method to add an association from one entity to another. This is for example used to add association
     * from resource types to metric types, etc.
     *
     * @param typeInSource the type of the entity to look for in the {@link #source()}.
     * @param rel          the type of the relationship representing the association
     * @param others       a 1 element iterable of the vertices to create the association to
     * @return the relationship representing the new association
     */
    protected Relationship addAssociation(Constants.Type typeInSource, Relationships.WellKnown rel,
                                          Iterable<Vertex> others) {
        try {
            context.getInventoryLock().writeLock().lock();
            //noinspection LoopStatementThatDoesntLoop
            for (Vertex v : source().hasType(typeInSource)) {
                //noinspection LoopStatementThatDoesntLoop
                for (Vertex o : others) {
                    for (Edge e : v.getEdges(Direction.OUT, rel.name())) {
                        if (e.getVertex(Direction.IN).equals(o)) {
                            throw new RelationAlreadyExistsException(rel.name(), FilterApplicator.filters(sourcePaths));
                        }
                    }

                    Edge e = addEdge(v, rel.name(), o);

                    return new Relationship(getEid(e), e.getLabel(), convert(e.getVertex(Direction.OUT)),
                                            convert(e.getVertex(Direction.IN)));
                }

                throw new EntityNotFoundException(entityClass,
                                                  FilterApplicator.filters(FilterApplicator.from(sourcePaths).andPath(Related.by(rel)).get()));
            }

            throw new EntityNotFoundException(typeInSource.getEntityType(), FilterApplicator.filters(sourcePaths));
        } finally {
            context.getInventoryLock().writeLock().unlock();
        }
    }

    /**
     * Tries to find the association labeled by the provided lable from the {@link #source()} to the entity with the
     * provided id.
     *
     * @param targetId the id of the entity being in association with the {@link #source()}.
     * @param label    the label of the association
     * @return the relationship representing the association
     */
    protected Relationship findAssociation(String targetId, String label) {
        try {
            context.getInventoryLock().readLock().lock();
            Vertex source = source().next();

            for (Edge e : source.getEdges(Direction.OUT, label)) {
                Vertex target = e.getVertex(Direction.IN);
                if (getEid(target).equals(targetId)) {
                    return new Relationship(getEid(e), label, convert(source), convert(target));
                }
            }

            throw new RelationNotFoundException(label, FilterApplicator.filters(sourcePaths));
        } finally {
            context.getInventoryLock().readLock().unlock();
        }
    }

    /**
     * Removes the association between entities of given type in the {@link #source()}.
     *
     * @param typeInSource the type of the entities in the {@link #source()}
     * @param rel the relationship type representing the association
     * @param targetUid the ID of the target of the association
     * @return the relationship representing the removed association
     */
    protected Relationship removeAssociation(Constants.Type typeInSource, Relationships.WellKnown rel,
                                     String targetUid) {
        try {
            context.getInventoryLock().writeLock().lock();
            Constants.Type myType = Constants.Type.of(entityClass);

            Iterable<Edge> edges = source().hasType(typeInSource).outE(rel.name())
                    .and(new HawkularPipeline<Edge, Object>().inV().hasType(myType).hasEid(targetUid));

            Iterator<Edge> it = edges.iterator();

            if (!it.hasNext()) {
                throw new RelationNotFoundException(typeInSource.getEntityType(), rel.name(),
                                                    FilterApplicator.filters(sourcePaths), "Relationship does not exist.", null);
            }

            Edge edge = it.next();
            Relationship ret = new Relationship(getEid(edge), edge.getLabel(), convert(edge.getVertex(Direction.OUT)),
                                                convert(edge.getVertex(Direction.IN)));
            context.getGraph().removeEdge(edge);
            return ret;
        } finally {
            context.getInventoryLock().writeLock().unlock();
        }
    }

    /**
     * To be implemented by subclasses, this method creates a browser interface instance for a single entity targeted
     * by the provided path.
     *
     * @param path the path to the single entity
     * @return a new instance of the single entity browser
     */
    protected abstract Single createSingleBrowser(FilterApplicator.Tree path);

    /**
     * To be implemented by subclasses, this method creates a browser interface instance for multiple entities targeted
     * by the provided path.
     *
     * @param path the path to the multiple entities
     * @return a new instance of the multiple entities browser
     */
    protected abstract Multiple createMultiBrowser(FilterApplicator.Tree path);

    /**
     * To be implemented by subclasses, this extracts the proposed ID from the blueprint.
     *
     * @param b the blueprint of the entity being created
     * @return the proposed ID of the new entity as defined in the blueprint
     */
    protected abstract String getProposedId(Blueprint b);

    /**
     * After a vertex for the given entity is created, this method is called to properly initialize it according to the
     * requirements of the type of the entity.
     *
     * <p>This method is called from {@link #create(Entity.Blueprint)}.
     *
     * @param newEntity the vertex of the new entity
     * @param blueprint the blueprint used to create the entity
     * @return the path to the new entity in the inventory. This doesn't have to extend the current {@link #sourcePaths}
     */
    protected abstract Filter[] initNewEntity(Vertex newEntity, Blueprint blueprint);

    /**
     * Checks properties that they don't contain any disallowed keys for the {@link #entityClass} this filter interface
     * accesses.
     * @param properties the properties
     */
    private void checkProperties(Map<String, Object> properties) {
        Constants.Type type = Constants.Type.of(entityClass);
        checkProperties(properties, type.getMappedProperties());
    }
}
