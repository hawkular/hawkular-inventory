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

import static java.util.stream.Collectors.toSet;

import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.relationship;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
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

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementBlueprintVisitor;
import org.hawkular.inventory.api.model.ElementUpdateVisitor;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.api.paging.SizeAwarePage;
import org.hawkular.inventory.base.Query;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.base.spi.ShallowStructuredData;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.GraphQuery;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.ElementHelper;
import com.tinkerpop.blueprints.util.io.graphson.GraphSONMode;
import com.tinkerpop.blueprints.util.io.graphson.GraphSONWriter;
import com.tinkerpop.pipes.PipeFunction;

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
    public Element find(CanonicalPath path) throws ElementNotFoundException {
        GraphQuery query = context.getGraph().query().has(Constants.Property.__cp.name(), path.toString());
        Iterator<? extends Element> it = query.vertices().iterator();
        if (!it.hasNext()) {
            it = query.edges().iterator();
            if (!it.hasNext()) {
                throw new ElementNotFoundException();
            }
        }
        return it.next();
    }

    @Override
    public Page<Element> traverse(Element startingPoint, Query query, Pager pager) {
        HawkularPipeline<?, ? extends Element> q = translate(startingPoint, query);

        q.counter("total").page(pager);

        return new SizeAwarePage<>(q.cast(Element.class).iterator(), pager, () -> q.getCount("total"));
    }

    @Override public Element traverseToSingle(Element startingPoint, Query query) {
        HawkularPipeline<?, ? extends Element> q = translate(startingPoint, query);
        if (q.hasNext()) {
            return q.cast(Element.class).next();
        }
        return null;
    }

    @Override
    public Page<Element> query(Query query, Pager pager) {
        return traverse(null, query, pager);
    }

    @Override public Element querySingle(Query query) {
        return traverseToSingle(null, query);
    }

    private HawkularPipeline<?, ? extends Element> translate(Element startingPoint, Query query) {
        HawkularPipeline<?, ? extends Element> q;

        if (startingPoint != null) {
            q = new HawkularPipeline<>(startingPoint);
        } else if (query.getFragments()[0].getFilter() instanceof RelationFilter) {
            q = new HawkularPipeline<>(context.getGraph()).E();
        } else {
            q = new HawkularPipeline<>(context.getGraph()).V();
        }

        FilterApplicator.applyAll(query, q);

        return q;
    }

    @Override
    public <T> Page<T> query(Query query, Pager pager,
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
                        if (!(e instanceof AbstractElement)) {
                            return null;
                        }

                        AbstractElement<?, ?> el = (AbstractElement<?, ?>) e;

                        if (AbstractElement.ID_PROPERTY.equals(p)) {
                            return el.getId();
                        } else {
                            return (Comparable) el.getProperties().get(p);
                        }
                    });
        }

        return new SizeAwarePage<>(q2, pager, () -> q.getCount("total"));
    }

    @Override
    public Iterator<Element> getTransitiveClosureOver(Element startingPoint, Relationships.Direction direction,
                                                      String... relationshipNames) {

        return getTransitiveClosureOverImpl(startingPoint, direction, relationshipNames).iterator();
    }

    @Override
    public <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(CanonicalPath startingPoint,
                                                                         Relationships.Direction direction,
                                                                         Class<T> clazz,
                                                                         String... relationshipNames) {
        try {
            Element startingElement = find(startingPoint);
            if (!(startingElement instanceof Vertex)) {
                return Collections.<T>emptyList().iterator();
            }

            return getTransitiveClosureOverImpl(startingElement, direction, relationshipNames).stream()
                    .map(vertex -> convert(vertex, clazz)).iterator();

        } catch (ElementNotFoundException e) {
            throw new EntityNotFoundException(clazz, null);
        }
    }

    private List<Element> getTransitiveClosureOverImpl(Element startingPoint, Relationships.Direction direction,
                                                       String... relationshipNames) {
        if (!(startingPoint instanceof Vertex)) {
            return Collections.<Element>emptyList();
        } else {
            HawkularPipeline<?, Element> ret = new HawkularPipeline<Element, Element>(startingPoint).as("start");

            switch (direction) {
                case incoming:
                    ret.in(relationshipNames);
                    break;
                case outgoing:
                    ret.out(relationshipNames);
                    break;
                case both:
                    ret.both(relationshipNames);
                    break;
            }

            //toList() is important as it ensures eager evaluation of the closure - the callers might modify the
            //conditions for the evaluation during the iteration which would skew the results.
            return ret.loop("start", (x) -> true, (x) -> true).toList();
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
    public Class<?> extractType(Element entityRepresentation) {
        if (entityRepresentation instanceof Edge) {
            return Relationship.class;
        } else {
            return getType((Vertex) entityRepresentation).getEntityType();
        }
    }

    @Override
    public CanonicalPath extractCanonicalPath(Element entityRepresentation) {
        String cp = entityRepresentation.getProperty(Constants.Property.__cp.name());
        if (cp == null) {
            throw new IllegalArgumentException("Element is not representable using a canonical path. Element type is "
                    + extractType(entityRepresentation).getSimpleName() + ", element id is '"
                    + extractId(entityRepresentation) + "'.");
        }
        return CanonicalPath.fromString(cp);
    }

    @Override
    public <T> T convert(Element entityRepresentation, Class<T> entityType) {
        Constants.Type type = Constants.Type.of(extractType(entityRepresentation));

        Object e;

        if (type == relationship) {
            Edge edge = (Edge) entityRepresentation;
            CanonicalPath source = extractCanonicalPath(edge.getVertex(Direction.OUT));
            CanonicalPath target = extractCanonicalPath(edge.getVertex(Direction.IN));

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
                    e = new MetricType(extractCanonicalPath(v),
                            MetricUnit.fromDisplayName(v.getProperty(Constants.Property.__unit.name())),
                            MetricDataType.fromDisplayName(v.getProperty(
                                    Constants.Property.__metric_data_type.name())));
                    break;
                case resource:
                    Vertex rtv = v.getVertices(Direction.IN, Relationships.WellKnown.defines.name()).iterator().next();
                    ResourceType rt = convert(rtv, ResourceType.class);
                    e = new Resource(extractCanonicalPath(v), rt
                    );
                    break;
                case resourceType:
                    e = new ResourceType(extractCanonicalPath(v));
                    break;
                case tenant:
                    e = new Tenant(extractCanonicalPath(v));
                    break;
                case structuredData:
                    e = loadStructuredData(v, StructuredData.class.equals(entityType));
                    break;
                case dataEntity:
                    CanonicalPath cp = extractCanonicalPath(v);

                    e = new DataEntity(cp.up(), DataEntity.Role.valueOf(cp.getSegment().getElementId()),
                            loadStructuredData(v, hasData));
                    break;
                case operationType:
                    e = new OperationType(extractCanonicalPath(v));
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

        if (StructuredData.class.equals(entityType)) {
            return entityType.cast(e);
        } else if (ShallowStructuredData.class.equals(entityType)) {
            return entityType.cast(new ShallowStructuredData((StructuredData) e));
        } else {
            @SuppressWarnings("ConstantConditions")
            AbstractElement<?, ?> el = (AbstractElement<?, ?>) e;

            return el.accept(new ElementVisitor<T, Void>() {
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
                public T visitData(DataEntity data, Void ignored) {
                    return common(data, DataEntity.Update.builder());
                }

                @Override public T visitOperationType(OperationType operationType, Void parameter) {
                    return common(operationType, OperationType.Update.builder());
                }

                @Override public T visitUnknown(Object entity, Void parameter) {
                    return null;
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
    }

    @Override
    public Element descendToData(Element dataEntityRepresentation, RelativePath dataPath) {
        Query q = Query.path().with(With.dataAt(dataPath)).get();

        HawkularPipeline<Element, Element> pipeline = new HawkularPipeline<>(dataEntityRepresentation);

        FilterApplicator.applyAll(q, pipeline);

        if (pipeline.hasNext()) {
            return pipeline.next();
        } else {
            return null;
        }
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
        e.setProperty(Constants.Property.__cp.name(), CanonicalPath.of().relationship(e.getId().toString()).get()
                .toString());
        return e;
    }

    @Override
    public Element persist(CanonicalPath path, Blueprint blueprint) {
        return blueprint.accept(new ElementBlueprintVisitor<Element, Void>() {

            @Override
            public Element visitTenant(Tenant.Blueprint tenant, Void parameter) {
                return common(path, tenant.getProperties(), Tenant.class);
            }

            @Override
            public Element visitEnvironment(Environment.Blueprint environment, Void parameter) {
                return common(path, environment.getProperties(), Environment.class);
            }

            @Override
            public Element visitFeed(Feed.Blueprint feed, Void parameter) {
                return common(path, feed.getProperties(), Feed.class);
            }

            @Override
            public Element visitMetric(Metric.Blueprint metric, Void parameter) {
                return common(path, metric.getProperties(), Metric.class);
            }

            @Override
            public Element visitMetricType(MetricType.Blueprint definition, Void parameter) {
                Element entity = common(path, definition.getProperties(), MetricType.class);

                entity.setProperty(Constants.Property.__metric_data_type.name(), definition.getType().getDisplayName());
                return entity;
            }

            @Override
            public Element visitResource(Resource.Blueprint resource, Void parameter) {
                return common(path, resource.getProperties(), Resource.class);
            }

            @Override
            public Element visitResourceType(ResourceType.Blueprint type, Void parameter) {
                return common(path, type.getProperties(), ResourceType.class);
            }

            @Override
            public Element visitRelationship(Relationship.Blueprint relationship, Void parameter) {
                throw new IllegalArgumentException("Relationships cannot be persisted using the persist() method.");
            }

            @Override
            public Element visitData(DataEntity.Blueprint data, Void parameter) {
                return common(path, data.getProperties(), DataEntity.class);
            }

            @Override
            public Element visitOperationType(OperationType.Blueprint operationType, Void parameter) {
                return common(path, operationType.getProperties(), OperationType.class);
            }

            @Override
            public Element visitUnknown(Object blueprint, Void parameter) {
                throw new IllegalArgumentException("Unknown type of entity blueprint: " + blueprint.getClass());
            }

            private Vertex common(org.hawkular.inventory.api.model.CanonicalPath path, Map<String, Object> properties,
                                  Class<? extends AbstractElement<?, ?>> cls) {
                try {
                    checkProperties(properties, Constants.Type.of(cls).getMappedProperties());

                    Vertex v = context.getGraph().addVertex(null);
                    v.setProperty(Constants.Property.__type.name(), Constants.Type.of(cls).name());
                    v.setProperty(Constants.Property.__eid.name(), path.getSegment().getElementId());
                    v.setProperty(Constants.Property.__cp.name(), path.toString());

                    if (properties != null) {
                        ElementHelper.setProperties(v, properties);
                    }
                    return v;
                } catch (RuntimeException e) {
                    throw context.translateException(e);
                }
            }
        }, null);
    }

    @Override
    public Vertex persist(StructuredData structuredData) {
        Vertex thisVertex = context.getGraph().addVertex(null);

        Pair<Vertex, Vertex> parentAndCurrent = new Pair<>(null, thisVertex);

        structuredData.accept(new StructuredData.Visitor.Simple<Void, StructuredData>() {
            @Override
            protected Void defaultAction(Serializable value, StructuredData data) {
                relateToParent();
                parentAndCurrent.second.setProperty(Constants.Property.__type.name(),
                        Constants.Type.structuredData.name());
                parentAndCurrent.second.setProperty(Constants.Property.__structuredDataType.name(),
                        data.getType().name());
                if (value != null) {
                    parentAndCurrent.second.setProperty(Constants.Property.__structuredDataValue.name(), value);
                }
                return null;
            }

            @Override
            public Void visitList(List<StructuredData> value, StructuredData data) {
                relateToParent();
                parentAndCurrent.second.setProperty(Constants.Property.__type.name(),
                        Constants.Type.structuredData.name());
                parentAndCurrent.second.setProperty(Constants.Property.__structuredDataType.name(),
                        StructuredData.Type.list.name());

                Vertex currentParent = parentAndCurrent.first;
                Vertex currentCurrent = parentAndCurrent.second;

                parentAndCurrent.first = parentAndCurrent.second;

                int idx = 0;
                for (StructuredData c : value) {
                    parentAndCurrent.second = context.getGraph().addVertex(null);
                    parentAndCurrent.second.setProperty(Constants.Property.__structuredDataIndex.name(), idx++);
                    c.accept(this, c);
                }

                parentAndCurrent.first = currentParent;
                parentAndCurrent.second = currentCurrent;

                return null;
            }

            @Override
            public Void visitMap(Map<String, StructuredData> value, StructuredData data) {
                relateToParent();
                parentAndCurrent.second.setProperty(Constants.Property.__type.name(),
                        Constants.Type.structuredData.name());
                parentAndCurrent.second.setProperty(Constants.Property.__structuredDataType.name(),
                        StructuredData.Type.map.name());

                Vertex currentParent = parentAndCurrent.first;
                Vertex currentCurrent = parentAndCurrent.second;

                parentAndCurrent.first = parentAndCurrent.second;

                int idx = 0;
                for (Map.Entry<String, StructuredData> e : value.entrySet()) {
                    parentAndCurrent.second = context.getGraph().addVertex(null);
                    //we need to make sure the maps are stored in the same order as seen - the maps are linked
                    //and therefore preserve insertion order
                    parentAndCurrent.second.setProperty(Constants.Property.__structuredDataIndex.name(), idx++);
                    parentAndCurrent.second.setProperty(Constants.Property.__structuredDataKey.name(), e.getKey());
                    e.getValue().accept(this, e.getValue());
                }

                parentAndCurrent.first = currentParent;
                parentAndCurrent.second = currentCurrent;

                return null;
            }

            private void relateToParent() {
                if (parentAndCurrent.first != null) {
                    relate(parentAndCurrent.first, parentAndCurrent.second, Relationships.WellKnown.contains.name(),
                            null);
                }
            }
        }, structuredData);

        return thisVertex;
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
                if (definition.getUnit() != null) {
                    entity.setProperty(Constants.Property.__unit.name(), definition.getUnit().getDisplayName());
                }
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
                return null;
            }

            @Override
            public Void visitRelationship(Relationship.Update relationship, Void parameter) {
                common(relationship.getProperties(), Relationship.class);
                return null;
            }

            @Override
            public Void visitData(DataEntity.Update data, Void parameter) {
                common(data.getProperties(), DataEntity.class);

                Vertex v = (Vertex) entity;
                Vertex dataVertex = v.getVertices(Direction.OUT, Relationships.WellKnown.hasData.name()).iterator()
                        .next();

                Iterator<Element> children = getTransitiveClosureOver(dataVertex,
                        Relationships.Direction.outgoing, Relationships.WellKnown.contains.name());

                while (children.hasNext()) {
                    Vertex c = (Vertex) children.next();
                    context.getGraph().removeVertex(c);
                }
                context.getGraph().removeVertex(dataVertex);

                StructuredData dataValue = data.getValue();
                if (dataValue == null) {
                    dataValue = StructuredData.get().undefined();
                }
                Element newData = persist(dataValue);

                relate(v, newData, Relationships.WellKnown.hasData.name(), null);
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
    public void deleteStructuredData(Element dataRepresentation) {
        if (!StructuredData.class.equals(extractType(dataRepresentation))) {
            throw new IllegalArgumentException("The supplied element is not a data entity's data.");
        }

        Iterator<Element> dataElements = getTransitiveClosureOver(dataRepresentation, outgoing, contains.name());

        // we know the closure is constructed eagerly in this impl, so this loop is OK.
        while (dataElements.hasNext()) {
            delete(dataElements.next());
        }
    }

    @Override
    public void commit(Transaction t) throws CommitFailureException {
        try {
            context.commit(t);
        } catch (Exception e) {
            throw new CommitFailureException(e);
        }
    }

    @Override
    public void rollback(Transaction t) {
        context.rollback(t);
    }

    @Override
    public void close() throws Exception {
        context.getGraph().shutdown();
    }

    private StructuredData loadStructuredData(Vertex owner, Relationships.WellKnown owningEdge) {
        Iterator<Vertex> it = owner.getVertices(Direction.OUT, owningEdge.name()).iterator();
        if (!it.hasNext()) {
            return null;
        }

        return loadStructuredData(it.next(), true);
    }

    private StructuredData loadStructuredData(Vertex root, boolean recurse) {
        StructuredData.Type type = StructuredData.Type.valueOf(root.getProperty(
                Constants.Property.__structuredDataType.name()));

        switch (type) {
            case bool:
                return StructuredData.get().bool(root.getProperty(Constants.Property.__structuredDataValue.name()));
            case integral:
                return StructuredData.get().integral(root.getProperty(Constants.Property.__structuredDataValue.name()));
            case floatingPoint:
                return StructuredData.get()
                        .floatingPoint(root.getProperty(Constants.Property.__structuredDataValue.name()));
            case undefined:
                return StructuredData.get().undefined();
            case string:
                return StructuredData.get().string(root.getProperty(Constants.Property.__structuredDataValue.name()));
            case list:
                StructuredData.ListBuilder lst = StructuredData.get().list();
                if (recurse) {
                    loadStructuredDataList(root, lst);
                }
                return lst.build();
            case map:
                StructuredData.MapBuilder mp = StructuredData.get().map();
                if (recurse) {
                    loadStructuredDataMap(root, mp);
                }
                return mp.build();
            default:
                throw new IllegalArgumentException("Unknown structured data type stored in db: " + type);
        }
    }

    private void loadStructuredDataList(Vertex root, StructuredData.AbstractListBuilder<?> bld) {
        PipeFunction<com.tinkerpop.pipes.util.structures.Pair<Vertex, Vertex>, Integer> orderFn = (vs) -> {
            Integer idxA = vs.getA().getProperty(Constants.Property.__structuredDataIndex.name());
            Integer idxB = vs.getB().getProperty(Constants.Property.__structuredDataIndex.name());

            return idxA - idxB;
        };

        for (Vertex child : new HawkularPipeline<>(root).out(contains).order(orderFn)) {
            StructuredData.Type type = StructuredData.Type.valueOf(child.getProperty(
                    Constants.Property.__structuredDataType.name()));

            switch (type) {
                case bool:
                    bld.addBool(child.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case integral:
                    bld.addIntegral(child.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case floatingPoint:
                    bld.addFloatingPoint(child.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case undefined:
                    bld.addUndefined();
                    break;
                case string:
                    bld.addString(child.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case list:
                    StructuredData.InnerListBuilder<?> lst = bld.addList();
                    loadStructuredDataList(child, lst);
                    lst.closeList();
                    break;
                case map:
                    StructuredData.InnerMapBuilder<?> mp = bld.addMap();
                    loadStructuredDataMap(child, mp);
                    mp.closeMap();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown structured data type stored in db: " + type);
            }
        }
    }

    private void loadStructuredDataMap(Vertex root, StructuredData.AbstractMapBuilder<?> bld) {
        PipeFunction<com.tinkerpop.pipes.util.structures.Pair<Vertex, Vertex>, Integer> orderFn = (vs) -> {
            Integer idxA = vs.getA().getProperty(Constants.Property.__structuredDataIndex.name());
            Integer idxB = vs.getB().getProperty(Constants.Property.__structuredDataIndex.name());

            return idxA - idxB;
        };

        for (Vertex v : new HawkularPipeline<>(root).out(contains).order(orderFn)) {
            String key = v.getProperty(Constants.Property.__structuredDataKey.name()).toString();

            String type = v.getProperty(Constants.Property.__structuredDataType.name());

            switch (StructuredData.Type.valueOf(type)) {
                case bool:
                    bld.putBool(key, v.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case integral:
                    bld.putIntegral(key, v.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case floatingPoint:
                    bld.putFloatingPoint(key, v.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case undefined:
                    bld.putUndefined(key);
                    break;
                case string:
                    bld.putString(key, v.getProperty(Constants.Property.__structuredDataValue.name()));
                    break;
                case list:
                    StructuredData.InnerListBuilder<?> lst = bld.putList(key);
                    loadStructuredDataList(v, lst);
                    lst.closeList();
                    break;
                case map:
                    StructuredData.InnerMapBuilder<?> mp = bld.putMap(key);
                    loadStructuredDataMap(v, mp);
                    mp.closeMap();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown structured data type stored in db: " + type);
            }
        }
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
        if (properties == null) {
            return;
        }

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

    public InputStream getGraphSON(String tenantId) {
        PipedInputStream in = new PipedInputStream();
        //PartitionGraph pGraph = new PartitionGraph(context.getGraph(), Constants.Property.__eid.name(), tenantId);
        new Thread(() -> {
            try (PipedOutputStream out = new PipedOutputStream(in)) {
                GraphSONWriter.outputGraph(/*pGraph*/context.getGraph(), out, GraphSONMode.NORMAL);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create the GraphSON dump.", e);
            }
        }).start();
        return in;
    }

    private static final class Pair<F, S> {
        public F first;
        public S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}
