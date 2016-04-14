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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.Blueprint;

/**
 * Generic methods to write access to entities.
 *
 * @param <Id> the type of the identification of the entity (usually a string)
 * @param <U> type of entity update class
 * @param <B> the blueprint type that supplies data necessary to create a new entity
 * @param <Single> the access interface to a single entity
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface WriteInterface<U, B extends Blueprint, Single, Id> {

    /**
     * Creates a new entity at the current position in the inventory traversal.
     * <p>
     * Note that the returned access interface can cache the entity created in this blueprint. That means that a
     * subsequent call to {@link ResolvableToSingle#entity()} on the returned instance will NOT access the database
     * @param blueprint the blueprint to be used to create the new entity
     * @param cache whether to cache the resulting entity so that the subsequent call to
     * {@link ResolvableToSingle#entity()} doesn't need to touch database
     * @return access interface to the freshly created entity
     *
     * @throws EntityAlreadyExistsException if the entity already exists
     * @throws IllegalArgumentException if the blueprint or context in which the entity is being create is somehow
     *                                  invalid
     */
    Single create(B blueprint, boolean cache) throws EntityAlreadyExistsException;

    /**
     * Equivalent to {@code create(blueprint, true)}
     * @param blueprint the blueprint of the new entity
     * @return access interface to the freshly create entity
     * @throws EntityAlreadyExistsException
     * @throws IllegalArgumentException if the blueprint or context in which the entity is being create is somehow
     *                                  invalid
     * @see #create(Blueprint, boolean)
     */
    default Single create(B blueprint) throws EntityAlreadyExistsException {
        return create(blueprint, true);
    }

    /**
     * Persists the provided entity on the current position in the inventory traversal.
     *
     * @param id the id of the entity to update
     * @param update the updates to the entity
     *
     * @throws EntityNotFoundException if the entity is not found in the database
     * @throws java.lang.IllegalArgumentException if the supplied entity could not be updated for some reason
     */
    void update(Id id, U update) throws EntityNotFoundException;

    /**
     * Deletes an entity with the provided id from the current position in the inventory traversal.
     *
     * @param id the id of the entity to delete
     * @throws EntityNotFoundException if an entity with given ID doesn't exist on the current position in the inventory
     *                                 traversal
     * @throws java.lang.IllegalArgumentException if the supplied entity could not be deleted for some reason
     */
    void delete(Id id) throws EntityNotFoundException;
}
