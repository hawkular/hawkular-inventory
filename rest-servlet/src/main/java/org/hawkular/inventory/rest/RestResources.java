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

import java.util.Collection;

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

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.rest.filters.ResourceFilters;
import org.hawkular.inventory.rest.json.ApiError;


/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/", description = "Resources CRUD")
public class RestResources extends RestBase {

    @POST
    @Path("/{environmentId}/resources")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addResource(@PathParam("environmentId") String environmentId,
            @ApiParam(required = true) Resource.Blueprint resource, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        if (!security.canCreate(Resource.class).under(Environment.class, tenantId, environmentId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources()
                .create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    @POST
    @Path("/{environmentId}/{feedId}/resources")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addResource(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @ApiParam(required =  true) Resource.Blueprint resource, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        if (!security.canCreate(Resource.class).under(Feed.class, tenantId, environmentId, feedId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).resources()
                .create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    // TODO the is one of the few bits of querying in the API. How should we go about it generally?
    // Copy the approach taken here on appropriate places or go with something more generic like a textual
    // representation of our Java API?
    @GET
    @Path("/{environmentId}/resources")
    @ApiOperation("Retrieves resources in the environment, optionally filtering by resource type. Accepts paging " +
            "query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResourcesByType(@PathParam("environmentId") String environmentId,
            @QueryParam("type") String typeId, @QueryParam("typeVersion") String typeVersion,
            @QueryParam("feedless") @DefaultValue("false") boolean feedless, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        Environments.Single envs = inventory.tenants().get(tenantId).environments().get(environmentId);

        ResolvingToMultiple<Resources.Multiple> rr = feedless ? envs.feedlessResources() : envs.allResources();
        Pager pager = extractPaging(uriInfo);
        ResourceFilters filters = new ResourceFilters(tenantId, uriInfo.getQueryParameters());
        Page<Resource> rs = rr.getAll(filters.get()).entities(pager);
        return pagedResponse(Response.ok(), uriInfo, rs).build();
    }

    @GET
    @Path("/{environmentId}/{feedId}/resources")
    @ApiOperation("Retrieves resources in the feed, optionally filtering by resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResourcesByType(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        Resources.ReadWrite rr = inventory.tenants().get(tenantId).environments().get(environmentId)
                .feeds().get(feedId).resources();
        Pager pager = extractPaging(uriInfo);
        ResourceFilters filters = new ResourceFilters(tenantId, uriInfo.getQueryParameters());
        Page<Resource> rs = rr.getAll(filters.get()).entities(pager);
        return pagedResponse(Response.ok(), uriInfo, rs).build();
    }

    @GET
    @Path("/{environmentId}/resources/{resourceId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Resource getResource(@PathParam("environmentId") String environmentId, @PathParam("resourceId") String uid) {
        return inventory.tenants().get(getTenantId()).environments().get(environmentId).feedlessResources()
                .get(uid).entity();
    }

    @GET
    @Path("/{environmentId}/{feedId}/resources/{resourceId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Resource getResource(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourceId") String uid) {
        return inventory.tenants().get(getTenantId()).environments().get(environmentId).feeds().get(feedId).resources()
                .get(uid).entity();
    }

    @PUT
    @Path("/{environmentId}/resources/{resourceId}")
    @ApiOperation("Update a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("environmentId") String environmentId, @PathParam("resourceId") String resourceId,
            @ApiParam(required = true) Resource.Update update) {

        String tenantId = getTenantId();

        if (!security.canUpdate(Resource.class, tenantId, environmentId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources().update(resourceId,
                update);

        return Response.noContent().build();
    }

    @PUT
    @Path("/{environmentId}/{feedId}/resources/{resourceId}")
    @ApiOperation("Update a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourceId") String resourceId, @ApiParam(required = true) Resource.Update update) {

        String tenantId = getTenantId();

        if (!security.canUpdate(Resource.class, tenantId, environmentId, feedId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).resources()
                .update(resourceId, update);

        return Response.noContent().build();
    }

    @DELETE
    @Path("/{environmentId}/resources/{resourceId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteResource(@PathParam("environmentId") String environmentId,
            @PathParam("resourceId") String resourceId) {

        String tenantId = getTenantId();

        if (!security.canDelete(Resource.class, tenantId, environmentId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources().delete(resourceId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{environmentId}/{feedId}/resources/{resourceId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteResource(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourceId") String resourceId) {

        String tenantId = getTenantId();

        if (!security.canDelete(Resource.class, tenantId, environmentId, feedId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).resources()
                .delete(resourceId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{environmentId}/resources/{resourceId}/metrics/")
    @ApiOperation("Associates a pre-existing metric with a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("resourceId") String resourceId, Collection<String> metricIds) {

        String tenantId = getTenantId();

        if (!security.canAssociateFrom(Resource.class, tenantId, environmentId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }

        Metrics.ReadAssociate metricDao = inventory.tenants().get(tenantId).environments().get(environmentId)
                .feedlessResources().get(resourceId).metrics();

        metricIds.forEach(metricDao::associate);

        return Response.noContent().build();
    }

    @POST
    @Path("/{environmentId}/{feedId}/resources/{resourceId}/metrics/")
    @ApiOperation("Associates a pre-existing metric with a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourceId") String resourceId,
            Collection<String> metricIds) {

        String tenantId = getTenantId();

        if (!security.canAssociateFrom(Resource.class, tenantId, environmentId, feedId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }

        Metrics.ReadAssociate metricDao = inventory.tenants().get(tenantId).environments().get(environmentId)
                .feeds().get(feedId).resources().get(resourceId).metrics();

        metricIds.forEach(metricDao::associate);

        return Response.noContent().build();
    }

    @GET
    @Path("/{environmentId}/resources/{resourceId}/metrics")
    @ApiOperation("Retrieves all metrics associated with a resource. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metrics"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetrics(@PathParam("environmentId") String environmentID,
            @PathParam("resourceId") String resourceId, @Context UriInfo uriInfo) {
        Page<Metric> ms = inventory.tenants().get(getTenantId()).environments().get(environmentID)
                    .feedlessResources().get(resourceId).metrics().getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    @GET
    @Path("/{environmentId}/{feedId}/resources/{resourceId}/metrics")
    @ApiOperation("Retrieves all metrics associated with a resource. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metrics"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourceId") String resourceId, @Context UriInfo uriInfo) {
        Page<Metric> ms = inventory.tenants().get(getTenantId()).environments().get(environmentId)
                 .feeds().get(feedId).resources().get(resourceId).metrics().getAll()
                 .entities(extractPaging(uriInfo));
        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    @GET
    @Path("/{environmentId}/resources/{resourceId}/metrics/{metricId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The resource"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetric(@PathParam("environmentId") String environmentId,
            @PathParam("resourceId") String resourceId, @PathParam("metricId") String metricId) {
        Metric m = inventory.tenants().get(getTenantId()).environments().get(environmentId).feedlessResources()
                .get(resourceId).metrics().get(metricId).entity();
        return Response.ok(m).build();
    }

    @GET
    @Path("/{environmentId}/{feedId}/resources/{resourceId}/metrics/{metricId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The resource"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, feed, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetric(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourceId") String resourceId,
            @PathParam("metricId") String metricId) {
        Metric m = inventory.tenants().get(getTenantId()).environments().get(environmentId).feeds().get(feedId)
                .resources().get(resourceId).metrics().get(metricId).entity();
        return Response.ok(m).build();
    }

    @DELETE
    @Path("/{environmentId}/resources/{resourceId}/metrics/{metricId}")
    @ApiOperation("Disassociates the given resource from the given metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetric(@PathParam("environmentId") String environmentId,
            @PathParam("resourceId") String resourceId, @PathParam("metricId") String metricId) {

        String tenantId = getTenantId();

        if (!security.canAssociateFrom(Resource.class, tenantId, environmentId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }
        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources().get(resourceId)
                .metrics().disassociate(metricId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{environmentId}/{feedId}/resources/{resourceId}/metrics/{metricId}")
    @ApiOperation("Disassociates the given resource from the given metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, feed, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetric(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourceId") String resourceId,
            @PathParam("metricId") String metricId) {

        String tenantId = getTenantId();

        if (!security.canAssociateFrom(Resource.class, tenantId, environmentId, resourceId)) {
            return Response.status(FORBIDDEN).build();
        }
        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).resources()
                .get(resourceId).metrics().disassociate(metricId);
        return Response.noContent().build();
    }

}
