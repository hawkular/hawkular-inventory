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
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.rest.json.ApiError;
import org.hawkular.inventory.rest.json.IdJSON;

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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/tenants")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/tenants", description = "CRUD for tenants")
public class RestTenants {

    @Inject @ForRest
    private Inventory inventory;

    @GET
    @Path("/")
    @ApiOperation(nickname = "getAllTenants",
            value = "Lists all tenants",
            notes = "",
            responseContainer = "Set<Tenant>",
            response = Tenant.class)
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of tenants"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll() {
        return Response.ok(inventory.tenants().getAll().entities()).build();
    }

    @POST
    @Path("/")
    @ApiOperation(nickname = "createTenant",
            value = "Creates a new tenant",
            notes = "")
    @ApiResponses({
            @ApiResponse(code = 201, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 409, message = "Tenant already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(IdJSON tenantId, @Context UriInfo uriInfo) {
        inventory.tenants().create(new Tenant.Blueprint(tenantId.getId(), tenantId.getProperties()));
        return ResponseUtil.created(uriInfo, tenantId.getId()).build();
    }

    @PUT
    @Path("/{tenantId}")
    @ApiOperation("Updates properties of a tenant")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("tenantId") String tenantId,
                           @ApiParam(required = true) Map<String, Object> properties) {
        Tenant t = new Tenant(tenantId);
        t.getProperties().putAll(properties);

        inventory.tenants().update(t);

        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}")
    @ApiOperation("Deletes a tenant")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("tenantId") String tenantId) {
        inventory.tenants().delete(tenantId);
        return Response.noContent().build();
    }
}
