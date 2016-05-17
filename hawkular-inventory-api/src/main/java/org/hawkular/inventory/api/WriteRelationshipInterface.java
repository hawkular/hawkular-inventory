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

import java.util.Map;

import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.paths.Path;

/**
 * Generic methods to write access to relationships.
 *
 * @param <Single> the access interface to a single relationship
 *
 * @author Jirka Kremser
 * @since 1.0
 */
interface WriteRelationshipInterface<Single> {

    /**
     * Creates a new relationship at the current position in the inventory traversal.
     *
     * <p>It is possible to have multiple relationships with the same name between 2 entities. These relationships will
     * differ in their ids and can have different properties.
     *
     * <p>Note: please review the comments on the individual well-known relationships (
     * {@link org.hawkular.inventory.api.Relationships.WellKnown#contains contains},
     * {@link org.hawkular.inventory.api.Relationships.WellKnown#defines defines},
     * {@link org.hawkular.inventory.api.Relationships.WellKnown#incorporates incorporates}) for restrictions of usage,
     * especially what restrictions the relationships impose when deleting entities.
     *
     * @param name the name of the relationship
     * @param targetOrSource the the source/target entity (based on the chosen relationship direction) that the current
     *                       entity (based on the position in the inventory traversal) will be in the relationship with
     * @param properties the properties of the newly created relationship or null if none specified
     * @return access interface to the freshly created relationship
     *
     * @throws java.lang.IllegalArgumentException if any of the parameters is null
     * @throws EntityNotFoundException if the current position in the inventory traversal doesn't evaluate to an
     * existing entity or if the provided other end of the relationship doesn't exist.
     */
    Single linkWith(String name, Path targetOrSource, Map<String, Object> properties)
            throws IllegalArgumentException;

    /**
     * @see #linkWith(String, Path, Map)
     */
    default Single linkWith(Relationships.WellKnown name, Path targetOrSource, Map<String, Object> properties)
            throws IllegalArgumentException {
        return linkWith(name.name(), targetOrSource, properties);
    }

    /**
     * Updates the provided relationship on the current position in the inventory traversal.
     *
     * @param id the id of the relationship to update
     * @param update the update
     *
     * @throws org.hawkular.inventory.api.RelationNotFoundException if the relationship is not found in the database
     */
    void update(String id, Relationship.Update update) throws RelationNotFoundException;

    /**
     * Deletes an relationship with the provided id from the current position in the inventory traversal.
     *
     * @param id the id of the relationship to delete
     * @throws org.hawkular.inventory.api.RelationNotFoundException if the relation with given id doesn't exist on the
     * current position in the inventory traversal
     */
    void delete(String id) throws RelationNotFoundException;
}
