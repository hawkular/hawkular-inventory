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
import org.hawkular.inventory.api.model.Metric;
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
@Api(value = "/", description = "Metrics CRUD")
public class RestMetrics {

    @Inject @ForRest
    private Inventory inventory;

    @POST
    @Path("/{tenantId}/{environmentId}/metrics")
    @ApiOperation("Creates a new metric in given environment")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Metric created"),
            @ApiResponse(code = 400, message = "Invalid inputs", response = ApiError.class),
            @ApiResponse(code = 409, message = "Metric already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response createMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @ApiParam(required = true) Metric.Blueprint metric,
                                 @Context UriInfo uriInfo) {

        if (metric == null) {
            throw new IllegalArgumentException("metric to create not specified");
        }

        if (metric.getId() == null) {
            throw new IllegalArgumentException("metric id not specified");
        }

        if (metric.getMetricTypeId() == null) {
            throw new IllegalArgumentException("metric type id not specified");
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).metrics().create(metric);

        return ResponseUtil.created(uriInfo, metric.getId()).build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    @ApiOperation("Retrieves a single metric")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or metrics doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Metric getMetric(@PathParam("tenantId") String tenantId,
                            @PathParam("environmentId") String environmentId,
                            @PathParam("metricId") String metricId) {

        return inventory.tenants().get(tenantId).environments().get(environmentId).metrics().get(metricId).entity();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/metrics")
    @ApiOperation("Retrieves all metrics in an environment")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Set<Metric> getMetrics(@PathParam("tenantId") String tenantId,
                                  @PathParam("environmentId") String environmentId) {

        return inventory.tenants().get(tenantId).environments().get(environmentId).metrics().getAll().entities();
    }

    @PUT
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    @ApiOperation("Updates a metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or the metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The update failed because of invalid data"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @PathParam("metricId") String metricId,
                                 Metric.Update update) {
        inventory.tenants().get(tenantId).environments().get(environmentId).metrics().update(metricId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    @ApiOperation("Deletes a metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or the metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The delete failed because it would make inventory invalid"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @PathParam("metricId") String metricId) {

        inventory.tenants().get(tenantId).environments().get(environmentId).metrics().delete(metricId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/relationships")
    @ApiOperation("Retrieves all relationships of given metric.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getEnvironmentRelations(@PathParam("tenantId") String tenantId,
                                            @PathParam("environmentId") String environmentId,
                                            @PathParam("metricId") String metricId,
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

        return Response.ok(inventory.tenants().get(tenantId).environments().get(environmentId).metrics().get(metricId)
                .relationships(directed).getAll(filters).entities()).build();
    }

    public static String getUrl(Metric metric) {
        return String.format("/%s/%s/metrics/%s", metric.getTenantId(), metric.getEnvironmentId(), metric.getId());
    }
}
