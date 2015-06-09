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
package org.hawkular.inventory.lazy.spi;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.QueryFragmentTree;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The backend for the lazy inventory that does all the "low level" stuff like querying the actual inventory store,
 * its modifications, etc.
 *
 * @param <E> the type of the backend-specific objects representing the inventory entities. It is assumed that the
 *            backend is "untyped" and stores all different inventory entities using this single type.
 * @author Lukas Krejci
 * @since 0.0.6
 */
public interface LazyInventoryBackend<E> extends AutoCloseable {

    E find(CanonicalPath element);

    Page<E> query(QueryFragmentTree query, Pager pager);

    <T extends AbstractElement<?, ?>> Page<T> query(QueryFragmentTree query, Pager pager, Function<E, T> conversion,
            Function<T, Boolean> filter);

    Iterator<E> getTransitiveClosureOver(E startingPoint, String relationshipName, Relationships.Direction direction);

    boolean hasRelationship(E entity, Relationships.Direction direction, String relationshipName);

    boolean hasRelationship(E source, E target, String relationshipName);

    Set<E> getRelationships(E source, Relationships.Direction direction, String... names);

    E getRelationship(E source, E target, String relationshipName);

    String extractId(E entityRepresentation);

    String extractRelationshipName(E relationshipRepresentation);

    Map<String, Object> extractProperties(E entityRepresentation);

    Class<? extends AbstractElement<?, ?>> getType(E entityRepresentation);

    <T extends AbstractElement<?, ?>> T convert(E entityRepresentation, Class<T> entityType);

    E relate(E sourceEntity, E targetEntity, String label, Map<String, Object> properties);

    E persist(String id, AbstractElement.Blueprint entity);

    void update(E entity, AbstractElement.Update update);

    void delete(E entity);

    void commit();

    void rollback();
}
