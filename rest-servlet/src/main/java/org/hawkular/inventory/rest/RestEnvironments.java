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

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.rest.json.ApiError;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;
import java.util.Set;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
@Api(value = "/", description = "CRUD of environments.")
public class RestEnvironments {

    @Inject @ForRest
    private Inventory inventory;

    @GET
    @Path("/{tenantId}/environments")
    @ApiOperation("Returns all environments under given tenant.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = Set.class),
            @ApiResponse(code = 404, message = "Tenant not found", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Set<Environment> getAll(@PathParam("tenantId") String tenantId) throws Exception {
        return inventory.tenants().get(tenantId).environments().getAll().entities();
    }

    @GET
    @Path("/{tenantId}/environments/{environmentId}")
    @ApiOperation("Retrieves a single environment")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Environment get(@PathParam("tenantId") String tenantId, @PathParam("environmentId") String environmentId)
            throws Exception {
        return inventory.tenants().get(tenantId).environments().get(environmentId).entity();
    }

    @POST
    @Path("/{tenantId}/environments")
    @ApiOperation("Creates a new environment in given tenant.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Environment created"),
            @ApiResponse(code = 404, message = "Tenant not found", response = ApiError.class),
            @ApiResponse(code = 409, message = "Environment already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(@PathParam("tenantId") String tenantId,
                           @ApiParam(required = true) Environment.Blueprint environmentBlueprint,
                           @Context UriInfo uriInfo)
            throws Exception {
        inventory.tenants().get(tenantId).environments().create(environmentBlueprint);
        return ResponseUtil.created(uriInfo, environmentBlueprint.getId()).build();
    }

    @PUT
    @Path("/{tenantId}/environments/{environmentId}")
    @ApiOperation("Updates properties of the environment")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The properties of the environment successfully updated"),
            @ApiResponse(code = 400, message = "Properties invalid", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or environment not found", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public void update(@PathParam("tenantId") String tenantId, @PathParam("environmentId") String environmentId,
                           @ApiParam(required = true) Map<String, Object> properties) throws Exception {
        Environment env = new Environment(tenantId, environmentId);
        env.getProperties().putAll(properties);

        inventory.tenants().get(tenantId).environments().update(env);
    }

    @DELETE
    @Path("/{tenantId}/environments/{environmentId}")
    @ApiOperation("Deletes the environment from the tenant")
    @ApiResponses({
        @ApiResponse(code = 204, message = "Environment successfully deleted"),
        @ApiResponse(code = 400, message = "Delete failed because it would leave inventory in invalid state",
                response = ApiError.class),
        @ApiResponse(code = 404, message = "Tenant or environment not found", response = ApiError.class),
        @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("tenantId") String tenantId, @PathParam("environmentId") String environmentId)
        throws Exception {

        inventory.tenants().get(tenantId).environments().delete(environmentId);
        return Response.noContent().build();
    }
}
