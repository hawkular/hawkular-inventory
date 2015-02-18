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
public class EntityNotFoundException extends RuntimeException {

    private final String entityType;
    private final Filter[] filters;

    public EntityNotFoundException(Class<? extends Entity> entityClass, Filter[] filters) {
        this.entityType = entityClass.getSimpleName();
        this.filters = filters;
    }

    public EntityNotFoundException(Class<? extends Entity> entityClass, Filter[] filters, Throwable cause) {
        super(cause);
        this.entityType = entityClass.getSimpleName();
        this.filters = filters;
    }

    @Override
    public String getMessage() {
        return entityType + " not found using filters: " + Arrays.toString(filters);
    }
}
