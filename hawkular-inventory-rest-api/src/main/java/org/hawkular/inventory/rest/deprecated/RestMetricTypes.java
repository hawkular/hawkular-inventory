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

import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Parents;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.RequestUtil;
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
 * @since 0.0.1
 */
@Path("/deprecated")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
@Api(value = "/deprecated", description = "Metric types CRUD", tags = {"Deprecated"})
public class RestMetricTypes extends RestBase {

    @GET
    @Path("/metricTypes")
    @ApiOperation("Retrieves all metric types. Accepts paging query parameters")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@QueryParam("feedless") @DefaultValue("false") boolean feedless, @Context UriInfo uriInfo) {

        Tenants.Single tenants = inventory.tenants().get(getTenantId());

        Page<MetricType> ret = (feedless ? tenants.metricTypes() : tenants.metricTypesUnder(Parents.any()))
                .getAll().entities(RequestUtil.extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @Path("/metricTypes/{metricTypeId}")
    @ApiOperation("Retrieves a single metric type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Metric type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public MetricType get(@PathParam("metricTypeId") String metricTypeId) {
        return inventory.tenants().get(getTenantId()).metricTypes().get(metricTypeId).entity();
    }

    @GET
    @Path("/feeds/{feedId}/metricTypes")
    @ApiOperation("Retrieves all metric types under feed. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Metric type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@PathParam("feedId") String feedId, @Context UriInfo uriInfo) {

        Page<MetricType> ret = inventory.tenants().get(getTenantId()).feeds().get(feedId).metricTypes().getAll()
                .entities(extractPaging(uriInfo));
        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @Path("/feeds/{feedId}/metricTypes/{metricTypeId}")
    @ApiOperation("Retrieves a single metric type under feed")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Metric type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public MetricType get(@PathParam("feedId") String feedId, @PathParam("metricTypeId") String metricTypeId) {

        return inventory.tenants().get(getTenantId()).feeds().get(feedId).metricTypes().get(metricTypeId).entity();
    }

    @POST
    @Path("/metricTypes")
    @ApiOperation("Creates a new metric type")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Metric type successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Metric type already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(@ApiParam(required = true) MetricType.Blueprint metricType, @Context UriInfo uriInfo) {
        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();

        if (!security.canCreate(MetricType.class).under(tenant)) {
            return Response.status(FORBIDDEN).build();
        }

        MetricType entity = createMetricType(inventory.inspect(tenant, Tenants.Single.class).metricTypes(), metricType);

        return ResponseUtil.created(entity, uriInfo, metricType.getId()).build();
    }

    @POST
    @Path("/feeds/{feedId}/metricTypes")
    @ApiOperation("Creates a new metric type under feed")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Metric type successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Metric type already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response create(@PathParam("feedId") String feedId,
                           @ApiParam(required = true) MetricType.Blueprint metricType, @Context UriInfo uriInfo) {
        String tenantId = getTenantId();

        CanonicalPath feed = CanonicalPath.of().tenant(tenantId).feed(feedId).get();

        if (!security.canCreate(MetricType.class).under(feed)) {
            return Response.status(FORBIDDEN).build();
        }

        MetricType entity = createMetricType(inventory.inspect(feed, Feeds.Single.class).metricTypes(), metricType);

        return ResponseUtil.created(entity, uriInfo, metricType.getId()).build();
    }

    private MetricType createMetricType(MetricTypes.ReadWrite accessInterface, MetricType.Blueprint metricType) {
        if (metricType == null) {
            throw new IllegalArgumentException("metricType to create not specified");
        }

        if (metricType.getId() == null) {
            throw new IllegalArgumentException("metricType id not specified");
        }

        return accessInterface.create(metricType).entity();
    }

    @PUT
    @Path("/metricTypes/{metricTypeId}")
    @ApiOperation("Updates a metric type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Metric type successfully updated"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("metricTypeId") String metricTypeId,
            @ApiParam(required = true) MetricType.Update update) throws Exception {

        String tenantId = getTenantId();

        if (!security.canUpdate(CanonicalPath.of().tenant(tenantId).metricType(metricTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).metricTypes().update(metricTypeId, update);
        return Response.noContent().build();
    }

    @PUT
    @Path("/feeds/{feedId}/metricTypes/{metricTypeId}")
    @ApiOperation("Updates a metric type under feed")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Metric type successfully updated"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("feedId") String feedId,
                           @PathParam("metricTypeId") String metricTypeId,
                           @ApiParam(required = true) MetricType.Update update) throws Exception {

        String tenantId = getTenantId();

        if (!security.canUpdate(CanonicalPath.of().tenant(tenantId).feed(feedId).metricType(metricTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).feeds().get(feedId).metricTypes().update(metricTypeId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/metricTypes/{metricTypeId}")
    @ApiOperation("Deletes a metric type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Metric type successfully deleted"),
            @ApiResponse(code = 400, message = "Metric type cannot be deleted because of constraints on it",
                    response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or metric type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("metricTypeId") String metricTypeId) {

        String tenantId = getTenantId();

        if (!security.canDelete(CanonicalPath.of().tenant(tenantId).metricType(metricTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).metricTypes().delete(metricTypeId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/feeds/{feedId}/metricTypes/{metricTypeId}")
    @ApiOperation("Deletes a metric type under feed")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Metric type successfully deleted"),
            @ApiResponse(code = 400, message = "Metric type cannot be deleted because of constraints on it",
                         response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or metric type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("feedId") String feedId,
                           @PathParam("metricTypeId") String metricTypeId) {

        String tenantId = getTenantId();

        if (!security.canDelete(CanonicalPath.of().tenant(tenantId).feed(feedId).metricType(metricTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).feeds().get(feedId).metricTypes().delete(metricTypeId);
        return Response.noContent().build();
    }
}
