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
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Clause;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class CassandraBackend implements InventoryBackend<Row> {
    private static final String RELATIONSHIP_OUT = "relationship_out";
    private static final String RELATIONSHIP_IN = "relationship_in";
    private static final String ENTITY = "entity";
    private static final String CP = "cp";
    private static final String SOURCE_CP = "source_cp";
    private static final String TARGET_CP = "target_cp";
    private static final String NAME = "name";
    private static final String PROPERTIES = "properties";

    private final Session session;

    public CassandraBackend(Session session) {
        this.session = session;
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
        Select select;
        String cp = element.toString();
        if (element.getSegment().getElementType() == SegmentType.rl) {
            select = select().all().from(RELATIONSHIP_OUT);
        } else {
            select = select().all().from(ENTITY);
        }

        //TODO this doesn't work for relationships - they don't have a CP in database, because it is meant to be
        //inferred from source_cp and target_cp...
        ResultSet results = session.execute(select.where(eq(CP, cp)));
        return results.one();
    }

    @Override public Page<Row> query(Query query, Pager pager) {
        //TODO implement
        return null;
    }

    @Override public Row querySingle(Query query) {
        //TODO implement
        return null;
    }

    @Override public Page<Row> traverse(Row startingPoint, Query query, Pager pager) {
        //TODO implement
        return null;
    }

    @Override public Row traverseToSingle(Row startingPoint, Query query) {
        //TODO implement
        return null;
    }

    @Override
    public <T> Page<T> query(Query query, Pager pager, Function<Row, T> conversion, Function<T, Boolean> filter) {
        //TODO implement
        return null;
    }

    @Override
    public Iterator<Row> getTransitiveClosureOver(Row startingPoint, Relationships.Direction direction,
                                                       String... relationshipNames) {
        //TODO implement
        return null;
    }

    @Override
    public boolean hasRelationship(Row entity, Relationships.Direction direction, String relationshipName) {
        String cp = entity.getString(CP);
        Clause cond;
        String table;
        switch (direction) {
            case incoming:
                cond = eq(TARGET_CP, cp);
                table = RELATIONSHIP_IN;
                break;
            case outgoing:
                cond = eq(SOURCE_CP, cp);
                table = RELATIONSHIP_OUT;
                break;
            case both:
                return hasRelationship(entity, outgoing, relationshipName)
                        || hasRelationship(entity, incoming, relationshipName);
            default:
                throw new IllegalStateException("Unsupported direction: " + direction);
        }

        Statement q = select().countAll().from(table).where(cond).and(eq(NAME, relationshipName));
        ResultSet rs = session.execute(q);
        rs.one().getLong(0);
        return false;
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
        String sourceCp = sourceEntity.getString(CP);
        String targetCp = targetEntity.getString(CP);
        Map<String, String> props = properties == null ? Collections.emptyMap() : properties.entrySet().stream()
                .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue().toString()))
                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey,
                        AbstractMap.SimpleImmutableEntry::getValue));

        Insert q = insertInto(RELATIONSHIP_OUT).values(new String[]{SOURCE_CP, TARGET_CP, NAME, PROPERTIES},
                new Object[]{sourceCp, targetCp, name, props}).ifNotExists();

        ResultSet rs = session.execute(q);
        return rs.one();
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

                Insert q = insertInto(ENTITY).values(new String[] {CP, NAME, PROPERTIES},
                        new Object[] {path, name, props}).ifNotExists();

                ResultSet rs = session.execute(q);
                return rs.one();
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
