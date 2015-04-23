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
import org.hawkular.inventory.api.model.MetricType;
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
import java.util.Set;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @author jkremser
 * @since 0.0.1
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
@Api(value = "/", description = "Metric types CRUD")
public class RestMetricTypes {

    @Inject @ForRest
    private Inventory inventory;

    @GET
    @Path("/{tenantId}/metricTypes")
    @ApiOperation("Retrieves all metric types")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Set<MetricType> getAll(@PathParam("tenantId") String tenantId) {
        return inventory.tenants().get(tenantId).metricTypes().getAll().entities();
    }

    @GET
    @Path("/{tenantId}/metricTypes/{metricTypeId}")
    @ApiOperation("Retrieves a single metric type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or metric type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public MetricType get(@PathParam("tenantId") String tenantId, @PathParam("metricTypeId") String metricTypeId) {
        return inventory.tenants().get(tenantId).metricTypes().get(metricTypeId).entity();
    }

    @POST
    @Path("/{tenantId}/metricTypes")
    @ApiOperation("Creates a new metric type")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Metric type successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Metric type already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(@PathParam("tenantId") String tenantId,
                           @ApiParam(required = true) MetricType.Blueprint metricType,
                           @Context UriInfo uriInfo) {

        inventory.tenants().get(tenantId).metricTypes().create(metricType);

        return ResponseUtil.created(uriInfo, metricType.getId()).build();
    }

    @PUT
    @Path("/{tenantId}/metricTypes/{metricTypeId}")
    @ApiOperation("Updates a metric type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Metric type successfully updated"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("tenantId") String tenantId, @PathParam("metricTypeId") String metricTypeId,
                       @ApiParam(required = true) MetricType.Update update)
            throws Exception {

        inventory.tenants().get(tenantId).metricTypes().update(metricTypeId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}/metricTypes/{metricTypeId}")
    @ApiOperation("Deletes a metric type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Metric type successfully deleted"),
            @ApiResponse(code = 400, message = "Metric type cannot be deleted because of constraints on it",
                    response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or metric type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("tenantId") String tenantId,
                           @PathParam("metricTypeId") String metricTypeId) {

        inventory.tenants().get(tenantId).metricTypes().delete(metricTypeId);
        return Response.noContent().build();
    }

    public static String getUrl(MetricType metricType) {
        return String.format("/%s/metricTypes/%s", metricType.getTenantId(), metricType.getId());
    }

    @GET
    @Path("/{tenantId}/metricTypes/{metricTypeId}/relationships")
    @ApiOperation("Retrieves all relationships of given metric type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or metric type not found", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMetricTypeRelations(@PathParam("tenantId") String tenantId,
                                            @PathParam("metricTypeId") String metricTypeId,
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
        return Response.ok(inventory.tenants().get(tenantId).metricTypes().get(metricTypeId)
                .relationships(directed).getAll(filters).entities()).build();
    }
}
