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
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
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
import java.util.Set;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/", description = "Resource type CRUD")
public class RestResourceTypes {

    @Inject @ForRest
    private Inventory inventory;

    @GET
    @Path("/{tenantId}/resourceTypes")
    @ApiOperation("Retrieves all resource types")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of resource types"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@PathParam("tenantId") String tenantId) {
        return Response.ok(inventory.tenants().get(tenantId).resourceTypes().getAll().entities()).build();
    }

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}")
    @ApiOperation("Retrieves a single resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public ResourceType get(@PathParam("tenantId") String tenantId,
                            @PathParam("resourceTypeId") String resourceTypeId) {
        return inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).entity();
    }

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Retrieves all metric types associated with the resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of metric types associated with the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Set<MetricType> getMetricTypes(@PathParam("tenantId") String tenantId,
                                          @PathParam("resourceTypeId") String resourceTypeId) {
        return inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).metricTypes().getAll()
                .entities();
    }

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/resources")
    @ApiOperation("Retrieves all resources with given resource types")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of resources"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Set<Resource> getResources(@PathParam("tenantId") String tenantId,
                                      @PathParam("resourceTypeId") String resourceTypeId) {

        return inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                .resources().getAll().entities();
    }

    @POST
    @Path("/{tenantId}/resourceTypes")
    @ApiOperation("Creates a new resource type")
    @ApiResponses({
            @ApiResponse(code = 201, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource type already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(@PathParam("tenantId") String tenantId, ResourceType.Blueprint resourceType,
                           @Context UriInfo uriInfo) {
        inventory.tenants().get(tenantId).resourceTypes().create(resourceType);

        return ResponseUtil.created(uriInfo, resourceType.getId()).build();
    }

    @PUT
    @Path("{tenantId}/resourceTypes/{resourceTypeId}")
    @ApiOperation("Update a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("tenantId") String tenantId, @PathParam("resourceTypeId") String resourceTypeId,
                           @ApiParam(required = true) ResourceType.Update update) {
        inventory.tenants().get(tenantId).resourceTypes().update(resourceTypeId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}")
    @ApiOperation("Deletes a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("tenantId") String tenantId,
                           @PathParam("resourceTypeId") String resourceTypeId) {
        inventory.tenants().get(tenantId).resourceTypes().delete(resourceTypeId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Associates a pre-existing metric type with a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, resource type or metric type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addMetricType(@PathParam("tenantId") String tenantId,
                                  @PathParam("resourceTypeId") String resourceTypeId,
                                  IdJSON metricTypeId) {
        inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).metricTypes()
                .associate(metricTypeId.getId());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/metricTypes/{metricTypeId}")
    @ApiOperation("Disassociates the resource type with a metric type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, resource type or metric type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response removeMetricType(@PathParam("tenantId") String tenantId,
                                     @PathParam("resourceTypeId") String resourceTypeId,
                                     @PathParam("metricTypeId") String metricTypeId) {
        inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).metricTypes().disassociate(metricTypeId);
        return Response.noContent().build();
    }
}
