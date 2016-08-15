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
package org.hawkular.inventory.api.model;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.paths.CanonicalPath;

/**
 * Utility class to compute the content hash of entities. Content hash is not hierarchical nor it is "identifying".
 * It merely hashes the contents of an entity without its ID. I.e. it includes the name, generic properties and any
 * other properties on the entities.
 * <p>
 * Note that {@link MetadataPack} does not have a content hash.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
public final class ContentHash {

    private ContentHash() {

    }

    public static String of(Entity<? extends Entity.Blueprint, ?> entity) {
        //the only entity that requires the inventory to convert to its blueprint is the metadatapack.
        //but metadatapacks do not have a content hash, so we're good passing in null here.
        Entity.Blueprint bl = Inventory.asBlueprint(entity);
        return of(bl, entity.getPath());
    }

    /**
     * The entity path is currently only required for resources, which base their content hash on the resource type
     * path which can be specified using a relative or canonical path in the blueprint. For the content hash to be
     * consistent we need to convert to just a single representation of the path.
     *
     * @param entity the entity to produce the hash of
     * @param entityPath the canonical path of the entity (can be null for all but resources)
     * @return the content hash of the entity
     */
    public static String of(Entity.Blueprint entity, CanonicalPath entityPath) {
        return ComputeHash.of(InventoryStructure.of(entity).build(), entityPath, false, true, false).getContentHash();
    }
}
