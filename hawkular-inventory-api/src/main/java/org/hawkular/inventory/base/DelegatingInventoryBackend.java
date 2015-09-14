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
package org.hawkular.inventory.base;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * Simple delegating inventory backend impl. Useful as a base class for implementations that need to override behavior
 * of another.
 *
 * @author Lukas Krejci
 * @since 0.4.0
 */
public class DelegatingInventoryBackend<E> implements InventoryBackend<E> {

    private final InventoryBackend<E> backend;

    public DelegatingInventoryBackend(InventoryBackend<E> backend) {
        this.backend = backend;
    }

    @Override

    public void commit(Transaction transaction) throws CommitFailureException {
        backend.commit(transaction);
    }

    @Override

    public <T> T convert(E entityRepresentation, Class<T> entityType) {
        return backend.convert(entityRepresentation, entityType);
    }

    @Override

    public void delete(E entity) {
        backend.delete(entity);
    }

    @Override

    public void deleteStructuredData(E dataRepresentation) {
        backend.deleteStructuredData(dataRepresentation);
    }

    @Override

    public E descendToData(E dataEntityRepresentation, RelativePath dataPath) {
        return backend.descendToData(dataEntityRepresentation, dataPath);
    }

    @Override

    public CanonicalPath extractCanonicalPath(E entityRepresentation) {
        return backend.extractCanonicalPath(entityRepresentation);
    }

    @Override

    public String extractId(E entityRepresentation) {
        return backend.extractId(entityRepresentation);
    }

    @Override

    public String extractRelationshipName(E relationship) {
        return backend.extractRelationshipName(relationship);
    }

    @Override

    public Class<?> extractType(E entityRepresentation) {
        return backend.extractType(entityRepresentation);
    }

    @Override

    public E find(CanonicalPath element) throws ElementNotFoundException {
        return backend.find(element);
    }

    @Override

    public InputStream getGraphSON(String tenantId) {
        return backend.getGraphSON(tenantId);
    }

    @Override
    public E getRelationship(E source, E target, String relationshipName) throws ElementNotFoundException {
        return backend.getRelationship(source, target, relationshipName);
    }

    @Override
    public Set<E> getRelationships(E entity, Relationships.Direction direction,
                                   String... names) {
        return backend.getRelationships(entity, direction, names);
    }

    @Override
    public E getRelationshipSource(E relationship) {
        return backend.getRelationshipSource(relationship);
    }

    @Override
    public E getRelationshipTarget(E relationship) {
        return backend.getRelationshipTarget(relationship);
    }

    @Override
    public <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(
            CanonicalPath startingPoint,
            Relationships.Direction direction, Class<T> clazz,
            String... relationshipNames) {
        return backend.getTransitiveClosureOver(startingPoint, direction, clazz, relationshipNames);
    }

    @Override
    public Iterator<E> getTransitiveClosureOver(E startingPoint,
                                                Relationships.Direction direction,
                                                String... relationshipNames) {
        return backend.getTransitiveClosureOver(startingPoint, direction, relationshipNames);
    }

    @Override
    public boolean hasRelationship(E entity, Relationships.Direction direction,
                                   String relationshipName) {
        return backend.hasRelationship(entity, direction, relationshipName);
    }

    @Override
    public boolean hasRelationship(E source, E target, String relationshipName) {
        return backend.hasRelationship(source, target, relationshipName);
    }

    @Override
    public E persist(CanonicalPath path,
                     Blueprint blueprint) {
        return backend.persist(path, blueprint);
    }

    @Override
    public E persist(StructuredData structuredData) {
        return backend.persist(structuredData);
    }

    @Override
    public Page<E> query(Query query, Pager pager) {
        return backend.query(query, pager);
    }

    @Override
    public <T> Page<T> query(Query query, Pager pager,
                             Function<E, T> conversion,
                             Function<T, Boolean> filter) {
        return backend.query(query, pager, conversion, filter);
    }

    @Override
    public E relate(E sourceEntity, E targetEntity, String name,
                    Map<String, Object> properties) {
        return backend.relate(sourceEntity, targetEntity, name, properties);
    }

    @Override
    public void rollback(Transaction transaction) {
        backend.rollback(transaction);
    }

    @Override
    public Transaction startTransaction(boolean mutating) {
        return backend.startTransaction(mutating);
    }

    @Override
    public Page<E> traverse(E startingPoint, Query query,
                            Pager pager) {
        return backend.traverse(startingPoint, query, pager);
    }

    @Override
    public void update(E entity, AbstractElement.Update update) {
        backend.update(entity, update);
    }

    @Override
    public void close() throws Exception {
        backend.close();
    }
}
