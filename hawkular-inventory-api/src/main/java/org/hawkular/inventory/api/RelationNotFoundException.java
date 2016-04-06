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
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Jirka Kremser
 * @since 1.0
 */
public final class RelationNotFoundException extends InventoryException {

    private final String sourceEntityType;
    private final Filter[][] filters;
    private final String nameOrId;

    public RelationNotFoundException(SegmentType sourceEntityType, String nameOrId, Filter[] filters,
                                     String message, Throwable cause) {
        this(sourceEntityType == null ? null : sourceEntityType.getSimpleName(), nameOrId, oneElem(filters), message,
                cause);
    }

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, String nameOrId, Filter[] filters,
                                     String message, Throwable cause) {
        this(sourceEntityType, nameOrId, oneElem(filters), message, cause);
    }

    public RelationNotFoundException(SegmentType sourceEntityType, String nameOrId, Filter[][] filters,
                                     String message, Throwable cause) {
        this(sourceEntityType == null ? null : sourceEntityType.getSimpleName(), nameOrId, filters, message, cause);
    }

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, String nameOrId, Filter[][] filters,
                                     String message, Throwable cause) {
        this(sourceEntityType == null ? null : sourceEntityType.getSimpleName(), nameOrId, filters, message, cause);
    }

    private RelationNotFoundException(String sourceEntityType, String nameOrId, Filter[][] filters,
                                     String message, Throwable cause) {
        super(message, cause);
        this.sourceEntityType = sourceEntityType;
        this.filters = filters;
        this.nameOrId = nameOrId;
    }

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, String nameOrId, Filter[] filters,
                                     Throwable cause) {
        this(sourceEntityType, nameOrId, filters, null, cause);
    }

    public RelationNotFoundException(String nameOrId, Filter[] filters, String message) {
        this((String) null, nameOrId, oneElem(filters), message, null);
    }

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, Filter[] filters) {
        this(sourceEntityType, null, filters, null, null);
    }

    public RelationNotFoundException(Class<? extends Entity> sourceEntityType, Filter[][] filters) {
        this(sourceEntityType, null, filters, null, null);
    }

    public RelationNotFoundException(String nameOrId, Filter[] filters) {
        this((String) null, nameOrId, oneElem(filters), null, null);
    }

    public RelationNotFoundException(String nameOrId, Filter[][] filters) {
        this((String) null, nameOrId, filters, null, null);
    }

    @Override
    public String getMessage() {
        return "Relation"
                + (sourceEntityType != null ? " with source in " + sourceEntityType : "")
                + (nameOrId != null ? " with name or id '" + nameOrId + "'" : "")
                + (filters != null ? " searched for using any of the filters: " + Arrays.deepToString(filters) : "")
                + (super.getMessage() == null ? ": Was not found." : ": " + super.getMessage());
    }

    private static Filter[][] oneElem(Filter[] elem) {
        Filter[][] ret = new Filter[1][];
        ret[0] = elem;
        return ret;
    }

    public String getNameOrId() {
        return nameOrId;
    }

    public String getSourceEntityType() {
        return sourceEntityType;
    }

    public Filter[][] getFilters() {
        return filters;
    }
}
