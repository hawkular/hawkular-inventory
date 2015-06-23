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
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.model.AbstractPath;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
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
        Page<Resource> rs;
        if (typeId != null && typeVersion != null) {
            rs = rr.getAll(Defined.by(CanonicalPath.of().tenant(tenantId).resourceType(typeId).get())).entities(pager);
        } else {
            rs = rr.getAll().entities(pager);
        }
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
            @PathParam("feedId") String feedId, @QueryParam("type") String typeId,
            @QueryParam("typeVersion") String typeVersion, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        Resources.ReadWrite rr = inventory.tenants().get(tenantId).environments().get(environmentId)
                .feeds().get(feedId).resources();
        Pager pager = extractPaging(uriInfo);
        Page<Resource> rs;
        if (typeId != null && typeVersion != null) {
            rs = rr.getAll(Defined.by(CanonicalPath.of().tenant(tenantId).resourceType(typeId).get())).entities(pager);
        } else {
            rs = rr.getAll().entities(pager);
        }
        return pagedResponse(Response.ok(), uriInfo, rs).build();
    }

    @GET
    @Path("/{environmentId}/resources/{resourcePath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Resource getResource(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath) {
        return inventory.tenants().get(getTenantId()).environments().get(environmentId).feedlessResources()
                .get(resourcePath).entity();
    }

    @GET
    @Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Resource getResource(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourcePath") String resourcePath) {
        return inventory.tenants().get(getTenantId()).environments().get(environmentId).feeds().get(feedId).resources()
                .get(resourcePath).entity();
    }

    @PUT
    @Path("/{environmentId}/resources/{resourcePath:.+}")
    @ApiOperation("Update a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @ApiParam(required = true) Resource.Update update) {

        String tenantId = getTenantId();

        if (!security.canUpdate(Resource.class, tenantId, environmentId, resourcePath)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources().update(resourcePath,
                update);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}")
    @ApiOperation("Update a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourcePath") String resourcePath, @ApiParam(required = true) Resource.Update update) {

        String tenantId = getTenantId();

        if (!security.canUpdate(Resource.class, tenantId, environmentId, feedId, resourcePath)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).resources()
                .update(resourcePath, update);

        return Response.noContent().build();
    }

    @DELETE
    @Path("/{environmentId}/resources/{resourcePath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteResource(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        if (!security.canDelete(Resource.class, tenantId, environmentId, resourcePath)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources().delete(resourcePath);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteResource(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        if (!security.canDelete(Resource.class, tenantId, environmentId, feedId, resourcePath)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).resources()
                .delete(resourcePath);
        return Response.noContent().build();
    }

    @POST
    @Path("/{environmentId}/resources/{resourcePath:.+}/metrics/")
    @ApiOperation("Associates a pre-existing metric with a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, Collection<CanonicalPath> metricIds) {

        String tenantId = getTenantId();

        if (!security.canAssociateFrom(composeCanonicalPath(tenantId, environmentId, null, resourcePath))) {
            return Response.status(FORBIDDEN).build();
        }

        Metrics.ReadAssociate metricDao = inventory.tenants().get(tenantId).environments().get(environmentId)
                .feedlessResources().get(resourcePath).metrics();

        metricIds.forEach(metricDao::associate);

        return Response.noContent().build();
    }

    @POST
    @Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics/")
    @ApiOperation("Associates a pre-existing metric with a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourcePath") String resourcePath,
            Collection<CanonicalPath> metricPaths) {

        String tenantId = getTenantId();

        if (!security.canAssociateFrom(composeCanonicalPath(tenantId, environmentId, feedId, resourcePath))) {
            return Response.status(FORBIDDEN).build();
        }

        Metrics.ReadAssociate metricDao = inventory.tenants().get(tenantId).environments().get(environmentId)
                .feeds().get(feedId).resources().get(resourcePath).metrics();

        metricPaths.forEach(metricDao::associate);

        return Response.noContent().build();
    }

    @GET
    @Path("/{environmentId}/resources/{resourcePath:.+}/metrics")
    @ApiOperation("Retrieves all metrics associated with a resource. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metrics"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetrics(@PathParam("environmentId") String environmentID,
            @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {
        Page<Metric> ms = inventory.tenants().get(getTenantId()).environments().get(environmentID)
                .feedlessResources().get(resourcePath).metrics().getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    @GET
    @Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics")
    @ApiOperation("Retrieves all metrics associated with a resource. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metrics"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourcePath") String resourcePath,
            @Context UriInfo uriInfo) {
        Page<Metric> ms = inventory.tenants().get(getTenantId()).environments().get(environmentId)
                .feeds().get(feedId).resources().get(resourcePath).metrics().getAll()
                 .entities(extractPaging(uriInfo));
        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    // TODO we need to be able to address the metric more intelligently - figure out how to do relative addressing in
    // REST as we do in Java API
    @GET
    @Path("/{environmentId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Retrieves a single metric associated with a resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The resource"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetric(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @PathParam("metricPath") RelativePath metricPath) {

        String tenantId = getTenantId();

        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (Security.isTenantEscapeAttempt(rp, metricPath)) {
            Response.status(FORBIDDEN).build();
        }

        Metric m = inventory.inspect(rp, Resources.Single.class).metrics().get(metricPath).entity();

        return Response.ok(m).build();
    }

    @GET
    @Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The resource"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, feed, resource or metric doesn't exist or if the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetric(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourcePath") String resourcePath,
            @PathParam("metricPath") RelativePath metricPath) {
        String tenantId = getTenantId();

        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        if (Security.isTenantEscapeAttempt(rp, metricPath)) {
            Response.status(FORBIDDEN).build();
        }

        Metric m = inventory.inspect(rp, Resources.Single.class).metrics().get(metricPath).entity();
        return Response.ok(m).build();
    }

    @DELETE
    @Path("/{environmentId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Disassociates the given resource from the given metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetric(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @PathParam("metricPath") RelativePath metricPath) {

        String tenantId = getTenantId();

        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (!security.canAssociateFrom(rp)) {
            return Response.status(FORBIDDEN).build();
        }

        if (Security.isTenantEscapeAttempt(rp, metricPath)) {
            Response.status(FORBIDDEN).build();
        }

        inventory.inspect(rp, Resources.Single.class).metrics().disassociate(metricPath);

        return Response.noContent().build();
    }

    @DELETE
    @Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Disassociates the given resource from the given metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, feed, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetric(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourcePath") String resourcePath,
            @PathParam("metricPath") RelativePath metricPath) {

        String tenantId = getTenantId();

        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        if (!security.canAssociateFrom(rp)) {
            return Response.status(FORBIDDEN).build();
        }

        if (Security.isTenantEscapeAttempt(rp, metricPath)) {
            Response.status(FORBIDDEN).build();
        }

        inventory.inspect(rp, Resources.Single.class).metrics().disassociate(metricPath);

        return Response.noContent().build();
    }

    private CanonicalPath composeCanonicalPath(String tenantId, String envId, String feedId, String resourcePath) {
        CanonicalPath.Extender<CanonicalPath> bld = CanonicalPath.empty().extend(Tenant.class, tenantId)
                .extend(Environment.class, envId);

        if (feedId != null) {
            bld = bld.extend(Feed.class, feedId);
        }

        for (String rid : resourcePath.split("/")) {
            bld = bld.extend(Resource.class, rid);
        }

        return bld.get();
    }

    private static RelativePath resourcePath(String resourcePath) {
        AbstractPath.Extender<RelativePath> ret = RelativePath.empty();

        for (String id : resourcePath.split("/")) {
            ret.extend(Resource.class, id);
        }

        return ret.get();
    }
}
