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
package org.hawkular.inventory.base;

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
import org.hawkular.inventory.base.spi.Discriminator;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
public class DelegatingTransaction<E> implements Transaction<E> {
    private final Transaction<E> tx;

    public DelegatingTransaction(Transaction<E> tx) {
        this.tx = tx;
    }

    @Override public <T> T convert(Discriminator discriminator, E entityRepresentation, Class<T> entityType) {
        return tx.convert(discriminator, entityRepresentation, entityType);
    }

    @Override public void markDeleted(Discriminator discriminator, E entity) {
        tx.markDeleted(discriminator, entity);
    }

    @Override public void deleteStructuredData(E dataRepresentation) {
        tx.deleteStructuredData(dataRepresentation);
    }

    @Override public E descendToData(Discriminator discriminator, E dataEntityRepresentation, RelativePath dataPath) {
        return tx.descendToData(discriminator, dataEntityRepresentation, dataPath);
    }

    @Override public InventoryBackend<E> directAccess() {
        return tx.directAccess();
    }

    @Override public void eradicate(E entityRepresentation) {
        tx.eradicate(entityRepresentation);
    }

    @Override public CanonicalPath extractCanonicalPath(E entityRepresentation) {
        return tx.extractCanonicalPath(entityRepresentation);
    }

    @Override public String extractId(E entityRepresentation) {
        return tx.extractId(entityRepresentation);
    }

    @Override public String extractIdentityHash(Discriminator discriminator, E entityRepresentation) {
        return tx.extractIdentityHash(discriminator, entityRepresentation);
    }

    @Override public String extractContentHash(Discriminator discriminator, E entityRepresentation) {
        return tx.extractContentHash(discriminator, entityRepresentation);
    }

    @Override public String extractSyncHash(Discriminator discriminator, E entityRepresentation) {
        return tx.extractSyncHash(discriminator, entityRepresentation);
    }

    @Override public String extractRelationshipName(E relationship) {
        return tx.extractRelationshipName(relationship);
    }

    @Override public Class<?> extractType(E entityRepresentation) {
        return tx.extractType(entityRepresentation);
    }

    @Override public E find(Discriminator discriminator, CanonicalPath element) throws ElementNotFoundException {
        return tx.find(discriminator, element);
    }

    @Override public InputStream getGraphSON(Discriminator discriminator, String tenantId) {
        return tx.getGraphSON(discriminator, tenantId);
    }

    @Override public PreCommit<E> getPreCommit() {
        return tx.getPreCommit();
    }

    @Override public E getRelationship(Discriminator discriminator, E source, E target, String relationshipName) throws ElementNotFoundException {
        return tx.getRelationship(discriminator, source, target, relationshipName);
    }

    @Override public Set<E> getRelationships(Discriminator discriminator, E entity, Relationships.Direction direction,
                                             String... names) {
        return tx.getRelationships(discriminator, entity, direction, names);
    }

    @Override public E getRelationshipSource(Discriminator discriminator, E relationship) {
        return tx.getRelationshipSource(discriminator, relationship);
    }

    @Override public E getRelationshipTarget(Discriminator discriminator, E relationship) {
        return tx.getRelationshipTarget(discriminator, relationship);
    }

    @Override public <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(
            Discriminator discriminator, CanonicalPath startingPoint,
            Relationships.Direction direction, Class<T> clazz,
            String... relationshipNames) {
        return tx.getTransitiveClosureOver(discriminator, startingPoint, direction, clazz, relationshipNames);
    }

    @Override public Iterator<E> getTransitiveClosureOver(Discriminator discriminator, E startingPoint,
                                                          Relationships.Direction direction,
                                                          String... relationshipNames) {
        return tx.getTransitiveClosureOver(discriminator, startingPoint, direction, relationshipNames);
    }

    @Override public boolean hasRelationship(Discriminator discriminator, E entity, Relationships.Direction direction,
                                             String relationshipName) {
        return tx.hasRelationship(discriminator, entity, direction, relationshipName);
    }

    @Override public boolean hasRelationship(Discriminator discriminator, E source, E target, String relationshipName) {
        return tx.hasRelationship(discriminator, source, target, relationshipName);
    }

    @Override public boolean isBackendInternal(E element) {
        return tx.isBackendInternal(element);
    }

    @Override public boolean isUniqueIndexSupported() {
        return tx.isUniqueIndexSupported();
    }

    @Override public E persist(CanonicalPath path,
                               Blueprint blueprint) {
        return tx.persist(path, blueprint);
    }

    @Override public E persist(StructuredData structuredData) {
        return tx.persist(structuredData);
    }

    @Override public Page<E> query(Discriminator discriminator, Query query,
                                   Pager pager) {
        return tx.query(discriminator, query, pager);
    }

    @Override public <T> Page<T> query(Discriminator discriminator, Query query,
                                       Pager pager,
                                       Function<E, T> conversion,
                                       Function<T, Boolean> filter) {
        return tx.query(discriminator, query, pager, conversion, filter);
    }

    @Override public E querySingle(Discriminator discriminator, Query query) {
        return tx.querySingle(discriminator, query);
    }

    @Override public void registerCommittedPayload(TransactionPayload.Committing<?, E> committedPayload) {
        tx.registerCommittedPayload(committedPayload);
    }

    @Override public E relate(Discriminator discriminator, E sourceEntity, E targetEntity, String name,
                              Map<String, Object> properties) {
        return tx.relate(discriminator, sourceEntity, targetEntity, name, properties);
    }

    @Override public Page<E> traverse(Discriminator discriminator, E startingPoint, Query query,
                                      Pager pager) {
        return tx.traverse(discriminator, startingPoint, query, pager);
    }

    @Override public E traverseToSingle(Discriminator discriminator, E startingPoint, Query query) {
        return tx.traverseToSingle(discriminator, startingPoint, query);
    }

    @Override public void update(Discriminator discriminator, E entity, AbstractElement.Update update) {
        tx.update(discriminator, entity, update);
    }

    @Override public void updateHashes(Discriminator discriminator, E entity, Hashes hashes) {
        tx.updateHashes(discriminator, entity, hashes);
    }

    @Override public boolean requiresRollbackAfterFailure(Throwable t) {
        return tx.requiresRollbackAfterFailure(t);
    }
}
