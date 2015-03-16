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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

        // TODO avoid loops and diamonds in contains closures...
        // diamond avoidance - vertex should have at most 1 incoming contains edge
        // loop avoidance - go "up" the contains path looking for self
        // note that these can actually only happen with custom relationships, we could also
        // just prohibit the creation of "contains" and other well-known relationships through
        // relationships().

        Set<Vertex> verticesToBeDeletedThatDefineSomething = new HashSet<>();

        try {
            transitiveClosureOver(v, Direction.OUT, Relationships.WellKnown.contains.name())
                    .forEach(c -> {
                        if (c.getEdges(Direction.OUT, Relationships.WellKnown.defines.name()).iterator().hasNext()) {
                            verticesToBeDeletedThatDefineSomething.add(c);
                        } else {
                            context.getGraph().removeVertex(c);
                        }
                    });

            for (Vertex d : verticesToBeDeletedThatDefineSomething) {
                if (d.getEdges(Direction.OUT, Relationships.WellKnown.defines.name()).iterator().hasNext()) {
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
                    context.getGraph().removeVertex(d);
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

    /**
     * Returns a stream of vertices that are connected with the startPoint vertex by edge with one of the provided
     * labels, recursively.
     *
     * <p/>The stream returns the vertices in a breadth-first-search manner with the startPoint vertex always being
     * the first to return.
     *
     * @param startPoint the start point to crawl from
     * @param direction the direction to follow
     * @param edgeLabels the labels to consider
     * @return a parallel stream of vertices
     */
    protected Stream<Vertex> transitiveClosureOver(Vertex startPoint, Direction direction, String... edgeLabels) {
        return StreamSupport.stream(new DFSBottomUpTransitiveClosureSpliterator(startPoint, direction, edgeLabels),
                false);
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

    /**
     * Assumes no loops or diamonds in the graph.
     *
     * Produces a spliterator that traverses the transitive closure over vertices connected with the provided labels in
     * provided direction. The vertices are traversed in a bottom-up manner, i.e. the leftmost leaf first.
     */
    private static class DFSBottomUpTransitiveClosureSpliterator implements Spliterator<Vertex> {
        private final Direction direction;
        private final String[] edgeLabels;
        private Deque<Vertex> branch;
        private final Deque<Iterator<Vertex>> siblings;

        private DFSBottomUpTransitiveClosureSpliterator(Vertex startingPoint, Direction direction,
                                                String[] edgeLabels) {
            this.direction = direction;
            this.edgeLabels = edgeLabels;
            this.branch = new ArrayDeque<>();
            this.siblings = new ArrayDeque<>();

            //after this method, the "branch" will contain the "left-most" branch in the
            //tree spanning from the starting point down to the left-most leaf.
            //the "siblings" will contain the siblings of each of the elements in the branch.

            //The branch is used in tryAdvance to exhaust the tree.

            Iterator<Vertex> sibs = Collections.<Vertex>emptySet().iterator();

            this.siblings.push(sibs);
            this.branch.push(startingPoint);

            pushChildren(startingPoint);
        }

        @Override
        public boolean tryAdvance(Consumer<? super Vertex> action) {
            if (branch.isEmpty()) {
                return false;
            }

            Vertex toProcess = branch.pop();

            if (siblings.peek().hasNext()) {
                //the current leaf has siblings
                Vertex sibling = siblings.peek().next();

                //replace the current leaf with its sibling
                branch.push(sibling);

                //and push any children of the sibling onto the stack.
                //next time we're called, we serve the children first.
                pushChildren(sibling);
            } else {
                //k, this was the last sibling... let's just shorten the pending queue
                //to match the branch queue in size.
                siblings.pop();
            }

            action.accept(toProcess);

            return true;
        }

        @Override
        public Spliterator<Vertex> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.NONNULL;
        }

        private void pushChildren(Vertex startingPoint) {
            do {
                Iterator<Vertex> children = startingPoint.getVertices(direction, edgeLabels).iterator();
                if (children.hasNext()) {
                    startingPoint = children.next();
                    branch.push(startingPoint);
                    siblings.push(children);
                } else {
                    startingPoint = null;
                }
            } while (startingPoint != null);
        }
    }
}
