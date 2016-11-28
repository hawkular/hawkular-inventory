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

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Hashes;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class CassandraBackend implements InventoryBackend<CElement> {
    @Override public boolean isPreferringBigTransactions() {
        return false;
    }

    @Override public boolean isUniqueIndexSupported() {
        return false;
    }

    @Override public InventoryBackend<CElement> startTransaction() {
        //TODO implement
        return null;
    }

    @Override public CElement find(CanonicalPath element) throws ElementNotFoundException {
        //TODO implement
        return null;
    }

    @Override public Page<CElement> query(Query query, Pager pager) {
        //TODO implement
        return null;
    }

    @Override public CElement querySingle(Query query) {
        //TODO implement
        return null;
    }

    @Override public Page<CElement> traverse(CElement startingPoint, Query query, Pager pager) {
        //TODO implement
        return null;
    }

    @Override public CElement traverseToSingle(CElement startingPoint, Query query) {
        //TODO implement
        return null;
    }

    @Override
    public <T> Page<T> query(Query query, Pager pager, Function<CElement, T> conversion, Function<T, Boolean> filter) {
        //TODO implement
        return null;
    }

    @Override
    public Iterator<CElement> getTransitiveClosureOver(CElement startingPoint, Relationships.Direction direction,
                                                       String... relationshipNames) {
        //TODO implement
        return null;
    }

    @Override
    public boolean hasRelationship(CElement entity, Relationships.Direction direction, String relationshipName) {
        //TODO implement
        return false;
    }

    @Override public boolean hasRelationship(CElement source, CElement target, String relationshipName) {
        //TODO implement
        return false;
    }

    @Override
    public Set<CElement> getRelationships(CElement entity, Relationships.Direction direction, String... names) {
        //TODO implement
        return null;
    }

    @Override public CElement getRelationship(CElement source, CElement target, String relationshipName)
            throws ElementNotFoundException {
        //TODO implement
        return null;
    }

    @Override public CElement getRelationshipSource(CElement relationship) {
        //TODO implement
        return null;
    }

    @Override public CElement getRelationshipTarget(CElement relationship) {
        //TODO implement
        return null;
    }

    @Override public String extractRelationshipName(CElement relationship) {
        //TODO implement
        return null;
    }

    @Override public String extractId(CElement entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public Class<?> extractType(CElement entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public CanonicalPath extractCanonicalPath(CElement entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public String extractIdentityHash(CElement entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public String extractContentHash(CElement entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public String extractSyncHash(CElement entityRepresentation) {
        //TODO implement
        return null;
    }

    @Override public <T> T convert(CElement entityRepresentation, Class<T> entityType) {
        //TODO implement
        return null;
    }

    @Override public CElement descendToData(CElement dataEntityRepresentation, RelativePath dataPath) {
        //TODO implement
        return null;
    }

    @Override
    public CElement relate(CElement sourceEntity, CElement targetEntity, String name, Map<String, Object> properties) {
        //TODO implement
        return null;
    }

    @Override public CElement persist(CanonicalPath path, Blueprint blueprint) {
        //TODO implement
        return null;
    }

    @Override public CElement persist(StructuredData structuredData) {
        //TODO implement
        return null;
    }

    @Override public void update(CElement entity, AbstractElement.Update update) {
        //TODO implement

    }

    @Override public void updateHashes(CElement entity, Hashes hashes) {
        //TODO implement

    }

    @Override public void delete(CElement entity) {
        //TODO implement

    }

    @Override public void deleteStructuredData(CElement dataRepresentation) {
        //TODO implement

    }

    @Override public void commit() throws CommitFailureException {
        //TODO implement

    }

    @Override public void rollback() {
        //TODO implement

    }

    @Override public boolean isBackendInternal(CElement element) {
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
