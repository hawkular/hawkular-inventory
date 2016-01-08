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

import org.hawkular.inventory.api.Parents;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.json.ApiError;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/", description = "Resource type CRUD")
public class RestResourceTypes extends RestBase {

    @GET
    @Path("/resourceTypes")
    @ApiOperation("Retrieves all resource types. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of resource types"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@QueryParam("feedless") @DefaultValue("false") boolean feedless, @Context UriInfo uriInfo) {

        Tenants.Single tenants = inventory.tenants().get(getTenantId());

        Page<ResourceType> ret = (feedless ? tenants.resourceTypes() : tenants.resourceTypesUnder(Parents.any()))
                .getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @Path("/resourceTypes/{resourceTypeId}")
    @ApiOperation("Retrieves a single resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public ResourceType get(@PathParam("resourceTypeId") String resourceTypeId) {
        return inventory.tenants().get(getTenantId()).resourceTypes().get(resourceTypeId).entity();
    }

    @POST
    @Path("/resourceTypes")
    @ApiOperation("Creates a new resource type")
    @ApiResponses({
            @ApiResponse(code = 201, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource type already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(ResourceType.Blueprint resourceType, @Context UriInfo uriInfo) {
        String tenantId = getTenantId();

        if (!security.canCreate(ResourceType.class).under(CanonicalPath.of().tenant(tenantId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).resourceTypes().create(resourceType);

        return ResponseUtil.created(uriInfo, resourceType.getId()).build();
    }

    @PUT
    @Path("/resourceTypes/{resourceTypeId}")
    @ApiOperation("Update a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("resourceTypeId") String resourceTypeId,
            @ApiParam(required = true) ResourceType.Update update) {
        String tenantId = getTenantId();

        if (!security.canUpdate(CanonicalPath.of().tenant(tenantId).resourceType(resourceTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).resourceTypes().update(resourceTypeId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/resourceTypes/{resourceTypeId}")
    @ApiOperation("Deletes a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("resourceTypeId") String resourceTypeId) {
        String tenantId = getTenantId();

        if (!security.canDelete(CanonicalPath.of().tenant(tenantId).resourceType(resourceTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).resourceTypes().delete(resourceTypeId);
        return Response.noContent().build();
    }

    @GET
    @Path("/feeds/{feedId}/resourceTypes")
    @ApiOperation("Retrieves all metric types associated with the resource type. Accepts paging query params.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of metric types associated with the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@PathParam("feedId") String feedId,
                           @Context UriInfo uriInfo) {

        Page<ResourceType> ret = inventory.tenants().get(getTenantId()).feeds().get(feedId).resourceTypes().getAll()
                .entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}")
    @ApiOperation("Retrieves all metric types associated with the resource type. Accepts paging query params.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of metric types associated with the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public ResourceType get(@PathParam("feedId") String feedId,
                            @PathParam("resourceTypeId") String resourceTypeId, @Context UriInfo uriInfo) {

        return inventory.tenants().get(getTenantId()).feeds().get(feedId).resourceTypes().get(resourceTypeId).entity();
    }

    @POST
    @Path("/feeds/{feedId}/resourceTypes")
    @ApiOperation("Creates a new resource type")
    @ApiResponses({
            @ApiResponse(code = 201, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource type already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(@PathParam("feedId") String feedId,
                           ResourceType.Blueprint resourceType, @Context UriInfo uriInfo) {
        String tenantId = getTenantId();

        if (!security.canCreate(ResourceType.class).under(CanonicalPath.of().tenant(tenantId).feed(feedId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).feeds().get(feedId).resourceTypes().create(resourceType);

        return ResponseUtil.created(uriInfo, resourceType.getId()).build();
    }

    @PUT
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}")
    @ApiOperation("Update a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("feedId") String feedId,
                           @PathParam("resourceTypeId") String resourceTypeId,
                           @ApiParam(required = true) ResourceType.Update update) {
        String tenantId = getTenantId();

        if (!security.canUpdate(CanonicalPath.of().tenant(tenantId).feed(feedId).resourceType(resourceTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).feeds().get(feedId).resourceTypes().update(resourceTypeId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}")
    @ApiOperation("Deletes a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("feedId") String feedId,
                           @PathParam("resourceTypeId") String resourceTypeId) {
        String tenantId = getTenantId();

        if (!security.canDelete(CanonicalPath.of().tenant(tenantId).feed(feedId).resourceType(resourceTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).feeds().get(feedId).resourceTypes().delete(resourceTypeId);
        return Response.noContent().build();
    }
}
