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
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.rest.json.ApiError;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @author jkremser
 * @since 0.0.1
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
    @ApiOperation("Lists all tenants")
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
    @ApiOperation("Creates a new tenant")
    @ApiResponses({
            @ApiResponse(code = 201, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 409, message = "Tenant already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(Tenant.Blueprint tenant, @Context UriInfo uriInfo) {
        inventory.tenants().create(tenant);
        return ResponseUtil.created(uriInfo, tenant.getId()).build();
    }

    @GET
    @Path("/{tenantId}")
    @ApiOperation("Retrieves a single tenant")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getTenant(@PathParam("tenantId") String tenantId) {
        return Response.ok(inventory.tenants().get(tenantId).entity()).build();
    }

    @GET
    @Path("/{tenantId}/relationships")
    @ApiOperation("Retrieves all relationships of given tenant.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getTenantRelations(@PathParam("tenantId") String tenantId,
                                       @DefaultValue("both") @QueryParam("direction") String direction,
                                       @DefaultValue("") @QueryParam("property") String propertyName,
                                       @DefaultValue("") @QueryParam("propertyValue") String propertyValue,
                                       @DefaultValue("") @QueryParam("named") String named,
                                       @DefaultValue("") @QueryParam("sourceType") String sourceType,
                                       @DefaultValue("") @QueryParam("targetType") String targetType,
                                       @Context UriInfo info) {

        RelationFilter[] filters = RestRelationships.extractFilters(propertyName, propertyValue, named, sourceType,
                targetType, info);

        // this will throw IllegalArgumentException on undefined values
        Relationships.Direction directed = Relationships.Direction.valueOf(direction);
        return Response.ok(inventory.tenants().get(tenantId).relationships(directed)
                .getAll(filters)
                .entities()).build();
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
                           @ApiParam(required = true) Tenant.Update update) {
        inventory.tenants().update(tenantId, update);
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

    public static String getUrl(Tenant tenant) {
        return String.format("/tenants/%s", tenant.getId());
    }
}
