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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.rest.json.ApiError;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
@Path("/path")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
@Api(value = "/path", description = "The endpoint to obtain inventory entities by their canonical path.")
public class RestPath extends RestBase {

    @GET
    @Path("/{entityPath:.+}")
    @ApiOperation("Return an entity with the provided canonical path")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The entity", response = Entity.class),
            @ApiResponse(code = 401, message = "Unauthorized access"),
            @ApiResponse(code = 404, message = "The entity doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response get(String entityPath) {
        String tenantId = getTenantId();
        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath path = CanonicalPath.fromPartiallyUntypedString('/' + entityPath, tenant, Entity.class);

        return Response.ok(inventory.inspect(path, ResolvableToSingle.class).entity()).build();
    }
}
