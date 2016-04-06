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
package org.hawkular.inventory.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;

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

import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
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
@Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/", description = "CRUD for operation type data",
        tags = "ResourceTypes OperationTypes Data")
public class RestResourceTypesOperationTypesData extends RestBase {
    @POST
    @javax.ws.rs.Path("/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
    @ApiOperation("Creates the configuration for pre-existing resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK Created"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response createConfiguration(@PathParam("resourceTypeId") String resourceType,
                                        @PathParam("operationTypeId") String operationTypeId,
                                        @ApiParam(required = true)
                                        DataEntity.Blueprint<DataRole.OperationType> configuration,
                                        @Context UriInfo uriInfo) {

        return doCreateData(null, null, resourceType, operationTypeId, configuration, uriInfo);
    }

    @POST
    @javax.ws.rs.Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
    @ApiOperation("Creates the configuration for pre-existing resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK Created"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource type or feed doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response createConfiguration(@PathParam("feedId") String feedId,
                                        @PathParam("resourceTypeId") String resourceType,
                                        @PathParam("operationTypeId") String operationTypeId,
                                        @ApiParam(required = true)
                                        DataEntity.Blueprint<DataRole.OperationType> configuration,
                                        @Context UriInfo uriInfo) {

        return doCreateData(null, feedId, resourceType, operationTypeId, configuration, uriInfo);
    }

    @PUT
    @javax.ws.rs.Path("/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
    @ApiOperation("Updates the configuration of a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateData(@PathParam("resourceTypeId") String resourceType,
                               @PathParam("operationTypeId") String operationTypeId,
                               @QueryParam("dataType") @DefaultValue("returnType")
                               DataRole.OperationType dataType,
                               @ApiParam(required = true) DataEntity.Update data) {

        return doUpdateData(null, null, resourceType, operationTypeId, dataType, data);
    }

    @PUT
    @javax.ws.rs.Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
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
                               @QueryParam("dataType") @DefaultValue("returnType")
                               DataRole.OperationType dataType,
                               @ApiParam(required = true) DataEntity.Update data) {

        return doUpdateData(null, feedId, resourceType, operationTypeId, dataType, data);
    }

    @DELETE
    @javax.ws.rs.Path("/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
    @ApiOperation("Updates the configuration of a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteData(@PathParam("resourceTypeId") String resourceType,
                               @PathParam("operationTypeId") String operationTypeId,
                               @QueryParam("dataType") @DefaultValue("returnType")
                               DataRole.OperationType dataType) {

        return doDeleteData(null, null, resourceType, operationTypeId, dataType);
    }

    @DELETE
    @javax.ws.rs.Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
    @ApiOperation("Updates the configuration of a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteData(@PathParam("feedId") String feedId,
                               @PathParam("resourceTypeId") String resourceType,
                               @PathParam("operationTypeId") String operationTypeId,
                               @QueryParam("dataType") @DefaultValue("returnType")
                               DataRole.OperationType dataType) {

        return doDeleteData(null, feedId, resourceType, operationTypeId, dataType);
    }

    @GET
    @Path("/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
    @ApiOperation("Retrieves a single resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public DataEntity get(@PathParam("resourceTypeId") String resourceTypeId,
                          @PathParam("operationTypeId") String operationTypeId,
                          @QueryParam("dataType") @DefaultValue("returnType")
                          DataRole.OperationType dataType) {
        return doGetDataEntity(null, null, resourceTypeId, operationTypeId, dataType);
    }

    @GET
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/operationTypes/{operationTypeId}/data")
    @ApiOperation("Retrieves a single resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public DataEntity get(@PathParam("feedId") String feedId,
                          @PathParam("resourceTypeId") String resourceTypeId,
                          @PathParam("operationTypeId") String operationTypeId,
                          @QueryParam("dataType") @DefaultValue("returnType")
                          DataRole.OperationType dataType) {
        return doGetDataEntity(null, feedId, resourceTypeId, operationTypeId, dataType);
    }

    private Response doCreateData(String environmentId, String feedId, String resourceTypeId, String operationTypeId,
                                  DataEntity.Blueprint<DataRole.OperationType> blueprint, UriInfo uriInfo) {

        CanonicalPath operationType = getOperationTypePath(environmentId, feedId, resourceTypeId, operationTypeId);

        if (!security.canUpdate(operationType)) {
            return Response.status(FORBIDDEN).build();
        }
        DataEntity entity =
                inventory.inspect(operationType, OperationTypes.Single.class).data().create(blueprint).entity();

        return ResponseUtil.created(entity, uriInfo, blueprint.getRole().name()).build();

    }

    private Response doUpdateData(String environmentId, String feedId, String resourceTypeId, String operationTypeId,
                                  DataRole.OperationType dataType, DataEntity.Update update) {

        CanonicalPath operationType = getOperationTypePath(environmentId, feedId, resourceTypeId, operationTypeId);

        if (!security.canUpdate(operationType)) {
            return Response.status(FORBIDDEN).build();
        }
        inventory.inspect(operationType, OperationTypes.Single.class).data().update(dataType, update);

        return Response.noContent().build();
    }

    private Response doDeleteData(String environmentId, String feedId, String resourceTypeId, String operationTypeId,
                                  DataRole.OperationType dataType) {
        CanonicalPath operationType = getOperationTypePath(environmentId, feedId, resourceTypeId, operationTypeId);

        if (!security.canUpdate(operationType)) {
            return Response.status(FORBIDDEN).build();
        }
        inventory.inspect(operationType, OperationTypes.Single.class).data().delete(dataType);

        return Response.noContent().build();
    }

    private DataEntity doGetDataEntity(String environmentId, String feedId, String resourceTypeId,
                                       String operationTypeId, DataRole.OperationType dataType) {

        return inventory
                .inspect(getOperationTypePath(environmentId, feedId, resourceTypeId, operationTypeId),
                        OperationTypes.Single.class)
                .data().get(dataType).entity();
    }

    private CanonicalPath getOperationTypePath(String environmentId, String feedId, String resourceTypeId,
                                               String operationTypeId) {
        if (environmentId == null) {
            return CanonicalPath.of().tenant(getTenantId()).resourceType(resourceTypeId)
                    .operationType(operationTypeId).get();
        } else {
            return CanonicalPath.of().tenant(getTenantId()).feed(feedId).resourceType(resourceTypeId)
                    .operationType(operationTypeId).get();
        }
    }
}
