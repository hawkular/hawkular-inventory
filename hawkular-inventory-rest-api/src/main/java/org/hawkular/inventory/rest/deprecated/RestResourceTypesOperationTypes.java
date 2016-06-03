/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.rest.deprecated;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;

import static org.hawkular.inventory.rest.RequestUtil.extractPaging;

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

import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.ResponseUtil;
import org.hawkular.inventory.rest.RestBase;
import org.hawkular.inventory.rest.json.ApiError;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;


/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
@Path("/deprecated")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/deprecated", description = "Manages associations between resource types and metric types",
        tags = {"Deprecated"})
public class RestResourceTypesOperationTypes extends RestBase {
    @POST
    @javax.ws.rs.Path("/resourceTypes/{resourceTypeId}/operationTypes")
    @ApiOperation("Creates a new operation type under a pre-existing resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK Created"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response createConfiguration(@PathParam("resourceTypeId") String resourceType,
                                        @ApiParam(required = true) OperationType.Blueprint operationType,
                                        @Context UriInfo uriInfo) {

        return doCreateData(null, null, resourceType, operationType, uriInfo);
    }

    @POST
    @javax.ws.rs.Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes")
    @ApiOperation("Creates a new operation type under a pre-existing resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK Created"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource type or feed doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response createConfiguration(@PathParam("feedId") String feedId,
                                        @PathParam("resourceTypeId") String resourceType,
                                        @ApiParam(required = true) OperationType.Blueprint operationType,
                                        @Context UriInfo uriInfo) {

        return doCreateData(null, feedId, resourceType, operationType, uriInfo);
    }

    @PUT
    @javax.ws.rs.Path("/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}")
    @ApiOperation("Updates the operation type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateData(@PathParam("resourceTypeId") String resourceType,
                               @PathParam("operationTypeId") String operationTypeId,
                               @ApiParam(required = true) OperationType.Update update) {

        return doUpdateData(null, null, resourceType, operationTypeId, update);
    }

    @PUT
    @javax.ws.rs.Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}")
    @ApiOperation("Updates the configuration of a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateData(@PathParam("feedId") String feedId,
                               @PathParam("resourceTypeId") String resourceType,
                               @PathParam("operationTypeId") String operationTypeId,
                               @ApiParam(required = true) OperationType.Update update) {

        return doUpdateData(null, feedId, resourceType, operationTypeId, update);
    }

    @DELETE
    @javax.ws.rs.Path("/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}")
    @ApiOperation("Deletes the operation type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteData(@PathParam("resourceTypeId") String resourceType,
                               @PathParam("operationTypeId") String operationTypeId) {

        return doDeleteData(null, null, resourceType, operationTypeId);
    }

    @DELETE
    @javax.ws.rs.Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}")
    @ApiOperation("Updates the configuration of a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteData(@PathParam("feedId") String feedId,
                               @PathParam("resourceTypeId") String resourceType,
                               @PathParam("operationTypeId") String operationTypeId) {

        return doDeleteData(null, feedId, resourceType, operationTypeId);
    }

    @GET
    @Path("resourceTypes/{resourceTypeId}/operationTypes")
    @ApiOperation("Retrieves operation types")
    @ApiResponses({
                          @ApiResponse(code = 200, message = "the resource type"),
                          @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                                       response = ApiError.class),
                          @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
                  })
    public Response getAll(@PathParam("resourceTypeId") String resourceTypeId,
                           @Context UriInfo uriInfo) {

        Page<OperationType> operationTypes = inventory.tenants().get(getTenantId()).resourceTypes()
                .get(resourceTypeId).operationTypes().getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, operationTypes).build();
    }

    @GET
    @Path("/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}")
    @ApiOperation("Retrieves the operation type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public OperationType get(@PathParam("resourceTypeId") String resourceTypeId,
                             @PathParam("operationTypeId") String operationTypeId) {
        return doGetDataEntity(null, null, resourceTypeId, operationTypeId);
    }

    @GET
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes")
    @ApiOperation("Retrieves operation types")
    @ApiResponses({
                          @ApiResponse(code = 200, message = "the resource type"),
                          @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                                       response = ApiError.class),
                          @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
                  })
    public Response getAll(@PathParam("feedId") String feedId,
                           @PathParam("resourceTypeId") String resourceTypeId,
                           @Context UriInfo uriInfo) {

        Page<OperationType> operationTypes = inventory.tenants().get(getTenantId())
                .feeds().get(feedId).resourceTypes().get(resourceTypeId).operationTypes()
                .getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, operationTypes).build();
    }

    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}")
    @ApiOperation("Retrieves a single resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public OperationType get(@PathParam("feedId") String feedId,
                             @PathParam("resourceTypeId") String resourceTypeId,
                             @PathParam("operationTypeId") String operationTypeId) {
        return doGetDataEntity(null, feedId, resourceTypeId, operationTypeId);
    }

    private Response doCreateData(String environmentId, String feedId, String resourceTypeId,
                                  OperationType.Blueprint blueprint, UriInfo uriInfo) {

        CanonicalPath resourceType = getResourceTypePath(environmentId, feedId, resourceTypeId);

        if (!security.canUpdate(resourceType)) {
            return Response.status(FORBIDDEN).build();
        }
        OperationType entity =
                inventory.inspect(resourceType, ResourceTypes.Single.class).operationTypes().create(blueprint).entity();

        return ResponseUtil.created(entity, uriInfo, blueprint.getId()).build();

    }

    private Response doUpdateData(String environmentId, String feedId, String resourceTypeId, String operationTypeId,
                                  OperationType.Update update) {

        CanonicalPath operationType = getOperationTypePath(environmentId, feedId, resourceTypeId, operationTypeId);

        if (!security.canUpdate(operationType)) {
            return Response.status(FORBIDDEN).build();
        }
        inventory.inspect(operationType, OperationTypes.Single.class).update(update);

        return Response.noContent().build();
    }

    private Response doDeleteData(String environmentId, String feedId, String resourceTypeId, String operationTypeId) {
        CanonicalPath operationType = getOperationTypePath(environmentId, feedId, resourceTypeId, operationTypeId);

        if (!security.canUpdate(operationType)) {
            return Response.status(FORBIDDEN).build();
        }
        inventory.inspect(operationType, OperationTypes.Single.class).delete();

        return Response.noContent().build();
    }

    private OperationType doGetDataEntity(String environmentId, String feedId, String resourceTypeId,
                                          String operationTypeId) {

        return inventory.inspect(getOperationTypePath(environmentId, feedId, resourceTypeId, operationTypeId),
                OperationTypes.Single.class).entity();
    }

    private CanonicalPath getOperationTypePath(String environmentId, String feedId, String resourceTypeId, String
            operationTypeId) {

        if (environmentId == null) {
            return CanonicalPath.of().tenant(getTenantId()).resourceType(resourceTypeId).operationType
                    (operationTypeId).get();
        } else {
            return CanonicalPath.of().tenant(getTenantId()).feed(feedId).resourceType(resourceTypeId)
                    .operationType(operationTypeId).get();
        }
    }

    private CanonicalPath getResourceTypePath(String environmentId, String feedId, String resourceTypeId) {
        if (environmentId == null) {
            return CanonicalPath.of().tenant(getTenantId()).resourceType(resourceTypeId).get();
        } else {
            return CanonicalPath.of().tenant(getTenantId()).feed(feedId).resourceType(resourceTypeId).get();
        }
    }
}
