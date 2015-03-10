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

package org.hawkular.inventory.rest;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.filters.Filter;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Provider
public class InventoryExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof EntityNotFoundException) {
            return Response.status(NOT_FOUND).entity(
                    EntityTypeAndPath.fromException((EntityNotFoundException) exception)).build();
        } else if (exception instanceof EntityAlreadyExistsException) {
            return Response.status(CONFLICT).entity(
                    EntityIdAndPath.fromException((EntityAlreadyExistsException) exception)).build();
        } else {
            RestApiLogger.LOGGER.warn(exception);
            return Response.serverError().entity(exception).build();
        }
    }

    public static class EntityTypeAndPath {
        private final String entityType;
        private final Filter[] path;

        public static EntityTypeAndPath fromException(EntityNotFoundException e) {
            return new EntityTypeAndPath(e.getEntityType().getSimpleName(), e.getFilters());
        }

        public EntityTypeAndPath(String entityType, Filter[] path) {
            this.entityType = entityType;
            this.path = path;
        }

        public String getEntityType() {
            return entityType;
        }

        public Filter[] getPath() {
            return path;
        }
    }

    public static class EntityIdAndPath {
        private final String entityId;
        private final Filter[] path;

        public static EntityIdAndPath fromException(EntityAlreadyExistsException e) {
            return new EntityIdAndPath(e.getEntityId(), e.getPath());
        }

        public EntityIdAndPath(String entityId, Filter[] path) {
            this.entityId = entityId;
            this.path = path;
        }

        public String getEntityId() {
            return entityId;
        }

        public Filter[] getPath() {
            return path;
        }
    }
}
