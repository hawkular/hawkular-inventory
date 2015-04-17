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
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.ElementBlueprintVisitor;
import org.hawkular.inventory.api.model.ElementUpdateVisitor;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.Query;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.relationship;
import static java.util.stream.Collectors.toSet;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
final class TinkerpopBackend implements InventoryBackend<Element> {
    private final InventoryContext<?> context;

    public TinkerpopBackend(InventoryContext<?> context) {
        this.context = context;
    }

    @Override
    public Transaction startTransaction(boolean mutating) {
        return context.startTransaction(mutating);
    }

    @Override
    public Element find(CanonicalPath element) throws ElementNotFoundException {
        HawkularPipeline<?, ? extends Element> q = translate(Query.to(element));
        if (!q.hasNext()) {
            throw new ElementNotFoundException();
        } else {
            return q.next();
        }
    }

    @Override
    public Page<Element> query(Query query, Pager pager) {
        HawkularPipeline<?, ? extends Element> q = translate(query);

        q.counter("total").page(pager);

        return new Page<>(q.cast(Element.class).toList(), pager, q.getCount("total"));
    }

    private HawkularPipeline<?, ? extends Element> translate(Query query) {
        HawkularPipeline<?, ? extends Element> q;
        if (query.getFragments()[0].getFilter() instanceof RelationFilter) {
            q = new HawkularPipeline<>(context.getGraph()).E();
        } else {
            q = new HawkularPipeline<>(context.getGraph()).V();
        }

        FilterApplicator.applyAll(query, q);

        return q;
    }

    @Override
    public <T extends AbstractElement<?, ?>> Page<T> query(Query query, Pager pager,
            Function<Element, T> conversion, Function<T, Boolean> filter) {

        HawkularPipeline<?, ? extends Element> q;

        if (query.getFragments()[0].getFilter() instanceof RelationFilter) {
            q = new HawkularPipeline<>(context.getGraph()).E();
        } else {
            q = new HawkularPipeline<>(context.getGraph()).V();
        }

        FilterApplicator.applyAll(query, q);

        HawkularPipeline<?, T> q2;
        if (filter == null) {
            q2 = q.counter("total").page(pager).transform(conversion::apply);
        } else {
            //the ResultFilter interface requires an entity to check its applicability and can rule out some of the
            //entities from the result set, which affects the total count. We therefore need to convert to entity first
            //and only then filter, count and page.
            //Note that it would not be enough to pass the current path and the ID to the filter, because for the filter
            //to have stable ids, it needs to have the "canonical" path to the entity, which the inventory traversal
            //path might not be. The transformation of a non-canonical to canonical path is essentially identical
            //operation to converting the vertex to the entity.
            q2 = q.transform(conversion::apply).filter(filter::apply).counter("total")
                    .page(pager, (e, p) -> {
                        if (AbstractElement.ID_PROPERTY.equals(p)) {
                            return e.getId();
                        } else {
                            return (Comparable) e.getProperties().get(p);
                        }
                    });
        }

        return new Page<>(q2.toList(), pager, q.getCount("total"));
    }

    @Override
    public Iterator<Element> getTransitiveClosureOver(Element startingPoint, String relationshipName,
            Relationships.Direction direction) {
        if (!(startingPoint instanceof Vertex)) {
            return Collections.<Element>emptyList().iterator();
        } else {
            HawkularPipeline<?, Element> ret = new HawkularPipeline<Element, Element>(startingPoint).as("start");

            switch (direction) {
                case incoming:
                    ret.in(relationshipName);
                    break;
                case outgoing:
                    ret.out(relationshipName);
                    break;
                case both:
                    ret.both(relationshipName);
                    break;
            }

            //toList() is important as it ensures eager evaluation of the closure - the callers might modify the
            //conditions for the evaluation during the iteration which would skew the results.
            return ret.loop("start", (x) -> true, (x) -> true).toList().iterator();
        }
    }

    @Override
    public boolean hasRelationship(Element entity, Relationships.Direction direction, String relationshipName) {
        if (!(entity instanceof Vertex)) {
            return false;
        }

        return ((Vertex) entity).getEdges(toNative(direction), relationshipName).iterator().hasNext();
    }

    @Override
    public boolean hasRelationship(Element source, Element target, String relationshipName) {
        if (!(source instanceof Vertex) || !(target instanceof Vertex)) {
            return false;
        }

        Iterator<Vertex> targets = ((Vertex) source).getVertices(Direction.OUT, relationshipName).iterator();

        while (targets.hasNext()) {
            if (target.equals(targets.next())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Element getRelationship(Element source, Element target, String relationshipName)
            throws ElementNotFoundException {

        if (!(source instanceof Vertex) || !(target instanceof Vertex)) {
            throw new IllegalArgumentException("Source or target entity not a vertex.");
        }

        if (relationshipName == null) {
            throw new IllegalArgumentException("relationshipName == null");
        }

        Vertex t = (Vertex) target;

        Iterator<Edge> it = new HawkularPipeline<>(source).outE(relationshipName).remember().inV().hasType(getType(t))
                .hasEid(getEid(t)).recall().cast(Edge.class);

        if (!it.hasNext()) {
            throw new ElementNotFoundException();
        } else {
            return it.next();
        }
    }

    @Override
    public Set<Element> getRelationships(Element entity, Relationships.Direction direction, String... names) {
        if (!(entity instanceof Vertex)) {
            return Collections.emptySet();
        }

        Vertex v = (Vertex) entity;

        HawkularPipeline<?, Element> q = new HawkularPipeline<>(v);

        switch (direction) {
            case incoming:
                q.inE(names);
                break;
            case outgoing:
                q.outE(names);
                break;
            case both:
                q.bothE(names);
                break;
            default:
                throw new AssertionError("Invalid relationship direction specified: " + direction);
        }

        return StreamSupport.stream(q.spliterator(), false).collect(toSet());
    }

    @Override
    public String extractRelationshipName(Element relationship) {
        return ((Edge) relationship).getLabel();
    }

    @Override
    public Element getRelationshipSource(Element relationship) {
        return ((Edge) relationship).getVertex(Direction.OUT);
    }

    @Override
    public Element getRelationshipTarget(Element relationship) {
        return ((Edge) relationship).getVertex(Direction.IN);
    }

    @Override
    public String extractId(Element entityRepresentation) {
        return entityRepresentation.getProperty(Constants.Property.__eid.name());
    }

    @Override
    public Class<? extends AbstractElement<?, ?>> extractType(Element entityRepresentation) {
        if (entityRepresentation instanceof Edge) {
            return Relationship.class;
        } else {
            return getType((Vertex) entityRepresentation).getEntityType();
        }
    }

    @Override
    public CanonicalPath extractCanonicalPath(Element entityRepresentation) {
        return CanonicalPath.fromString(entityRepresentation.getProperty(Constants.Property.__cp.name()));
    }

    @Override
    public <T extends AbstractElement<?, ?>> T convert(Element entityRepresentation, Class<T> entityType) {
        Constants.Type type = Constants.Type.of(extractType(entityRepresentation));

        Vertex environmentVertex;
        Vertex feedVertex;

        AbstractElement<?, ?> e;

        if (type == relationship) {
            Edge edge = (Edge) entityRepresentation;
            Entity<?, ?> source = convert(edge.getVertex(Direction.OUT), Entity.class);
            Entity<?, ?> target = convert(edge.getVertex(Direction.IN), Entity.class);

            e = new Relationship(extractId(edge), edge.getLabel(), source, target);
        } else {
            Vertex v = (Vertex) entityRepresentation;

            switch (type) {
                case environment:
                    e = new Environment(extractCanonicalPath(v));
                    break;
                case feed:
                    e = new Feed(extractCanonicalPath(v));
                    break;
                case metric:
                    Vertex mdv = v.getVertices(Direction.IN, Relationships.WellKnown.defines.name()).iterator()
                            .next();
                    MetricType md = convert(mdv, MetricType.class);
                    e = new Metric(extractCanonicalPath(v), md);
                    break;
                case metricType:
                    e = new MetricType(extractCanonicalPath(v), MetricUnit.fromDisplayName(
                            v.getProperty(Constants.Property.__unit.name())));
                    break;
                case resource:
                    Vertex rtv = v.getVertices(Direction.IN, Relationships.WellKnown.defines.name()).iterator().next();
                    ResourceType rt = convert(rtv, ResourceType.class);
                    e = new Resource(extractCanonicalPath(v), rt);
                    break;
                case resourceType:
                    e = new ResourceType(extractCanonicalPath(v), (String) v.getProperty(
                            Constants.Property.__version.name()));
                    break;
                case tenant:
                    e = new Tenant(extractCanonicalPath(v));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type of vertex");
            }
        }

        List<String> mappedProps = Arrays.asList(type.getMappedProperties());
        Map<String, Object> filteredProperties = new HashMap<>();
        entityRepresentation.getPropertyKeys().forEach(k -> {
            if (!mappedProps.contains(k)) {
                filteredProperties.put(k, entityRepresentation.getProperty(k));
            }
        });

        return e.accept(new ElementVisitor.Simple<T, Void>() {
            @Override
            public T visitTenant(Tenant tenant, Void ignored) {
                return common(tenant, Tenant.Update.builder());
            }

            @Override
            public T visitEnvironment(Environment environment, Void ignored) {
                return common(environment, Environment.Update.builder());
            }

            @Override
            public T visitFeed(Feed feed, Void ignored) {
                return common(feed, Feed.Update.builder());
            }

            @Override
            public T visitMetric(Metric metric, Void ignored) {
                return common(metric, Metric.Update.builder());
            }

            @Override
            public T visitMetricType(MetricType metricType, Void ignored) {
                return common(metricType, MetricType.Update.builder());
            }

            @Override
            public T visitResource(Resource resource, Void ignored) {
                return common(resource, Resource.Update.builder());
            }

            @Override
            public T visitResourceType(ResourceType type, Void ignored) {
                return common(type, ResourceType.Update.builder());
            }

            @Override
            public T visitRelationship(Relationship relationship, Void parameter) {
                return common(relationship, Relationship.Update.builder());
            }

            private <U extends AbstractElement.Update> T common(AbstractElement<?, U> entity,
                    AbstractElement.Update.Builder<U, ?> bld) {
                return entityType.cast(entity.update().with(bld.withProperties(filteredProperties).build()));
            }
        }, null);
    }

    @Override
    public Element relate(Element sourceEntity, Element targetEntity, String name, Map<String, Object> properties) {
        if (name == null) {
            throw new IllegalArgumentException("name == null");
        }

        if (!(sourceEntity instanceof Vertex)) {
            throw new IllegalArgumentException("Source not a vertex.");
        }

        if (!(targetEntity instanceof Vertex)) {
            throw new IllegalArgumentException("Target not a vertex.");
        }

        Edge e = ((Vertex) sourceEntity).addEdge(name, (Vertex) targetEntity);
        if (properties != null) {
            ElementHelper.setProperties(e, properties);
        }
        e.setProperty(Constants.Property.__eid.name(), e.getId().toString());
        return e;
    }

    @Override
    public Element persist(CanonicalPath path, AbstractElement.Blueprint blueprint) {
        return blueprint.accept(new ElementBlueprintVisitor.Simple<Element, Void>() {

            @Override
            public Element visitTenant(Tenant.Blueprint tenant, Void parameter) {
                return common(path, blueprint.getProperties(), Tenant.class);
            }

            @Override
            public Element visitEnvironment(Environment.Blueprint environment, Void parameter) {
                return common(path, blueprint.getProperties(), Environment.class);
            }

            @Override
            public Element visitFeed(Feed.Blueprint feed, Void parameter) {
                return common(path, blueprint.getProperties(), Feed.class);
            }

            @Override
            public Element visitMetric(Metric.Blueprint metric, Void parameter) {
                return common(path, blueprint.getProperties(), Metric.class);
            }

            @Override
            public Element visitMetricType(MetricType.Blueprint definition, Void parameter) {
                return common(path, blueprint.getProperties(), MetricType.class);
            }

            @Override
            public Element visitResource(Resource.Blueprint resource, Void parameter) {
                return common(path, blueprint.getProperties(), Resource.class);
            }

            @Override
            public Element visitResourceType(ResourceType.Blueprint type, Void parameter) {
                return common(path, blueprint.getProperties(), ResourceType.class);
            }

            @Override
            public Element visitRelationship(Relationship.Blueprint relationship, Void parameter) {
                throw new IllegalArgumentException("Relationships cannot be persisted using the persist() method.");
            }

            private Element common(org.hawkular.inventory.api.model.CanonicalPath path, Map<String, Object> properties,
                    Class<? extends AbstractElement<?, ?>> cls) {
                checkProperties(properties, Constants.Type.of(cls).getMappedProperties());

                Vertex v = context.getGraph().addVertex(null);
                v.setProperty(Constants.Property.__type.name(), Constants.Type.of(cls).name());
                v.setProperty(Constants.Property.__eid.name(), path.getSegment().getElementId());
                v.setProperty(Constants.Property.__cp.name(), path.toString());

                if (properties != null) {
                    ElementHelper.setProperties(v, properties);
                }

                return v;
            }
        }, null);
    }

    @Override
    public void update(Element entity, AbstractElement.Update update) {
        update.accept(new ElementUpdateVisitor.Simple<Void, Void>() {
            @Override
            public Void visitTenant(Tenant.Update tenant, Void parameter) {
                common(tenant.getProperties(), Tenant.class);
                return null;
            }

            @Override
            public Void visitEnvironment(Environment.Update environment, Void parameter) {
                common(environment.getProperties(), Environment.class);
                return null;
            }

            @Override
            public Void visitFeed(Feed.Update feed, Void parameter) {
                common(feed.getProperties(), Feed.class);
                return null;
            }

            @Override
            public Void visitMetric(Metric.Update metric, Void parameter) {
                common(metric.getProperties(), Metric.class);
                return null;
            }

            @Override
            public Void visitMetricType(MetricType.Update definition, Void parameter) {
                common(definition.getProperties(), MetricType.class);
                entity.setProperty(Constants.Property.__unit.name(), definition.getUnit().getDisplayName());
                return null;
            }

            @Override
            public Void visitResource(Resource.Update resource, Void parameter) {
                common(resource.getProperties(), Resource.class);
                return null;
            }

            @Override
            public Void visitResourceType(ResourceType.Update type, Void parameter) {
                common(type.getProperties(), ResourceType.class);
                entity.setProperty(Constants.Property.__version.name(), type.getVersion());
                return null;
            }

            @Override
            public Void visitRelationship(Relationship.Update relationship, Void parameter) {
                common(relationship.getProperties(), Relationship.class);
                return null;
            }

            private void common(Map<String, Object> properties, Class<? extends AbstractElement<?, ?>> entityType) {
                Class<?> actualType = extractType(entity);
                if (!actualType.equals(entityType)) {
                    throw new IllegalArgumentException("Update object doesn't correspond to the actual type of the" +
                            " entity.");
                }
                String[] disallowedProperties = Constants.Type.of(entityType).getMappedProperties();
                checkProperties(properties, disallowedProperties);
                updateProperties(entity, properties, disallowedProperties);
            }
        }, null);
    }

    @Override
    public void delete(Element entity) {
        entity.remove();
    }

    @Override
    public void commit(Transaction t) {
        context.commit(t);
    }

    @Override
    public void rollback(Transaction t) {
        context.rollback(t);
    }

    @Override
    public void close() throws Exception {
        context.getGraph().shutdown();
    }

    /**
     * If the properties map contains a key from the disallowed properties, throw an exception.
     *
     * @param properties           the properties to check
     * @param disallowedProperties the list of property names that cannot appear in the provided map
     * @throws IllegalArgumentException if the map contains one or more disallowed keys
     */
    static void checkProperties(Map<String, Object> properties, String[] disallowedProperties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        HashSet<String> disallowed = new HashSet<>(properties.keySet());
        disallowed.retainAll(Arrays.asList(disallowedProperties));

        if (!disallowed.isEmpty()) {
            throw new IllegalArgumentException("The following properties are reserved for this type of entity: "
                    + Arrays.asList(disallowedProperties));
        }
    }

    /**
     * Updates the properties of the element, disregarding any changes of the disallowed properties
     *
     * <p> The list of the disallowed properties will usually come from
     * {@link org.hawkular.inventory.impl.tinkerpop.Constants.Type#getMappedProperties()}.
     *
     * @param e                    the element to update properties of
     * @param properties           the properties to update
     * @param disallowedProperties the list of properties that are not allowed to change.
     */
    static void updateProperties(Element e, Map<String, Object> properties, String[] disallowedProperties) {
        Set<String> disallowed = new HashSet<>(Arrays.asList(disallowedProperties));

        //remove all non-mapped properties, that are not in the update
        String[] toRemove = e.getPropertyKeys().stream()
                .filter((p) -> !disallowed.contains(p) && !properties.containsKey(p)).toArray(String[]::new);

        for (String p : toRemove) {
            e.removeProperty(p);
        }

        //update and add new the properties
        properties.forEach((p, v) -> {
            if (!disallowed.contains(p)) {
                e.setProperty(p, v);
            }
        });
    }

    static Vertex getTenantVertexOf(Vertex entityVertex) {
        Constants.Type type = getType(entityVertex);

        switch (type) {
            case environment:
            case metricType:
            case resourceType:
                return entityVertex.getVertices(Direction.IN, Relationships.WellKnown.contains.name()).iterator()
                        .next();
            case feed:
            case resource:
            case metric:
                return getTenantVertexOf(getEnvironmentVertexOf(entityVertex));
            default:
                return null;
        }
    }

    static Vertex getEnvironmentVertexOf(Vertex entityVertex) {
        Constants.Type type = getType(entityVertex);

        switch (type) {
            case feed:
            case resource:
            case metric:
                return new HawkularPipeline<>(entityVertex).in(contains).hasType(Constants.Type.environment).iterator()
                        .next();
            default:
                return null;
        }
    }

    static Vertex getEnvironmentVertexOrNull(Vertex entityVertex) {
        Constants.Type type = getType(entityVertex);

        switch (type) {
            case feed:
            case resource:
            case metric:
                Iterator<Vertex> envs = new HawkularPipeline<>(entityVertex).in(contains)
                        .hasType(Constants.Type.environment).iterator();
                if (envs.hasNext()) {
                    return envs.next();
                }
                return null;
            default:
                return null;
        }
    }

    static Vertex getFeedVertexOrNull(Vertex entityVertex) {
        Constants.Type type = getType(entityVertex);

        switch (type) {
            case resource:
            case metric:
                Iterator<Vertex> feeds = new HawkularPipeline<>(entityVertex).in(contains).hasType(Constants.Type.feed)
                        .iterator();
                if (feeds.hasNext()) {
                    return feeds.next();
                }
                return null;
            default:
                return null;
        }
    }

    /**
     * Gets the type of the entity that the provided vertex represents.
     */
    static Constants.Type getType(Vertex v) {
        return Constants.Type.valueOf(v.getProperty(Constants.Property.__type.name()));
    }

    static String getEid(Element e) {
        return e.getProperty(Constants.Property.__eid.name());
    }

    static Direction toNative(Relationships.Direction direction) {
        return direction == incoming ? Direction.IN : (direction == outgoing ? Direction.OUT : Direction.BOTH);
    }
}
