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

import org.hawkular.inventory.api.filters.Filter;

/**
 * Generic methods for readonly access to relationships.
 * The only difference between this interface and ReadInterface is the getAll method, where
 * relationship specific filters are expected.
 *
 * @param <Single> an interface for traversing and resolving a single relationship
 * @param <Multiple> an interface for traversing and resolving multiple relationships
 *
 * @author Jirka Kremser
 * @since 1.0
 */
interface ReadRelationInterface<Single, Multiple> {

    /**
     * Tries to find a single entity in the current position in the inventory traversal.
     *
     * @param id the id of the entity to find in the current traversal position
     * @return access interface to the entity
     */
    Single get(String id) throws EntityNotFoundException, RelationNotFoundException;

    /**
     * Returns access interface to all entities conforming to provided filters in the current position in the inventory
     * traversal.
     *
     * @param filters the (possibly empty) list of filters to apply.
     * @return the (read-only) access interface to the found entities
     */
    Multiple getAll(Filter... filters);
}
