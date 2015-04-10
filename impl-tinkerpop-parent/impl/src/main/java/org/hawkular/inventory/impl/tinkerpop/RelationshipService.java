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
import com.tinkerpop.blueprints.util.ElementHelper;
import com.tinkerpop.pipes.PipeFunction;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;

import java.util.Collections;
import java.util.Map;

import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

/**
 * @author Lukas Krejci
 * @author Jiri Kremser
 * @since 1.0
 */
final class RelationshipService<E extends Entity> extends AbstractSourcedGraphService<Relationships.Single,
        Relationships.Multiple, E, Entity.Blueprint> implements Relationships.ReadWrite, Relationships.Read {

    private final Relationships.Direction direction;

    RelationshipService(InventoryContext iContext, PathContext ctx, Class<E> sourceClass, Relationships.Direction
            direction) {
        super(iContext, sourceClass, ctx);
        this.direction = direction;
    }

    @Override
    public Relationships.Single get(String id) {
        return createSingleBrowser(RelationWith.id(id));
    }

    @Override
    public Relationships.Multiple getAll(RelationFilter... filters) {
        return createMultiBrowser(filters);
    }

    @Override
    protected Relationships.Single createSingleBrowser(FilterApplicator... path) {
        return createSingleBrowser((RelationFilter[]) null);
    }

    private Relationships.Single createSingleBrowser(RelationFilter... filters) {
        return RelationshipBrowser.single(context, entityClass, direction, pathWith(selectCandidates())
                .get(), filters);
    }

    @Override
    protected Relationships.Multiple createMultiBrowser(FilterApplicator... path) {
        return createMultiBrowser((RelationFilter[]) null);
    }

    private Relationships.Multiple createMultiBrowser(RelationFilter... filters) {
        return RelationshipBrowser.multiple(context, entityClass, direction, pathWith(selectCandidates())
                .get(), filters);
    }

    @Override
    protected String getProposedId(Entity.Blueprint r) {
        // this doesn't make sense for Relationships
        throw new UnsupportedOperationException();
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, Entity.Blueprint r) {
        return new Filter[0];
    }

    @Override
    public Relationships.Multiple named(String name) {
        return createMultiBrowser(RelationWith.name(name));
    }

    @Override
    public Relationships.Multiple named(Relationships.WellKnown name) {
        return named(name.name());
    }

    @Override
    public Relationships.Single linkWith(String name, Entity targetOrSource, Map<String, String> properties) {
        if (null == name) {
            throw new IllegalArgumentException("name was null");
        }
        if (null == targetOrSource) {
            throw new IllegalArgumentException("targetOrSource was null");
        }

        Vertex incidenceVertex = convert(targetOrSource);

        if (contains.name().equals(name)) {
            Direction d = direction == outgoing ? Direction.OUT :
                    (direction == incoming ? Direction.IN : Direction.BOTH);

            checkContains(d, incidenceVertex);
        }

        HawkularPipeline<?, Edge> pipe = null;
        switch (direction) {
            case outgoing:
                pipe = source().linkOut(name, incidenceVertex).cap().cast(Edge.class);
                break;
            case incoming:
                pipe = source().linkIn(name, incidenceVertex).cap().cast(Edge.class);
                break;
            case both:
                // basically creates a bi-directional relationship
                pipe = source().linkBoth(name, incidenceVertex).cap().cast(Edge.class);
                break;
        }

        Edge newEdge = pipe.next();
        //believe it or not, Titan cannot filter on ids, hence we need to store the id as a property, too
        newEdge.setProperty(Constants.Property.uid.name(), newEdge.getId().toString());

        context.getGraph().commit();

        return createSingleBrowser(RelationWith.id(newEdge.getId().toString()));
    }

    @Override
    public Relationships.Single linkWith(Relationships.WellKnown name, Entity targetOrSource,
                                         Map<String, String> properties) {
        return linkWith(name.name(), targetOrSource, null);
    }

    @Override
    public void update(Relationship relationship) throws RelationNotFoundException {
        if (null == relationship) {
            throw new IllegalArgumentException("relationship was null");
        }
        if (null == relationship.getId()) {
            throw new IllegalArgumentException("relationship's ID was null");
        }

       // check if the source/target vertex of the relationship is on the current position in hawk-pipe. If not use the
       // `ifThenElse` for returning an empty iterator and fail subsequent querying
        PipeFunction<Vertex, Boolean> ifFunction = vertex -> {
            String uid = vertex.getProperty(Constants.Property.uid.name());
            boolean sourceOk = uid.equals(relationship.getSource().getId());
            boolean targetOk = uid.equals(relationship.getTarget().getId());
            return direction == outgoing ? sourceOk : direction ==
                    incoming ? targetOk : (sourceOk || targetOk);
        };
        PipeFunction<Vertex, ?> thenFunction = v -> v;
        PipeFunction<Vertex, ?> elseFunction = v -> Collections.<Vertex>emptyList().iterator();
        HawkularPipeline<?, ?> pipe = source().ifThenElse(ifFunction, thenFunction, elseFunction);

        switch (direction) {
            case outgoing:
                pipe = pipe.outE(relationship.getName());
                break;
            case incoming:
                pipe = pipe.inE(relationship.getName());
                break;
            case both:
                pipe = pipe.bothE(relationship.getName());
                break;
        }

        HawkularPipeline<?, Edge> edges = pipe.hasUid(relationship.getId()).cast(Edge.class);
        if (!edges.hasNext()) {
            throw new RelationNotFoundException(relationship.getId(), FilterApplicator.filters(path));
        }
        Edge edge = edges.next();
        if (!edge.getLabel().equals(relationship.getName())) {
            throw new RelationNotFoundException(getUid(edge), FilterApplicator.filters(path),
                    "Cannot update the name of a relationship. Create a new relationship instead.");
        }
        final Direction d1 = direction == outgoing ? Direction.IN : Direction.OUT;
        final Direction d2 = direction == outgoing ? Direction.OUT : Direction.IN;
        if (!(matches(edge.getVertex(d1), relationship.getTarget()) && matches(edge.getVertex(d2),
                relationship.getSource()))) {

            throw new RelationNotFoundException(getUid(edge), FilterApplicator.filters(path),
                    "Cannot update the source or target of a relationship. Create a new relationship instead.");
        }

        ElementHelper.setProperties(edge, relationship.getProperties());
    }

    @Override
    public void delete(String id) throws RelationNotFoundException {
        if (null == id) {
            throw new IllegalArgumentException("relationship's id was null");
        }
        HawkularPipeline<?, ? extends Element> pipe = null;
        switch (direction) {
            case outgoing:
                pipe = source().outE().hasUid(id);
                break;
            case incoming:
                pipe = source().inE().hasUid(id);
                break;
            case both:
                pipe = source().bothE().hasUid(id);
                break;
        }
        if (!pipe.hasNext()) {
            throw new RelationNotFoundException(id, null);
        }
        pipe.remove();
    }

    private void checkContains(Direction direction, Vertex incidenceVertex) {
        if (direction == Direction.BOTH) {
            throw new IllegalArgumentException("2 vertices cannot contain each other.");
        }

        //check for diamonds
        if (direction == Direction.OUT && incidenceVertex.getEdges(Direction.IN, contains.name()).iterator()
                .hasNext()) {
            throw new IllegalArgumentException("The target is already contained in another entity.");
        } else if (direction == Direction.IN && source().iterator().next().getEdges(Direction.IN, contains.name())
                .iterator().hasNext()) {
            throw new IllegalArgumentException("The source is already contained in another entity.");
        }

        //check for loops
        Vertex thisVertex = source().iterator().next();
        if (thisVertex.getId().equals(incidenceVertex.getId())) {
            throw new IllegalArgumentException("An entity cannot contain itself.");
        }

        if (direction == Direction.IN && source().as("source").out(contains.name()).loop("source",
                (l) -> !l.getObject().getId().equals(incidenceVertex.getId())).count() > 0) {

            throw new IllegalArgumentException("The target (indirectly) contains the source." +
                    " The source therefore cannot contain the target.");
        } else if (direction == Direction.OUT && source().as("source").in(contains.name()).loop("source",
                (l) -> !l.getObject().getId().equals(incidenceVertex.getId())).count() > 0) {

            throw new IllegalArgumentException("The source (indirectly) contains the target." +
                    " The target therefore cannot contain the source.");
        }
    }
}
