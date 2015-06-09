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
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;

import java.util.Map;

import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

/**
 * @author Lukas Krejci
 * @author Jiri Kremser
 * @since 0.0.1
 */
final class RelationshipService<E extends Entity<B, U>, B extends Entity.Blueprint, U extends AbstractElement.Update>
        extends AbstractGraphService implements Relationships.ReadWrite, Relationships.Read {

    static final String[] MAPPED_PROPERTIES = new String[]{Constants.Property.__eid.name()};

    private final InventoryContext context;
    private final Relationships.Direction direction;
    private final PathContext pathContext;
    private final Class<E> sourceEntityClass;

    RelationshipService(InventoryContext iContext, PathContext ctx, Class<E> sourceClass, Relationships.Direction
            direction) {
        super(iContext, ctx.sourcePath);
        this.context = iContext;
        this.pathContext = ctx;
        this.direction = direction;
        this.sourceEntityClass = sourceClass;
    }

    @Override
    public Relationships.Single get(String id) {
        return createSingleBrowser(RelationWith.id(id));
    }

    @Override
    public Relationships.Multiple getAll(RelationFilter... filters) {
        return createMultiBrowser(filters);
    }

    private Relationships.Single createSingleBrowser(RelationFilter... filters) {
        return RelationshipBrowser.single(context, sourceEntityClass, direction, pathWith(pathContext.candidatesFilters)
                .get(), filters);
    }

    private Relationships.Multiple createMultiBrowser(RelationFilter... filters) {
        return RelationshipBrowser.multiple(context, direction, pathWith(pathContext.candidatesFilters)
                .get(), filters);
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
    public Relationships.Single linkWith(String name, Entity<?, ?> targetOrSource, Map<String, Object> properties) {
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
        newEdge.setProperty(Constants.Property.__eid.name(), newEdge.getId().toString());

        context.getGraph().commit();

        return createSingleBrowser(RelationWith.id(newEdge.getId().toString()));
    }

    @Override
    public Relationships.Single linkWith(Relationships.WellKnown name, Entity<?, ?> targetOrSource,
            Map<String, Object> properties) {
        return linkWith(name.name(), targetOrSource, null);
    }

    @Override
    public void update(String id, Relationship.Update update) throws RelationNotFoundException {
        Edge edge = context.getGraph().getEdge(id);

        checkProperties(update.getProperties(), MAPPED_PROPERTIES);
        updateProperties(edge, update.getProperties(), MAPPED_PROPERTIES);

        context.getGraph().commit();
    }

    @Override
    public void delete(String id) throws RelationNotFoundException {
        if (null == id) {
            throw new IllegalArgumentException("relationship's id was null");
        }
        HawkularPipeline<?, ? extends Element> pipe = null;
        switch (direction) {
            case outgoing:
                pipe = source().outE().hasEid(id);
                break;
            case incoming:
                pipe = source().inE().hasEid(id);
                break;
            case both:
                pipe = source().bothE().hasEid(id);
                break;
        }
        if (!pipe.hasNext()) {
            throw new RelationNotFoundException(id, (Filter[]) null);
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
