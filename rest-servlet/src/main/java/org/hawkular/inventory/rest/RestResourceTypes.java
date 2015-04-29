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
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.rest.json.ApiError;
import org.hawkular.inventory.rest.json.IdJSON;

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
import static org.hawkular.inventory.rest.RequestUtil.extractPaging;
import static org.hawkular.inventory.rest.ResponseUtil.pagedResponse;

/**
 * @author Lukas Krejci
 * @author jkremser
 * @since 0.0.1
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
    @ApiOperation("Retrieves all resource types. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of resource types"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@PathParam("tenantId") String tenantId, @Context UriInfo uriInfo) {
        Page<ResourceType> ret = inventory.tenants().get(tenantId).resourceTypes().getAll()
                .entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
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
    @ApiOperation("Retrieves all metric types associated with the resource type. Accepts paging query params.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of metric types associated with the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMetricTypes(@PathParam("tenantId") String tenantId,
            @PathParam("resourceTypeId") String resourceTypeId, @Context UriInfo uriInfo) {
        Page<MetricType> ret = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).metricTypes()
                .getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/resources")
    @ApiOperation("Retrieves all resources with given resource types. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of resources"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResources(@PathParam("tenantId") String tenantId,
            @PathParam("resourceTypeId") String resourceTypeId, @Context UriInfo uriInfo) {

        ResourceTypes.Single single = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId);
        single.entity(); // check whether it exists
        Page<Resource> ret = single.resources().getAll().entities(extractPaging(uriInfo));
        return pagedResponse(Response.ok(), uriInfo, ret).build();
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

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/relationships")
    @ApiOperation("Retrieves all relationships of given resource type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or resource type not found", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResourceTypeRelations(@PathParam("tenantId") String tenantId,
                                            @PathParam("resourceTypeId") String resourceTypeId,
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
        return Response.ok(inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                .relationships(directed).getAll(filters).entities()).build();
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

    public static String getUrl(ResourceType resourceType) {
        return String.format("/%s/resourceTypes/%s", resourceType.getTenantId(), resourceType.getId());
    }
}
