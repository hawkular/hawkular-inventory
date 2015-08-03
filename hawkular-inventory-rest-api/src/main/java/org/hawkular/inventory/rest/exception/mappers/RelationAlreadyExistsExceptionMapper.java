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

package org.hawkular.inventory.rest.exception.mappers;

import static javax.ws.rs.core.Response.Status.CONFLICT;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.rest.json.ApiError;
import org.jboss.logging.Logger;

/**
 * @author Jirka Kremser
 * @since 0.1.0
 */
@Provider
public class RelationAlreadyExistsExceptionMapper implements ExceptionMapper<RelationAlreadyExistsException> {

    @Override
    public Response toResponse(RelationAlreadyExistsException exception) {
        return ExceptionMapperUtils.buildResponse(Logger.Level.DEBUG, new ApiError(exception.getMessage(),
            ExceptionMapperUtils.RelationshipNameAndPath.fromException(exception)), exception, CONFLICT);
    }
}
