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

import java.util.Arrays;

import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class EntityNotFoundException extends InventoryException {

    private final String entitySimpleTypeName;
    private final Filter[][] filters;
    private final String msg;

    public EntityNotFoundException(Class<?> entityClass, Filter[][] filters) {
        this(entityClass == null ? null : entityClass.getSimpleName(), filters);
    }

    public EntityNotFoundException(SegmentType entityType, Filter[][] filters) {
        this(entityType == null ? null : entityType.getSimpleName(), filters);
    }

    public EntityNotFoundException(String entitySimpleTypeName, Filter[][] filters) {
        this.entitySimpleTypeName = entitySimpleTypeName;
        this.filters = filters;
        this.msg = null;
    }

    public EntityNotFoundException(Filter[][] filters) {
        this((String) null, filters);
    }

    public EntityNotFoundException(String msg) {
        this.msg = msg;
        this.entitySimpleTypeName = null;
        this.filters = null;
    }

    /**
     * @return the simple type name (without Java package) of the entity that was not found.
     */
    public String getEntitySimpleTypeName() {
        return entitySimpleTypeName;
    }

    /**
     * @return the considered paths to the entity that was not found.
     */
    public Filter[][] getFilters() {
        return filters;
    }

    @Override
    public String getMessage() {
        if (null != msg) {
            return msg;
        }
        return (entitySimpleTypeName == null ? "Nothing" : ("No " + entitySimpleTypeName)) +
                " found on any of the following paths: " + Arrays.deepToString(filters);
    }
}
