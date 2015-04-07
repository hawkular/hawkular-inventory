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
package org.hawkular.inventory.api;

/**
 * Generic methods to write access to entities.
 *
 * @param <Entity> the entity type
 * @param <Blueprint> the blueprint type that supplies data necessary to create a new entity
 * @param <Single> the access interface to a single entity
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface WriteInterface<Entity, Blueprint, Single> {

    /**
     * Creates a new entity at the current position in the inventory traversal.
     *
     * @param blueprint the blueprint to be used to create the new entity
     * @return access interface to the freshly created entity
     *
     * @throws EntityAlreadyExistsException if the entity already exists
     */
    Single create(Blueprint blueprint) throws EntityAlreadyExistsException;

    /**
     * Persists the provided entity on the current position in the inventory traversal.
     *
     * @param entity the entity to update
     *
     * @throws EntityNotFoundException if the entity is not found in the database
     * @throws java.lang.IllegalArgumentException if the supplied entity could not be updated for some reason
     */
    void update(Entity entity) throws EntityNotFoundException;

    /**
     * Deletes an entity with the provided id from the current position in the inventory traversal.
     *
     * @param id the id of the entity to delete
     * @throws EntityNotFoundException
     * @throws java.lang.IllegalArgumentException if the supplied entity could not be deleted for some reason
     */
    void delete(String id) throws EntityNotFoundException;
}
