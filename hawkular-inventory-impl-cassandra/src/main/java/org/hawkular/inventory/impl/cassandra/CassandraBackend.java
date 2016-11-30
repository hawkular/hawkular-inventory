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

import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import org.hawkular.inventory.api.model.MetricType;
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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Clause;
import com.datastax.driver.core.querybuilder.Insert;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class CassandraBackend implements InventoryBackend<Row> {
    private static final Logger DBG = Logger.getLogger(CassandraBackend.class);

    private final RxSession session;
    private final Statements statements;
    private final QueryExecutor queryExecutor;

    public CassandraBackend(RxSession session) {
        this.session = session;
        this.statements = new Statements(session);
        this.queryExecutor = new QueryExecutor(session, statements);
    }

    @Override public boolean isPreferringBigTransactions() {
        return false;
    }

    @Override public boolean isUniqueIndexSupported() {
        return false;
    }

    @Override public InventoryBackend<Row> startTransaction() {
        return this;
    }

    @Override public Row find(CanonicalPath element) throws ElementNotFoundException {
        PreparedStatement select;
        String cp = element.toString();
        if (element.getSegment().getElementType() == SegmentType.rl) {
            select = statements.findRelationshipByCanonicalPath();
        } else {
            select = statements.findEntityByCanonicalPath();
        }

        DBG.debugf("Executing find of %s", element);

        //TODO this doesn't work for relationships - they don't have a CP in database, because it is meant to be
        //inferred from source_cp and target_cp...
        return session.execute(select.bind(cp)).toBlocking().first().one();
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

        Observable<Row> qrs = queryExecutor.execute(query);
        Observable<T> rs = qrs.map(conversion::apply).filter(filter::apply);
        return new Page<>(rs.toBlocking().getIterator(), pager, -1);
    }

    @Override
    public Iterator<Row> getTransitiveClosureOver(Row startingPoint, Relationships.Direction direction,
                                                       String... relationshipNames) {
        //TODO implement
        return null;
    }

    @Override
    public boolean hasRelationship(Row entity, Relationships.Direction direction, String relationshipName) {
        String cp = entity.getString(Statements.CP);
        Clause cond;
        String table;
        switch (direction) {
            case incoming:
                cond = eq(Statements.TARGET_CP, cp);
                table = Statements.RELATIONSHIP_IN;
                break;
            case outgoing:
                cond = eq(Statements.SOURCE_CP, cp);
                table = Statements.RELATIONSHIP_OUT;
                break;
            case both:
                return hasRelationship(entity, outgoing, relationshipName)
                        || hasRelationship(entity, incoming, relationshipName);
            default:
                throw new IllegalStateException("Unsupported direction: " + direction);
        }

        Statement q = select().countAll().from(table).where(cond).and(eq(Statements.NAME, relationshipName));
        return session.execute(q).count().toBlocking().first() > 0;
    }

    @Override public boolean hasRelationship(Row source, Row target, String relationshipName) {
        //TODO implement
        return false;
    }

    @Override
    public Set<Row> getRelationships(Row entity, Relationships.Direction direction, String... names) {
        //TODO implement
        return null;
    }

    @Override public Row getRelationship(Row source, Row target, String relationshipName)
            throws ElementNotFoundException {
        //TODO implement
        return null;
    }

    @Override public Row getRelationshipSource(Row relationship) {
        //TODO implement
        return null;
    }

    @Override public Row getRelationshipTarget(Row relationship) {
        //TODO implement
        return null;
    }

    @Override public String extractRelationshipName(Row relationship) {
        //TODO implement
        return null;
    }

    @Override public String extractId(Row entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public Class<?> extractType(Row entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public CanonicalPath extractCanonicalPath(Row entityRepresentation) {
        //TODO implement
        return null;
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

    @Override public <T> T convert(Row entityRepresentation, Class<T> entityType) {
        //TODO implement
        return null;
    }

    @Override public Row descendToData(Row dataEntityRepresentation, RelativePath dataPath) {
        //TODO implement
        return null;
    }

    @Override
    public Row relate(Row sourceEntity, Row targetEntity, String name, Map<String, Object> properties) {
        String sourceCp = sourceEntity.getString(Statements.CP);
        String targetCp = targetEntity.getString(Statements.CP);
        Map<String, String> props = properties == null ? Collections.emptyMap() : properties.entrySet().stream()
                .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue().toString()))
                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey,
                        AbstractMap.SimpleImmutableEntry::getValue));

        //the relationship CP is a composition of the sourceCP, name and targetCP
        CanonicalPath cp = CanonicalPath.of().relationship(
                PathSegmentCodec.encode(sourceCp) + ";" + name + ";" + PathSegmentCodec.encode(targetCp)).get();

        Insert q = insertInto(Statements.RELATIONSHIP).values(
                new String[]{Statements.CP, Statements.SOURCE_CP, Statements.TARGET_CP, Statements.NAME,
                        Statements.PROPERTIES},
                new Object[]{cp, sourceCp, targetCp, name, props});

        Insert out = insertInto(Statements.RELATIONSHIP_OUT).values(new String[]{
                        Statements.SOURCE_CP, Statements.TARGET_CP, Statements.CP, Statements.NAME,
                        Statements.PROPERTIES},
                new Object[]{sourceCp, targetCp, cp, name, props}).ifNotExists();

        Insert in = insertInto(Statements.RELATIONSHIP_IN).values(new String[]{
                        Statements.SOURCE_CP, Statements.TARGET_CP, Statements.CP, Statements.NAME,
                        Statements.PROPERTIES},
                new Object[]{sourceCp, targetCp, cp, name, props}).ifNotExists();

        return session.execute(q)
                //follow the successful addition into the main table by concurrent updates of the in/out companions
                .concatWith(session.execute(out).mergeWith(session.execute(in)))
                //force all statements to execute
                .toList()
                //and get the result
                .toBlocking().first().get(0).one();
    }

    @Override public Row persist(CanonicalPath path, Blueprint blueprint) {
        return blueprint.accept(new ElementBlueprintVisitor.Simple<Row, Void>() {
            private Row common(Entity.Blueprint bl) {
                return common(bl, bl.getName());
            }

            private Row common(AbstractElement.Blueprint bl, String name) {
                Map<String, String> props = bl.getProperties() == null
                        ? Collections.emptyMap()
                        : bl.getProperties().entrySet().stream()
                        .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue().toString()))
                        .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey,
                                AbstractMap.SimpleImmutableEntry::getValue));

                Insert q = insertInto(Statements.ENTITY).values(new String[] {
                                Statements.CP, Statements.ID, Statements.NAME, Statements.PROPERTIES},
                        new Object[] {path.toString(), name, props}).ifNotExists();

                Insert id_idx = insertInto(Statements.ENTITY_ID_IDX).values(
                        new String[] {Statements.ID, Statements.CP},
                        new Object[] {path.getSegment().getElementId(), path.toString()});

                Insert type_idx = insertInto(Statements.ENTITY_TYPE_IDX).values(
                        new String[] {Statements.TYPE, Statements.CP},
                        new Object[] {path.getSegment().getElementType().ordinal(), path.toString()});

                Observable<ResultSet> indices = session.execute(id_idx).mergeWith(session.execute(type_idx));
                if (name != null) {
                    Insert name_idx = insertInto(Statements.ENTITY_NAME_IDX).values(
                            new String[] {Statements.NAME, Statements.CP},
                            new Object[] {name, path.toString()});
                    indices = indices.mergeWith(session.execute(name_idx));
                }

                return session.execute(q)
                        //follow the successful addition into the main table by concurrent updates of the in/out companions
                        .concatWith(indices)
                        //force all statements to execute
                        .toList()
                        //and get the result
                        .toBlocking().first().get(0).one();
            }

            @Override public Row visitTenant(Tenant.Blueprint tenant, Void parameter) {
                return common(tenant);
            }

            @Override public Row visitEnvironment(Environment.Blueprint environment, Void parameter) {
                return common(environment);
            }

            @Override public Row visitFeed(Feed.Blueprint feed, Void parameter) {
                return common(feed);
            }

            @Override public Row visitMetric(Metric.Blueprint metric, Void parameter) {
                return common(metric);
            }

            @Override public Row visitMetricType(MetricType.Blueprint type, Void parameter) {
                return common(type);
            }

            @Override public Row visitResource(Resource.Blueprint resource, Void parameter) {
                return common(resource);
            }

            @Override public Row visitResourceType(ResourceType.Blueprint type, Void parameter) {
                return common(type);
            }

            @Override public Row visitRelationship(Relationship.Blueprint relationship, Void parameter) {
                throw new IllegalArgumentException("Relationships cannot be persisted using the persist() method.");
            }

            @Override public Row visitData(DataEntity.Blueprint<?> data, Void parameter) {
                return common(data);
            }

            @Override public Row visitOperationType(OperationType.Blueprint operationType, Void parameter) {
                return common(operationType);
            }

            @Override public Row visitMetadataPack(MetadataPack.Blueprint metadataPack, Void parameter) {
                return common(metadataPack, metadataPack.getName());
            }
        }, null);
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
        //TODO implement

    }
}
