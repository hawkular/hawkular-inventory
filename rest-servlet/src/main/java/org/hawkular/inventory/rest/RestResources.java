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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.rest.filters.ResourceFilters;
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
@javax.ws.rs.Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/", description = "Resources CRUD")
public class RestResources extends RestBase {

    @POST
    @javax.ws.rs.Path("/{environmentId}/resources")
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

        CanonicalPath env = CanonicalPath.of().tenant(tenantId).environment(environmentId).get();

        if (!security.canCreate(Resource.class).under(env)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(env, Environments.Single.class).feedlessResources().create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    @POST
    @javax.ws.rs.Path("/{environmentId}/resources/{parentPath:.+}")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addResource(@PathParam("environmentId") String environmentId,
            @PathParam("parentPath") String parentPath, @ApiParam(required = true) Resource.Blueprint resource,
            @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, parentPath);

        if (!security.canCreate(Resource.class).under(parent)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(parent, Resources.Single.class).containedChildren().create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    @POST
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addFeedResource(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @ApiParam(required = true) Resource.Blueprint resource,
            @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath feed = CanonicalPath.of().tenant(tenantId).environment(environmentId).feed(feedId).get();

        if (!security.canCreate(Resource.class).under(feed)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(feed, Feeds.Single.class).resources().create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    @POST
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{parentPath:.+}")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addFeedResource(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("parentPath") String parentPath,
            @ApiParam(required = true) Resource.Blueprint resource, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, feedId, parentPath);

        if (!security.canCreate(Resource.class).under(parent)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(parent, Resources.Single.class).containedChildren().create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    // TODO the is one of the few bits of querying in the API. How should we go about it generally?
    // Copy the approach taken here on appropriate places or go with something more generic like a textual
    // representation of our Java API?
    @GET
    @javax.ws.rs.Path("/{environmentId}/resources")
    @ApiOperation("Retrieves resources in the environment, optionally filtering by resource type. Accepts paging " +
            "query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResources(@PathParam("environmentId") String environmentId,
            @QueryParam("type") String typeId, @QueryParam("feedless") @DefaultValue("false") boolean feedless,
            @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        Environments.Single envs = inventory.tenants().get(tenantId).environments().get(environmentId);

        ResolvingToMultiple<Resources.Multiple> rr = feedless ? envs.feedlessResources() : envs.allResources();
        Pager pager = extractPaging(uriInfo);
        ResourceFilters filters = new ResourceFilters(tenantId, uriInfo.getQueryParameters());
        Page<Resource> rs = rr.getAll(filters.get()).entities(pager);
        return pagedResponse(Response.ok(), uriInfo, rs).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources")
    @ApiOperation("Retrieves resources in the feed, optionally filtering by resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResources(@PathParam("environmentId") String environmentId,
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
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}")
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
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}")
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

    @GET
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/children")
    @ApiOperation("Retrieves child resources of a resource. This can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getChildren(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).allChildren().getAll().entities(pager);

        return ResponseUtil.pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/children")
    @ApiOperation("Retrieves child resources of a resource. This can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getChildren(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).allChildren().getAll().entities(pager);

        return ResponseUtil.pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/parents")
    @ApiOperation("Retrieves parents resources of the resource. This can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getParents(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).parents().getAll().entities(pager);

        return ResponseUtil.pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/parents")
    @ApiOperation("Retrieves parent resources of a resource. This can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getParents(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).parents().getAll().entities(pager);

        return ResponseUtil.pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/parent")
    @ApiOperation("Retrieves the parent resources that contains the given resource. Such parent resource will not" +
            " exist for resources directly contained in an environment or a feed.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Resource getParent(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        return inventory.inspect(resource, Resources.Single.class).parent().entity();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/parent")
    @ApiOperation("Retrieves the parent resources that contains the given resource. Such parent resource will not" +
            " exist for resources directly contained in an environment or a feed.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment, feed or the resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Resource getParent(@PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        return inventory.inspect(resource, Resources.Single.class).parent().entity();
    }

    @POST
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/children")
    @ApiOperation("Associates given resources as children of a given resource.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response associateChildren(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @ApiParam("resources") Collection<Path> resources) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        Resources.ReadAssociate access = inventory.inspect(parent, Resources.Single.class).allChildren();

        resources.forEach(access::associate);

        return Response.noContent().build();
    }

    @POST
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/children")
    @ApiOperation("Associates given resources as children of a given resource.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response associateChildren(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourcePath") String resourcePath,
            @ApiParam("resources") Collection<Path> resources) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        Resources.ReadAssociate access = inventory.inspect(parent, Resources.Single.class).allChildren();

        resources.forEach(access::associate);

        return Response.noContent().build();
    }

    @SuppressWarnings("unchecked")
    @PUT
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}")
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

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (!security.canUpdate(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(resource, ResolvableToSingle.class).update(update);

        return Response.noContent().build();
    }

    @PUT
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}")
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

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        if (!security.canUpdate(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(resource, Resources.Single.class).update(update);

        return Response.noContent().build();
    }

    @DELETE
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}")
    @ApiOperation("Deletes a single resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteResource(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (!security.canDelete(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(resource, Resources.Single.class).delete();

        return Response.noContent().build();
    }

    @DELETE
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}")
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

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        if (!security.canDelete(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(resource, Resources.Single.class).delete();

        return Response.noContent().build();
    }

    @POST
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/metrics/")
    @ApiOperation("Associates a pre-existing metric with a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath,
            @ApiParam("A list of paths to metrics to be associated with the resource. They can either be canonical or" +
                    " relative to the resource.") Collection<String> metricPaths) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (!security.canAssociateFrom(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        Metrics.ReadAssociate metricDao = inventory.inspect(resource, Resources.Single.class).metrics();

        metricPaths.stream().map((p) -> Path.fromPartiallyUntypedString(p, tenant, resource, Metric.class))
                .forEach(metricDao::associate);

        return Response.noContent().build();
    }

    @POST
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics/")
    @ApiOperation("Associates a pre-existing metric with a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetrics(@PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId, @PathParam("resourcePath") String resourcePath,
            Collection<String> metricPaths) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        if (!security.canAssociateFrom(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        Metrics.ReadAssociate metricDao = inventory.inspect(resource, Resources.Single.class).metrics();

        metricPaths.stream().map((p) -> Path.fromPartiallyUntypedString(p, tenant, resource, Metric.class))
                .forEach(metricDao::associate);

        return Response.noContent().build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/metrics")
    @ApiOperation("Retrieves all metrics associated with a resource. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metrics"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetrics(@PathParam("environmentId") String environmentID,
            @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {
        CanonicalPath resource = composeCanonicalPath(getTenantId(), environmentID, null, resourcePath);
        Page<Metric> ms = inventory.inspect(resource, Resources.Single.class).metrics().getAll().entities(
                extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics")
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
        CanonicalPath resource = composeCanonicalPath(getTenantId(), environmentId, feedId, resourcePath);
        Page<Metric> ms = inventory.inspect(resource, Resources.Single.class).metrics().getAll().entities(
                extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Retrieves a single metric associated with a resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The resource"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetric(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @PathParam("metricPath") String metricPath,
            @QueryParam("canonical") @DefaultValue("false")
            @ApiParam("True if metric path should be considered canonical, false by default.")
            boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (isCanonical) {
            metricPath = "/" + metricPath;
        }

        Path mp = Path.fromPartiallyUntypedString(metricPath, tenant, rp, Metric.class);

        if (Security.isTenantEscapeAttempt(rp, mp)) {
            Response.status(FORBIDDEN).build();
        }

        Metric m = inventory.inspect(rp, Resources.Single.class).metrics().get(mp).entity();

        return Response.ok(m).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
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
            @PathParam("metricPath") String metricPath, @QueryParam("canonical") @DefaultValue("false")
    @ApiParam("True if metric path should be considered canonical, false by default.") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        if (isCanonical) {
            metricPath = "/" + metricPath;
        }

        Path mp = Path.fromPartiallyUntypedString(metricPath, tenant, rp, Metric.class);

        if (Security.isTenantEscapeAttempt(rp, mp)) {
            Response.status(FORBIDDEN).build();
        }

        Metric m = inventory.inspect(rp, Resources.Single.class).metrics().get(mp).entity();
        return Response.ok(m).build();
    }

    @DELETE
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Disassociates the given resource from the given metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetric(@PathParam("environmentId") String environmentId,
            @PathParam("resourcePath") String resourcePath, @PathParam("metricPath") String metricPath,
            @QueryParam("canonical") @DefaultValue("false")
            @ApiParam("True if metric path should be considered canonical, false by default.") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (!security.canAssociateFrom(rp)) {
            return Response.status(FORBIDDEN).build();
        }

        if (isCanonical) {
            metricPath = "/" + metricPath;
        }

        Path mp = Path.fromPartiallyUntypedString(metricPath, tenant, rp, Metric.class);

        if (Security.isTenantEscapeAttempt(rp, mp)) {
            Response.status(FORBIDDEN).build();
        }

        inventory.inspect(rp, Resources.Single.class).metrics().disassociate(mp);

        return Response.noContent().build();
    }

    @DELETE
    @javax.ws.rs.Path("/{environmentId}/{feedId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
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
            @PathParam("metricPath") String metricPath, @QueryParam("canonical") @DefaultValue("false")
    @ApiParam("True if metric path should be considered canonical, false by default.") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        if (!security.canAssociateFrom(rp)) {
            return Response.status(FORBIDDEN).build();
        }

        if (isCanonical) {
            metricPath = "/" + metricPath;
        }

        Path mp = Path.fromPartiallyUntypedString(metricPath, tenant, rp, Metric.class);

        if (Security.isTenantEscapeAttempt(rp, mp)) {
            Response.status(FORBIDDEN).build();
        }

        inventory.inspect(rp, Resources.Single.class).metrics().disassociate(mp);

        return Response.noContent().build();
    }

    private CanonicalPath composeCanonicalPath(String tenantId, String envId, String feedId, String resourcePath) {
        CanonicalPath.Extender bld = CanonicalPath.empty().extend(Tenant.class, tenantId)
                .extend(Environment.class, envId);

        if (feedId != null) {
            bld = bld.extend(Feed.class, feedId);
        }

        // split on every slash that is not preceded by the backward slash (escaped slash)
//        for (String rid : resourcePath.split("(?<=[^\\\\])/")) {
            bld = bld.extend(Resource.class, resourcePath);
//        }

        return bld.get();
    }

    private RelativePath getResourcePath(String path) {
        RelativePath.Extender ret = RelativePath.empty();

        for (String s : path.split("(?<=[^\\\\])/")) {
            ret.extend(Resource.class, s);
        }

        return ret.get();
    }
}
