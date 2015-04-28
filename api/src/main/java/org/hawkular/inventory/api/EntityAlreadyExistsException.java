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
import org.hawkular.inventory.api.model.Entity;

import java.util.Arrays;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class EntityAlreadyExistsException extends InventoryException {

    private final String entityId;
    private final Filter[][] paths;

    public EntityAlreadyExistsException(Entity entity) {
        this(entity.getId(), asPaths(entity));
    }

    public EntityAlreadyExistsException(String entityId, Filter[][] paths) {
        this.entityId = entityId;
        this.paths = paths;
    }

    public EntityAlreadyExistsException(Throwable cause, String entityId, Filter[][] paths) {
        super(cause);
        this.entityId = entityId;
        this.paths = paths;
    }

    public String getEntityId() {
        return entityId;
    }

    /**
     * The exception occurred when trying to access at least one of the provided paths.
     */
    public Filter[][] getPaths() {
        return paths;
    }

    @Override
    public String getMessage() {
        return "Entity with id '" + entityId + "' already exists at some of the positions: "
                + Arrays.deepToString(paths);
    }

    private static Filter[][] asPaths(Entity entity) {
        Filter[][] ret = new Filter[1][];
        ret[0] = Filter.pathTo(entity);
        return ret;
    }
}
