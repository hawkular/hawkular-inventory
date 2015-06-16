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
import static javax.ws.rs.core.Response.Status.FORBIDDEN;

import static org.hawkular.inventory.rest.RequestUtil.extractPaging;
import static org.hawkular.inventory.rest.ResponseUtil.pagedResponse;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.rest.json.ApiError;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
@Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/tenant", description = "Work with the tenant of the current persona")
public class RestTenants extends RestBase {

    @GET
    @Path("/tenant")
    @ApiOperation("Retrieves the tenant of the currently logged in persona")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized access"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMyTenant() {
        String tenantId = getTenantId();

        return Response.ok(inventory.tenants().get(tenantId).entity()).build();
    }

    @GET
    @Path("/tenants/{tenantId}")
    @ApiOperation("Retrieves the tenant if it belongs to the currently logged persona")
    @ApiResponses({
                          @ApiResponse(code = 200, message = "OK"),
                          @ApiResponse(code = 401, message = "Unauthorized access"),
                          @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
                          @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
                  })
    public Response getTenant(@PathParam("tenantId") String tenantId) {
        if (!tenantId.equals(getTenantId())) {
            return Response.status(FORBIDDEN).build();
        }
        return getMyTenant();
    }

    @GET
    @Path("/relationships")
    public Response getResourceRels(@Context UriInfo uriInfo) {
        Pager pager = extractPaging(uriInfo);
        Page<Relationship> entities = inventory.tenants().get(getTenantId())
                .relationships(Relationships.Direction.both).getAll().entities(pager);
        RestApiLogger.LOGGER.info(entities.toString());
        RestApiLogger.LOGGER.info("ahoj");
        System.out.println("AHOJ");
        RestApiLogger.LOGGER.error("kunda");

        return pagedResponse(Response.ok(), uriInfo, entities).build();
    }

    @PUT
    @Path("/tenant")
    @ApiOperation("Updates properties of the current tenant")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 401, message = "Unauthorized access"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateMyTenant(@ApiParam(required = true) Tenant.Update update) {
        String tenantId = getTenantId();
        if (!security.canUpdate(Tenant.class, tenantId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().update(tenantId, update);
        return Response.noContent().build();
    }

    @PUT
    @Path("/tenants/{tenantId}")
    @ApiOperation("Updates properties of the tenant")
    @ApiResponses({
                          @ApiResponse(code = 204, message = "OK"),
                          @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
                          @ApiResponse(code = 401, message = "Unauthorized access"),
                          @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
                          @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
                  })
    public Response update(@PathParam("tenantId") String tenantId,
                           @ApiParam(required = true) Tenant.Update update) {
        if (!tenantId.equals(getTenantId())) {
            return Response.status(FORBIDDEN).build();
        }
        return updateMyTenant(update);
    }

    @DELETE
    @Path("/")
    @ApiOperation("Deletes the tenant and all its data. Be careful!")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized access"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteMyTenant() {
        String tenantId = getTenantId();
        if (!security.canDelete(Tenant.class, tenantId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().delete(tenantId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/")
    @ApiOperation("Deletes the tenant and all its data. Be careful!")
    @ApiResponses({
                          @ApiResponse(code = 204, message = "OK"),
                          @ApiResponse(code = 401, message = "Unauthorized access"),
                          @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
                          @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
                  })
    public Response delete(@PathParam("tenantId") String tenantId) {
        if (!tenantId.equals(getTenantId())) {
            return Response.status(FORBIDDEN).build();
        }
        return deleteMyTenant();
    }

}
