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
public final class RelationNotFoundException extends InventoryException {

    private final String sourceEntityType;
    private final Filter[] filters;
    private final String nameOrId;

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, String nameOrId, Filter[] filters,
                                     Throwable cause) {
        super(cause);
        this.sourceEntityType = sourceEntityType.getSimpleName();
        this.filters = filters;
        this.nameOrId = nameOrId;
    }

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, String nameOrId, Filter[] filters) {
        this(sourceEntityType, nameOrId, filters, null);
    }

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, Filter[] filters) {
        this(sourceEntityType, null, filters, null);
    }

    public RelationNotFoundException(String nameOrId, Filter[] filters) {
        this(null, nameOrId, filters, null);
    }

    @Override
    public String getMessage() {
        return "Relation"
                + (sourceEntityType != null ? " with source in " + sourceEntityType : "")
                + (nameOrId != null ? " with name or id '" + nameOrId + "'" : "")
                + " not found" +
                (filters != null ? " using filters: " + Arrays.toString(filters) : ".");
    }
}
