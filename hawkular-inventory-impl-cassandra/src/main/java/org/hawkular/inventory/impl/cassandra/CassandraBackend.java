/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.impl.cassandra;

import static org.hawkular.inventory.api.Relationships.Direction.both;
import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;

import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;

import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementBlueprintVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Hashes;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.PathSegmentCodec;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.hawkular.rx.cassandra.driver.RxSession;
import org.jboss.logging.Logger;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.TypeTokens;
import com.datastax.driver.core.querybuilder.Insert;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class CassandraBackend implements InventoryBackend<Row> {
    private static final Logger DBG = Logger.getLogger(CassandraBackend.class);

    private static final TypeToken<Map<String, String>> PROPERTIES_TYPE = TypeTokens.mapOf(String.class, String.class);

    private final RxSession session;
    private final Statements statements;
    private final QueryExecutor queryExecutor;
    private final String keyspace;

    CassandraBackend(RxSession session, String keyspace) {
        this.session = session;
        this.statements = new Statements(session);
        this.queryExecutor = new QueryExecutor(statements);
        this.keyspace = keyspace;
    }

    @Override public boolean isPreferringBigTransactions() {
        return false;
    }

    @Override public boolean isUniqueIndexSupported() {
        return true;
    }

    @Override public InventoryBackend<Row> startTransaction() {
        return this;
    }

    @Override public Row find(CanonicalPath element) throws ElementNotFoundException {
        String cp = element.toString();
        final Observable<Row> rowObservable;
        if (element.getSegment().getElementType() == SegmentType.rl) {
            rowObservable = statements.findRelationshipByCanonicalPath(cp);
        } else {
            rowObservable = statements.findEntityByCanonicalPath(cp);
        }

        DBG.debugf("Executing find of %s", element);

        //TODO this doesn't work for relationships - they don't have a CP in database, because it is meant to be
        //inferred from source_cp and target_cp...
        Row row = rowObservable.toBlocking().firstOrDefault(null);
        if (row == null) {
            throw new ElementNotFoundException();
        }
        return row;
    }

    @Override public Page<Row> query(Query query, Pager pager) {
        DBG.debugf("Executing query %s", query);

        Observable<Row> rs = queryExecutor.execute(query);
        //total size just not supported. period
        return new Page<>(rs.toBlocking().getIterator(), pager, -1);
    }

    @Override public Row querySingle(Query query) {
        DBG.debugf("Executing querySingle %s", query);

        Observable<Row> rs = queryExecutor.execute(query);
        Iterator<Row> it = rs.toBlocking().getIterator();
        return it.hasNext() ? it.next() : null;
    }

    @Override public Page<Row> traverse(Row startingPoint, Query query, Pager pager) {
        DBG.debugf("Executing traverse %s from startingPoint %s", query, startingPoint);

        Observable<Row> rs = queryExecutor.traverse(startingPoint, query);
        //total size just not supported. period
        return new Page<>(rs.toBlocking().getIterator(), pager, -1);
    }

    @Override public Row traverseToSingle(Row startingPoint, Query query) {
        DBG.debugf("Executing traverseToSingle %s from startingPoint %s", query, startingPoint);

        Observable<Row> rs = queryExecutor.traverse(startingPoint, query);
        Iterator<Row> it = rs.toBlocking().getIterator();
        return it.hasNext() ? it.next() : null;
    }

    @Override
    public <T> Page<T> query(Query query, Pager pager, Function<Row, T> conversion, Function<T, Boolean> filter) {
        DBG.debugf("Executing query with conversion and filter %s", query);

        if (filter == null) {
            filter = any -> true;
        }

        Observable<Row> rs = queryExecutor.execute(query);
        Observable<T> ts = rs.map(conversion::apply).filter(filter::apply);
        return new Page<>(ts.toBlocking().getIterator(), pager, -1);
    }

    //TODO this is identical in purpose to getRelated()
    private Observable<String> getRelationshipOtherEnds(String cp, Relationships.Direction direction,
                                                        Collection<String> relationshipNames) {
        if (direction == outgoing) {
            return statements.findOutRelationshipBySourceAndNames(cp, relationshipNames)
                    .map(row -> row.getString(Statements.TARGET_CP));
        } else if (direction == incoming) {
            return statements.findInRelationshipByTargetAndNames(cp, relationshipNames)
                    .map(row -> row.getString(Statements.SOURCE_CP));
        } else if (direction == both) {
            return getRelationshipOtherEnds(cp, outgoing, relationshipNames)
                    .mergeWith(getRelationshipOtherEnds(cp, incoming, relationshipNames));
        }
        throw new IllegalStateException("Unsupported direction: " + direction);
    }

    @Override
    public Iterator<Row> getTransitiveClosureOver(Row startingPoint, Relationships.Direction direction,
                                                  String... relationshipNames) {
        String startingCP = startingPoint.getString(Statements.CP);
        if (startingCP == null) {
            // Not a vertex
            return Collections.<Row>emptyList().iterator();
        }

        TransitiveClosureProcessor runner = new TransitiveClosureProcessor(
                cp -> getRelationshipOtherEnds(cp, direction, Arrays.asList(relationshipNames)));
        return runner.process(startingCP)
                .toList()
                .flatMap(listCP -> loadEntities(listCP).toList()
                        .map(unsorted -> reorderEntities(unsorted, listCP)))
                .toBlocking()
                .first()
                .iterator();
    }

    private Observable<Row> loadEntities(List<String> listCP) {
        return statements.findEntityByCanonicalPaths(listCP);
    }

    private List<Row> reorderEntities(List<Row> unsorted, List<String> sortedCps) {
        Map<String, Row> rowsByCP = Maps.uniqueIndex(unsorted, row -> row.getString(Statements.CP));
        return sortedCps.stream().map(rowsByCP::get).collect(Collectors.toList());
    }

    @Override
    public boolean hasRelationship(Row entity, Relationships.Direction direction, String relationshipName) {
        String cp = entity.getString(Statements.CP);
        final Observable<Long> countObservable;
        switch (direction) {
            case incoming:
                countObservable = statements.countInRelationshipByTargetAndName(cp, relationshipName);
                break;
            case outgoing:
                countObservable = statements.countOutRelationshipBySourceAndName(cp, relationshipName);
                break;
            case both:
                return hasRelationship(entity, outgoing, relationshipName)
                        || hasRelationship(entity, incoming, relationshipName);
            default:
                throw new IllegalStateException("Unsupported direction: " + direction);
        }
        return countObservable.toBlocking().first() > 0;
    }

    @Override public boolean hasRelationship(Row source, Row target, String relationshipName) {
        String sourceCp = source.getString(Statements.CP);
        String targetCp = target.getString(Statements.CP);

        String relCP = getRelationshipCanonicalPath(relationshipName, sourceCp, targetCp);

        return !statements.findRelationshipByCanonicalPath(relCP).isEmpty().toBlocking().first();
    }

    @Override
    public Set<Row> getRelationships(Row entity, Relationships.Direction direction, String... names) {
        Observable<Row> rows = getRelationshipRows(entity, direction, names);
        Iterable<Row> res;
        if (names.length == 1) {
            res = rows.toBlocking().toIterable();
        } else {
            Set<String> sortedNames = Stream.of(names).sorted().collect(Collectors.toCollection(TreeSet::new));

            res = rows.filter(r -> sortedNames.contains(r.getString(Statements.NAME)))
                    .toBlocking().toIterable();
        }

        return StreamSupport.stream(res.spliterator(), false).collect(Collectors.toSet());
    }

    private Observable<Row> getRelated(Row entity, Relationships.Direction direction, String... relationshipNames) {
        String entityCp = entity.getString(Statements.CP);

        return getRelationshipRows(entity, direction, relationshipNames)
                .map(r -> {
                    switch (direction) {
                        case outgoing:
                            return r.getString(Statements.TARGET_CP);
                        case incoming:
                            return r.getString(Statements.SOURCE_CP);
                        case both:
                            String source = r.getString(Statements.SOURCE_CP);
                            String target = r.getString(Statements.TARGET_CP);
                            if (source.equals(entityCp)) {
                                return target;
                            } else {
                                return source;
                            }
                        default:
                            throw new IllegalStateException("Unhandled direction: " + direction);
                    }
                })
                .toList()
                .flatMap(statements::findEntityByCanonicalPaths);
    }

    private Observable<Row> getRelationshipRows(Row entity, Relationships.Direction direction, String... names) {
        String entityCP = entity.getString(Statements.CP);

        Observable<Row> res;
        switch (direction) {
            case incoming:
                if (names.length == 1) {
                    res = statements.findInRelationshipByTargetAndName(entityCP, names[0]);
                } else {
                    res = statements.findInRelationshipsByTarget(entityCP);
                }
                break;
            case outgoing:
                if (names.length == 1) {
                    res = statements.findOutRelationshipBySourceAndName(entityCP, names[0]);
                } else {
                    res = statements.findOutRelationshipsBySource(entityCP);
                }
                break;
            case both:
                if (names.length == 1) {
                    res = statements.findInRelationshipByTargetAndName(entityCP, names[0])
                            .mergeWith(statements.findOutRelationshipBySourceAndName(entityCP, names[0]));
                } else {
                    res = statements.findInRelationshipsByTarget(entityCP).mergeWith(
                            statements.findOutRelationshipsBySource(entityCP));
                }
                break;
            default:
                throw new IllegalStateException("Unhandled relationship direction: " + direction);
        }

        return res;
    }

    @Override public Row getRelationship(Row source, Row target, String relationshipName)
            throws ElementNotFoundException {
        String sourceCp = source.getString(Statements.CP);
        String targetCp = target.getString(Statements.CP);

        String relCP = getRelationshipCanonicalPath(relationshipName, sourceCp, targetCp);

        Row result = statements.findRelationshipByCanonicalPath(relCP).toBlocking().firstOrDefault(null);
        if (result == null) {
            throw new ElementNotFoundException();
        }

        return result;
    }

    @Override public Row getRelationshipSource(Row relationship) {
        String sourceCp = relationship.getString(Statements.SOURCE_CP);
        try {
            return find(CanonicalPath.fromString(sourceCp));
        } catch (ElementNotFoundException e) {
            throw new IllegalArgumentException("Could not find the source of the relationship.", e);
        }
    }

    @Override public Row getRelationshipTarget(Row relationship) {
        String targetCp = relationship.getString(Statements.TARGET_CP);
        try {
            return find(CanonicalPath.fromString(targetCp));
        } catch (ElementNotFoundException e) {
            throw new IllegalArgumentException("Could not find the target of the relationship.", e);
        }
    }

    @Override public String extractRelationshipName(Row relationship) {
        return relationship.getString(Statements.NAME);
    }

    @Override public String extractId(Row entityRepresentation) {
        if (entityRepresentation.getColumnDefinitions().contains(Statements.SOURCE_CP)) {
            return extractCanonicalPath(entityRepresentation).getSegment().getElementId();
        } else {
            return entityRepresentation.getString(Statements.ID);
        }
    }

    @Override public Class<?> extractType(Row entityRepresentation) {
        if (entityRepresentation.getColumnDefinitions().contains(Statements.SOURCE_CP)) {
            return Relationship.class;
        } else {
            int ord = entityRepresentation.getInt(Statements.TYPE);
            SegmentType st = SegmentType.values()[ord];
            return Inventory.types().bySegment(st).getElementType();
        }
    }

    @Override public CanonicalPath extractCanonicalPath(Row entityRepresentation) {
        return CanonicalPath.fromString(entityRepresentation.getString(Statements.CP));
    }

    @Override public String extractIdentityHash(Row entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public String extractContentHash(Row entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public String extractSyncHash(Row entityRepresentation) {
        //TODO implement
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override public <T> T convert(Row er, Class<T> entityType) {
        if (!AbstractElement.class.isAssignableFrom(entityType)) {
            throw new IllegalArgumentException("entityType");
        }

        SegmentType type = Inventory.types()
                .byElement((Class<AbstractElement<Blueprint, AbstractElement.Update>>) entityType)
                .getSegmentType();

        CanonicalPath cp = extractCanonicalPath(er);
        String name = er.getString(Statements.NAME);
        Map<String, String> props = er.getColumnDefinitions().contains(Statements.PROPERTIES)
                ? er.get(Statements.PROPERTIES, PROPERTIES_TYPE)
                : Collections.emptyMap();

        Function<Map<String, Object>, Object> applyPropertiesAndConstruct;

        //TODO init these
        String syncHash = "syncHash";
        String identityHash = "identityHash";
        String contentHash = "contentHash";

        switch (type) {
            case rl:
                CanonicalPath sourceCp = CanonicalPath.fromString(er.getString(Statements.SOURCE_CP));
                CanonicalPath targetCp = CanonicalPath.fromString(er.getString(Statements.TARGET_CP));
                applyPropertiesAndConstruct = ps ->
                        new Relationship(cp.getSegment().getElementId(), name, sourceCp, targetCp, ps);
                break;
            case d:
                StructuredData data = loadStructuredData(er, hasData.name());
                applyPropertiesAndConstruct = ps -> new DataEntity(cp, data, identityHash, contentHash, syncHash, ps);
                break;
            case e:
                applyPropertiesAndConstruct = ps -> new Environment(name, cp, contentHash, ps);
                break;
            case f:
                applyPropertiesAndConstruct = ps -> new Feed(name, cp, identityHash, contentHash, syncHash, ps);
                break;
            case m:
                Row mtr = getRelated(er, incoming, defines.name()).toBlocking().first();
                MetricType mt = convert(mtr, MetricType.class);
                applyPropertiesAndConstruct = ps -> {
                    Long collectionInterval = extractProperty(ps, MappedProperty.COLLECTION_INTERVAL);
                    return new Metric(name, cp, identityHash, contentHash, syncHash, mt, collectionInterval, ps);
                };
                break;
            case mp:
                applyPropertiesAndConstruct = ps -> new MetadataPack(name, cp, ps);
                break;
            case mt:
                applyPropertiesAndConstruct = ps -> {
                    Long collectionInterval = extractProperty(ps, MappedProperty.COLLECTION_INTERVAL);
                    MetricUnit unit = extractProperty(ps, MappedProperty.UNIT);
                    MetricDataType dataType = extractProperty(ps, MappedProperty.METRIC_DATA_TYPE);
                    return new MetricType(name, cp, identityHash, contentHash, syncHash, unit, dataType, ps,
                            collectionInterval);
                };
                break;
            case ot:
                applyPropertiesAndConstruct =
                        ps -> new OperationType(name, cp, identityHash, contentHash, syncHash, ps);
                break;
            case r:
                Row rtr = getRelated(er, incoming, defines.name()).toBlocking().first();
                ResourceType rt = convert(rtr, ResourceType.class);
                applyPropertiesAndConstruct = ps -> new Resource(name, cp, identityHash, contentHash, syncHash, rt, ps);
                break;
            case rt:
                applyPropertiesAndConstruct = ps -> new ResourceType(name, cp, identityHash, contentHash, syncHash, ps);
                break;
            case t:
                applyPropertiesAndConstruct = ps -> new Tenant(name, cp, contentHash, ps);
                break;
            default:
                throw new IllegalArgumentException("Unsupported entity type to convert: " + entityType);
        }

        //TODO handle structured data and shallow structured data
        return (T) applyPropertiesAndConstruct.apply((Map<String, Object>) (Map) props);
    }

    private <T> T extractProperty(Map<String, ?> props, MappedProperty prop) {
        Object val = props.remove(prop.propertyName());
        return prop.fromString(val == null ? null : val.toString());
    }

    @Override public Row descendToData(Row dataEntityRepresentation, RelativePath dataPath) {
        //TODO implement
        return null;
    }

    @Override
    public Row relate(Row sourceEntity, Row targetEntity, String name, Map<String, Object> properties) {
        String sourceCpS = sourceEntity.getString(Statements.CP);
        String targetCpS = targetEntity.getString(Statements.CP);
        CanonicalPath sourceCp = CanonicalPath.fromString(sourceCpS);
        CanonicalPath targetCp = CanonicalPath.fromString(targetCpS);

        Map<String, String> props = properties == null ? Collections.emptyMap() : properties.entrySet().stream()
                .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue().toString()))
                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey,
                        AbstractMap.SimpleImmutableEntry::getValue));

        //the relationship CP is a composition of the sourceCP, name and targetCP
        String cp = getRelationshipCanonicalPath(name, sourceCpS, targetCpS);

        String sourceId = sourceCp.getSegment().getElementId();
        int sourceType = sourceCp.getSegment().getElementType().ordinal();
        String targetId = targetCp.getSegment().getElementId();
        int targetType = targetCp.getSegment().getElementType().ordinal();

        Insert q = insertInto(Statements.RELATIONSHIP).values(
                new String[]{Statements.CP, Statements.SOURCE_CP, Statements.TARGET_CP, Statements.NAME,
                        Statements.PROPERTIES},
                new Object[]{cp, sourceCpS, targetCpS, name, props});

        Insert out = insertInto(Statements.RELATIONSHIP_OUT).values(new String[]{
                        Statements.SOURCE_CP, Statements.TARGET_CP, Statements.CP, Statements.NAME,
                        Statements.PROPERTIES, Statements.SOURCE_ID, Statements.SOURCE_TYPE, Statements.TARGET_ID,
                        Statements.TARGET_TYPE},
                new Object[]{sourceCpS, targetCpS, cp, name, props, sourceId, sourceType, targetId, targetType})
                .ifNotExists();

        Insert in = insertInto(Statements.RELATIONSHIP_IN).values(new String[]{
                        Statements.SOURCE_CP, Statements.TARGET_CP, Statements.CP, Statements.NAME,
                        Statements.PROPERTIES, Statements.SOURCE_ID, Statements.SOURCE_TYPE, Statements.TARGET_ID,
                        Statements.TARGET_TYPE},
                new Object[]{sourceCpS, targetCpS, cp, name, props, sourceId, sourceType, targetId, targetType})
                .ifNotExists();

        //blocking execution of the insert statements
        session.execute(q)
                //follow the successful addition into the main table by concurrent updates of the in/out companions
                .concatWith(session.execute(out).mergeWith(session.execute(in)))
                //force all statements to execute
                .toList()
                //and get the result
                .toBlocking().first().get(0).one();
        //but return the inserted row - which is not what the insert statement returns
        return GeneratedRow.ofRelationship(keyspace, cp, name, sourceCpS, targetCpS, props);
    }

    private String getRelationshipCanonicalPath(String name, String sourceCP, String targetCP) {
        return CanonicalPath.of().relationship(
                PathSegmentCodec.encode(sourceCP) + ";" + name + ";" + PathSegmentCodec.encode(targetCP)).get()
                .toString();
    }

    @Override public Row persist(CanonicalPath path, Blueprint blueprint) {
        return blueprint.accept(new ElementBlueprintVisitor.Simple<Row, Void>() {
            @Override protected Row defaultAction(Object bl, Void parameter) {
                return common((Entity.Blueprint) bl, ((Entity.Blueprint) bl).getName());
            }

            private Row common(AbstractElement.Blueprint bl, String name) {
                Map<String, String> props = bl.getProperties() == null
                        ? new HashMap<>(2)
                        : bl.getProperties().entrySet().stream()
                        .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue().toString()))
                        .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey,
                                AbstractMap.SimpleImmutableEntry::getValue));

                bl.accept(new ElementBlueprintVisitor.Simple<Void, Void>() {
                    @Override public Void visitMetric(Metric.Blueprint metric, Void parameter) {
                        addToPropertiesIfNotNull(props, metric.getCollectionInterval(),
                                MappedProperty.COLLECTION_INTERVAL);
                        return null;
                    }

                    @Override public Void visitMetricType(MetricType.Blueprint type, Void parameter) {
                        addToPropertiesIfNotNull(props, type.getCollectionInterval(),
                                MappedProperty.COLLECTION_INTERVAL);
                        addToPropertiesIfNotNull(props, type.getMetricDataType(), MappedProperty.METRIC_DATA_TYPE);
                        addToPropertiesIfNotNull(props, type.getUnit(), MappedProperty.UNIT);
                        return null;
                    }
                }, null);

                Insert q = insertInto(Statements.ENTITY).values(new String[]{
                                Statements.CP,
                                Statements.ID,
                                Statements.TYPE,
                                Statements.NAME,
                                Statements.PROPERTIES},
                        new Object[]{
                                path.toString(),
                                path.getSegment().getElementId(),
                                path.getSegment().getElementType().ordinal(),
                                name,
                                props}).ifNotExists();

                Insert id_idx = insertInto(Statements.ENTITY_ID_IDX).values(
                        new String[]{Statements.ID, Statements.CP},
                        new Object[]{path.getSegment().getElementId(), path.toString()});

                Insert type_idx = insertInto(Statements.ENTITY_TYPE_IDX).values(
                        new String[]{Statements.TYPE, Statements.CP},
                        new Object[]{path.getSegment().getElementType().ordinal(), path.toString()});

                Observable<ResultSet> indices = session.execute(id_idx).mergeWith(session.execute(type_idx));
                if (name != null) {
                    Insert name_idx = insertInto(Statements.ENTITY_NAME_IDX).values(
                            new String[]{Statements.NAME, Statements.CP},
                            new Object[]{name, path.toString()});
                    indices = indices.mergeWith(session.execute(name_idx));
                }

                //blocking execution of the insert...
                session.execute(q)
                        //follow the successful addition into the main table by concurrent updates of the in/out companions
                        .concatWith(indices)
                        //force all statements to execute
                        .toList()
                        //and get the result
                        .toBlocking().first().get(0).one();
                //but return the inserted row - which is not what the insert statement returns
                return GeneratedRow.ofEntity(keyspace, path.toString(), path.getSegment().getElementId(),
                        path.getSegment().getElementType().ordinal(), name, props);
            }

            @Override public Row visitRelationship(Relationship.Blueprint relationship, Void parameter) {
                throw new IllegalArgumentException("Relationships cannot be persisted using the persist() method.");
            }

            @Override public Row visitMetadataPack(MetadataPack.Blueprint metadataPack, Void parameter) {
                return common(metadataPack, metadataPack.getName());
            }
        }, null);
    }

    private void addToPropertiesIfNotNull(Map<String, String> properties, Object value, MappedProperty property) {
        if (value == null) {
            return;
        }

        properties.put(property.propertyName(), property.toString(value));
    }

    @Override public Row persist(StructuredData structuredData) {
        //TODO implement
        return null;
    }

    @Override public void update(Row entity, AbstractElement.Update update) {
        //TODO implement

    }

    @Override public void updateHashes(Row entity, Hashes hashes) {
        //TODO implement

    }

    @Override public void delete(Row entity) {
        //TODO implement

    }

    @Override public void deleteStructuredData(Row dataRepresentation) {
        //TODO implement

    }

    @Override public void commit() throws CommitFailureException {
        //TODO implement

    }

    @Override public void rollback() {
        //TODO implement

    }

    @Override public boolean isBackendInternal(Row element) {
        //TODO implement
        return false;
    }

    @Override public InputStream getGraphSON(String tenantId) {
        //TODO implement
        return null;
    }

    @Override public <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(CanonicalPath startingPoint,
                                                                                   Relationships.Direction direction,
                                                                                   Class<T> clazz,
                                                                                   String... relationshipNames) {
        //TODO implement
        return null;
    }

    @Override public void close() throws Exception {
        session.close();
    }

    private StructuredData loadStructuredData(Row parent, String relationship) {
        //TODO implement
        return null;
    }
}
