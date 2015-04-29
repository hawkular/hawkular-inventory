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
 * @author Jirka Kremser
 * @since 1.0
 */
public final class RelationAlreadyExistsException extends InventoryException {

    private final String relationName;
    private final Filter[][] path;

    public RelationAlreadyExistsException(Throwable cause, String relationName, Filter[] path) {
        super(cause);
        this.relationName = relationName;
        this.path = new Filter[1][];
        this.path[0] = path;
    }

    public RelationAlreadyExistsException(Throwable cause, String relationName, Filter[][] path) {
        super(cause);
        this.relationName = relationName;
        this.path = path;
    }

    public RelationAlreadyExistsException(String relationName, Filter[] path) {
        this(null, relationName, path);
    }

    public RelationAlreadyExistsException(String relationName, Filter[][] path) {
        this(null, relationName, path);
    }

    public RelationAlreadyExistsException(Entity entity) {
        this(entity.getId(), Filter.pathTo(entity));
    }

    @Override
    public String getMessage() {
        return "Relation with id '" + relationName + "' already exists at some of the positions: "
                + Arrays.deepToString(path);
    }
}
