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

import org.hawkular.inventory.api.model.AbstractElement;

/**
 * Base interface for all browser interfaces over a single entity.
 *
 * @param <Entity> the type of the entity being browsed
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface ResolvableToSingle<Entity extends AbstractElement<?, ?>, Update> {

    /**
     * Resolves the entity and returns it.
     * <p>
     * Note that this might return stale data if the entity was freshly created (and therefore cached) and subsequent
     * updates were made to the entity outside of this instance.
     *
     * @return the entity at the current position in the inventory traversal
     * @throws EntityNotFoundException   if there is no entity corresponding to the traversal
     * @throws RelationNotFoundException if there is no relation corresponding to the traversal
     */
    Entity entity() throws EntityNotFoundException, RelationNotFoundException;

    /**
     * Similar to {@link #entity()} but merely checks whether the entity exists on the position in the inventory
     * traversal.
     *
     * <p>Note that the default implementation might not be optimal performance-wise because it tries to fully resolve
     * the entity using the {@link #entity()} method but discards that result right after.
     *
     * @return true if there is an entity, false otherwise.
     */
    default boolean exists() {
        try {
            entity();
            return true;
        } catch (EntityNotFoundException | RelationNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Updates the entity.
     *
     * @param update the update to be applied
     * @throws EntityNotFoundException   if there is no entity corresponding to the traversal
     * @throws RelationNotFoundException if there is no relation corresponding to the traversal
     */
    void update(Update update) throws EntityNotFoundException, RelationNotFoundException;

    /**
     * Deletes the entity.
     *
     * <p>Note that this doesn't actually delete the entity from the inventory. It merely records its removal at the
     * provided point in time.
     *
     * @throws EntityNotFoundException   if there is no entity corresponding to the traversal
     * @throws RelationNotFoundException if there is no relation corresponding to the traversal
     * @throws IllegalArgumentException  if there were changes to the entity already made after the provided time of
     * deletion
     */
    void delete();

    /**
     * This removes the entity and all its history from the inventory. After this call, it looks like the entity never
     * existed in the inventory.
     *
     * <p>Usually, you don't want to use this method. Use {@link #delete()} instead which preserves the history
     * of the entity.
     */
    void eradicate();
}
