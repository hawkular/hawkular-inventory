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

import org.hawkular.inventory.api.model.Relationship;

/**
 * Generic methods to write access to relationships.
 *
 * @param <Entity> the entity type
 * @param <Blueprint> the blueprint type that supplies data necessary to create a new relationship
 * @param <Single> the access interface to a single relationship
 *
 * @author Jirka Kremser
 * @since 1.0
 */
interface WriteRelationshipInterface<Entity, Blueprint, Single> {

    /**
     * Creates a new relationship at the current position in the inventory traversal.
     *
     * @param blueprint the blueprint to be used to create the new relationship
     * @return access interface to the freshly created relationship
     *
     * @throws org.hawkular.inventory.api.RelationNotFoundException if the relationship already exists
     */
//    Single create(Blueprint blueprint) throws RelationNotFoundException;

    /**
     * Persists the provided relationship on the current position in the inventory traversal.
     *
     * @param relationship the relationship to update
     *
     * @throws org.hawkular.inventory.api.RelationNotFoundException if the relationship is not found in the database
     */
    void update(Relationship relationship) throws RelationNotFoundException;

    /**
     * Deletes an relationship with the provided id from the current position in the inventory traversal.
     *
     * @param id the id of the relationship to delete
     * @throws org.hawkular.inventory.api.RelationNotFoundException
     */
    void delete(String id) throws RelationNotFoundException;
}
