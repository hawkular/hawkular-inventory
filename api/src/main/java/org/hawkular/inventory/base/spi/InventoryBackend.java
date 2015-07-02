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
package org.hawkular.inventory.base.spi;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.Query;

/**
 * The backend for the base inventory that does all the "low level" stuff like querying the actual inventory store,
 * its modifications, etc.
 *
 * @param <E> the type of the backend-specific objects representing the inventory entities and relationships. It is
 *            assumed that the backend is "untyped" and stores all different inventory entities using this single type.
 * @author Lukas Krejci
 * @since 0.1.0
 */
public interface InventoryBackend<E> extends AutoCloseable {

    /**
     * Starts a transaction in the backend.
     *
     * @param mutating whether there will be calls mutating the data or not
     * @return the newly started transaction
     */
    Transaction startTransaction(boolean mutating);

    /**
     * Tries to find an element at given canonical path.
     *
     * @param element the canonical path of the element to find
     * @return the element
     * @throws ElementNotFoundException if the element is not found
     */
    E find(CanonicalPath element) throws ElementNotFoundException;

    /**
     * Translates the query to the backend-specific representation and runs it, returning a correct page of results
     * as prescribed by the provided pager object.
     *
     * @param query the query to execute
     * @param pager the page to return
     * @return a page of results corresponding to the parameters, possibly empty, never null.
     */
    Page<E> query(Query query, Pager pager);

    /**
     * A variant of the {@link #query(Query, Pager)} method which in addition to querying also converts the results
     * using the provided conversion function and, more importantly, filters the results using the provided (possibly
     * null) filter function PRIOR TO paging is applied.
     *
     * <p>Because the total count and the paging is dependent on the filtering it needs to be applied during the
     * querying process and not only after the fact be the caller.
     *
     * @param query      the query to perform
     * @param pager      the page to retrieve
     * @param conversion a conversion function to apply on the elements, never null
     * @param filter     possibly null filter to filter the results with
     * @param <T>        the type of the returned elements
     * @return the page of results according to the supplied parameters
     */
    <T extends AbstractElement<?, ?>> Page<T> query(Query query, Pager pager, Function<E, T> conversion,
            Function<T, Boolean> filter);

    /**
     * Going from the starting poing, this will return an iterator over all elements that are connected to the starting
     * point using relationships with provided name and recursively down to the elements connected in the same way to
     * them.
     *
     * @param startingPoint    the starting element
     * @param relationshipName the name of the relationship to follow when composing the transitive closure
     * @param direction        any of the valid directions including
     *                         {@link org.hawkular.inventory.api.Relationships.Direction#both}.
     * @return an iterator over the transitive closure, may be "lazy" and evaluate the closure on demand.
     */
    Iterator<E> getTransitiveClosureOver(E startingPoint, String relationshipName, Relationships.Direction direction);

    /**
     * Checks whether there exists any relationship in given direction relative to the given entity with given name.
     *
     * @param entity           the entity in question
     * @param direction        the direction the relationship should have relative to the entity (
     *                         {@link org.hawkular.inventory.api.Relationships.Direction#both} means "any" in this
     *                         context).
     * @param relationshipName the name of the relationship to seek
     * @return true if there is such relationship, false otherwise
     * @see #getRelationships(Object, Relationships.Direction, String...)
     */
    boolean hasRelationship(E entity, Relationships.Direction direction, String relationshipName);

    /**
     * Checks whether there exists a relationship with given name between the provided entities.
     *
     * @param source           the source of the relationship
     * @param target           the target of the relationship
     * @param relationshipName the name of the relationship
     * @return true, if such relationship exists, false otherwise
     */
    boolean hasRelationship(E source, E target, String relationshipName);

    /**
     * Similar to {@link #hasRelationship(Object, Relationships.Direction, String)} but this method actually returns
     * the relationship objects.
     *
     * @param entity    the entity in question
     * @param direction the direction in which the relationships should be going
     * @param names     the names of the relationships to return
     * @return the possibly empty set of the relationships, never null
     * @see #hasRelationship(Object, Relationships.Direction, String)
     */
    Set<E> getRelationships(E entity, Relationships.Direction direction, String... names);

    /**
     * Get a single relationship with the provided name between the source and target.
     *
     * @param source           the source of the relationship
     * @param target           the target of the relationship
     * @param relationshipName the name of the relationship
     * @return the relationship
     * @throws ElementNotFoundException if the relationship is not found
     * @throws IllegalArgumentException if source or target are not entities or relationship name is null
     */
    E getRelationship(E source, E target, String relationshipName) throws ElementNotFoundException;

    /**
     * @param relationship the relationship in question
     * @return the source of the relationship
     */
    E getRelationshipSource(E relationship);

    /**
     * @param relationship the relationship in question
     * @return the target of the relationship
     */
    E getRelationshipTarget(E relationship);

    /**
     * @param relationship the relationship in question
     * @return the name of the relationship
     */
    String extractRelationshipName(E relationship);

    /**
     * The element type is opaque from the point of the caller. This method provides the caller with the ability to
     * extract the ID of the entity represented by the object.
     *
     * @param entityRepresentation the object representing an element
     * @return the ID
     */
    String extractId(E entityRepresentation);

    /**
     * Similar to {@link #extractId(Object)} but extracts the type of element from the representation.
     *
     * @param entityRepresentation the representation object.
     * @return the type of the object represented
     */
    Class<? extends AbstractElement<?, ?>> extractType(E entityRepresentation);

    /**
     * Each element (including relationships) stores the canonical path to it. This will extract that value from the
     * entity representation.
     *
     * @param entityRepresentation the representation object
     * @return the extracted canonical path
     */
    CanonicalPath extractCanonicalPath(E entityRepresentation);

    /**
     * Converts the provided representation object to a inventory element of provided type.
     *
     * @param entityRepresentation the object representing the element
     * @param entityType           the desired type of the element
     * @param <T>                  the desired type of the element
     * @return the converted invetory element
     * @throws ClassCastException if the representation object doesn't correspond to the provided type
     */
    <T extends AbstractElement<?, ?>> T convert(E entityRepresentation, Class<T> entityType);

    /**
     * Creates a new relationship from source to target with given name and properties.
     *
     * @param sourceEntity the source of the relationship
     * @param targetEntity the target of the relationship
     * @param name         the name of the relationship
     * @param properties   the properties of the relationship, may be null
     * @return the representation of the newly created relationship
     * @throws IllegalArgumentException if source or target are relationships themselves or if name is null
     */
    E relate(E sourceEntity, E targetEntity, String name, Map<String, Object> properties);

    /**
     * Persists a new entity with the provided assigned path.
     *
     * @param path      the canonical path to the entity
     * @param blueprint the blueprint of the entity
     * @return the representation object of the newly created entity
     */
    E persist(CanonicalPath path, AbstractElement.Blueprint blueprint);

    /**
     * Updates given entity with the data provided in the update object.
     *
     * @param entity the entity to update
     * @param update the update object
     * @throws IllegalArgumentException if the entity is of different type than the update
     */
    void update(E entity, AbstractElement.Update update);

    /**
     * Simply deletes the entity from the storage.
     *
     * @param entity the entity to delete
     */
    void delete(E entity);

    /**
     * Commits the transaction.
     * @param transaction the transaction to commit
     */
    void commit(Transaction transaction) throws CommitFailureException;

    /**
     * Rolls back the transaction.
     * @param transaction the transaction to roll back
     */
    void rollback(Transaction transaction);

    /**
     * Represents a transaction being performed. Implementations of the {@link InventoryBackend} interface are
     * encouraged to inherit from this class and add additional information to it. The base inventory implementation
     * only needs and provides the information stored in this class though.
     */
    class Transaction {
        private final boolean mutating;

        public Transaction(boolean mutating) {
            this.mutating = mutating;
        }

        public boolean isMutating() {
            return mutating;
        }
    }
}
