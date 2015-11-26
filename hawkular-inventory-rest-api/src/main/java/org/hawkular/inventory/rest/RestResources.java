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

import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.rest.RequestUtil.extractPaging;

import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
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
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Path;
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

        inventory.inspect(env, Environments.Single.class).resources().create(resource);

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
            @Encoded @PathParam("parentPath") String parentPath, @ApiParam(required = true) Resource.Blueprint resource,
            @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, parentPath);

        if (!security.canCreate(Resource.class).under(parent)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(parent, Resources.Single.class).resources().create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    @POST
    @javax.ws.rs.Path("/feeds/{feedId}/resources")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addFeedResource(@PathParam("feedId") String feedId,
                                    @ApiParam(required = true) Resource.Blueprint resource, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath feed = CanonicalPath.of().tenant(tenantId).feed(feedId).get();

        if (!security.canCreate(Resource.class).under(feed)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(feed, Feeds.Single.class).resources().create(resource);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    @POST
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{parentPath:.+}")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addFeedResource(@PathParam("feedId") String feedId,
                                    @Encoded @PathParam("parentPath") String parentPath,
                                    @ApiParam(required = true) Resource.Blueprint resource, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, null, feedId, parentPath);

        if (!security.canCreate(Resource.class).under(parent)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(parent, Resources.Single.class).resources().create(resource);

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

        ResolvingToMultiple<Resources.Multiple> rr = feedless ? envs.resources() : envs.resources();
        Pager pager = extractPaging(uriInfo);
        ResourceFilters filters = new ResourceFilters(tenantId, uriInfo.getQueryParameters());
        Page<Resource> rs = rr.getAll(filters.get()).entities(pager);
        return pagedResponse(Response.ok(), uriInfo, rs).build();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources")
    @ApiOperation("Retrieves resources in the feed, optionally filtering by resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResources(@PathParam("feedId") String feedId, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        Resources.ReadWrite rr = inventory.tenants().get(tenantId).feeds().get(feedId).resources();
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
            @Encoded @PathParam("resourcePath") String resourcePath) {
        CanonicalPath res = composeCanonicalPath(getTenantId(), environmentId, null, resourcePath);
        return inventory.inspect(res, Resources.Single.class).entity();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Resource getResourceInFeed(@PathParam("feedId") String feedId,
                                      @Encoded @PathParam("resourcePath") String resourcePath) {
        CanonicalPath res = composeCanonicalPath(getTenantId(), null, feedId, resourcePath);
        return inventory.inspect(res, Resources.Single.class).entity();
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
            @Encoded @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).allResources().getAll().entities(pager);

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/children")
    @ApiOperation("Retrieves child resources of a resource. This can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getChildrenInFeed(@PathParam("feedId") String feedId,
                                      @Encoded @PathParam("resourcePath") String resourcePath,
                                      @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).allResources().getAll().entities(pager);

        return pagedResponse(Response.ok(), uriInfo, ret).build();
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
            @Encoded @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).parents().getAll().entities(pager);

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/parents")
    @ApiOperation("Retrieves parent resources of a resource. This can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getParentsInFeed(@PathParam("feedId") String feedId,
            @Encoded @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath parent = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        Pager pager = extractPaging(uriInfo);

        Page<Resource> ret = inventory.inspect(parent, Resources.Single.class).parents().getAll().entities(pager);

        return pagedResponse(Response.ok(), uriInfo, ret).build();
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
            @Encoded @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        return inventory.inspect(resource, Resources.Single.class).parent().entity();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/parent")
    @ApiOperation("Retrieves the parent resources that contains the given resource. Such parent resource will not" +
            " exist for resources directly contained in an environment or a feed.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment, feed or the resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Resource getParentInFeed(@PathParam("feedId") String feedId,
                                    @Encoded @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, null, feedId, resourcePath);

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
            @Encoded @PathParam("resourcePath") String resourcePath,
            @ApiParam("resources") Collection<String> resources) {

        String tenantId = getTenantId();

        CanonicalPath tenantPath = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        Resources.ReadAssociate access = inventory.inspect(parent, Resources.Single.class).allResources();

        resources.stream().map((p) -> Path.fromPartiallyUntypedString(p, tenantPath, parent, Resource.class))
                .forEach(access::associate);

        return Response.noContent().build();
    }

    @POST
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/children")
    @ApiOperation("Associates given resources as children of a given resource.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response associateChildrenInFeed(@PathParam("feedId") String feedId,
                                            @Encoded @PathParam("resourcePath") String resourcePath,
                                            @ApiParam("resources") Collection<String> resources) {

        String tenantId = getTenantId();

        CanonicalPath tenantPath = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath parent = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        Resources.ReadAssociate access = inventory.inspect(parent, Resources.Single.class).allResources();

        resources.stream().map((p) -> Path.fromPartiallyUntypedString(p, tenantPath, parent, Resource.class))
                .forEach(access::associate);

        return Response.noContent().build();
    }

    @DELETE
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/children/{childPath:.+}")
    @ApiOperation("Disassociates given child resource from given resource. The the resource doesn't own the child, " +
            "the child will no longer be considered a child of the resource, otherwise an error will be returned.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "environment, the parent resource or the child resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response disassociateChild(@PathParam("environmentId") String environmentId,
            @Encoded @PathParam("resourcePath") String resourcePath,
            @Encoded @PathParam("childPath") String childPath,
            @QueryParam("canonical") @DefaultValue("false") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenantPath = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (isCanonical) {
            childPath = "/" + childPath;
        }

        Path child = Path.fromPartiallyUntypedString(childPath, tenantPath, parent, Resource.class);

        inventory.inspect(parent, Resources.Single.class).allResources().disassociate(child);

        return Response.noContent().build();
    }

    @DELETE
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/children/{childPath:.+}")
    @ApiOperation("Disassociates given child resource from given resource. The the resource doesn't own the child, " +
            "the child will no longer be considered a child of the resource, otherwise an error will be returned.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "feed, the parent resource or the child resource not " +
                    "found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response disassociateChildInFeed(@PathParam("feedId") String feedId,
                                            @Encoded @PathParam("resourcePath") String resourcePath,
                                            @Encoded @PathParam("childPath") String childPath,
                                            @QueryParam("canonical") @DefaultValue("false") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenantPath = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath parent = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        if (isCanonical) {
            childPath = "/" + childPath;
        }

        Path child = Path.fromPartiallyUntypedString(childPath, tenantPath, parent, Resource.class);

        inventory.inspect(parent, Resources.Single.class).allResources().disassociate(child);

        return Response.noContent().build();
    }


    @PUT
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}")
    @ApiOperation("Update a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("environmentId") String environmentId,
            @Encoded @PathParam("resourcePath") String resourcePath,
            @ApiParam(required = true) Resource.Update update) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (!security.canUpdate(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(resource, Resources.Single.class).update(update);

        return Response.noContent().build();
    }

    @PUT
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}")
    @ApiOperation("Update a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Resource doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateInFeed(@PathParam("feedId") String feedId,
                                 @Encoded @PathParam("resourcePath") String resourcePath,
                                 @ApiParam(required = true) Resource.Update update) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, null, feedId, resourcePath);

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
            @Encoded @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (!security.canDelete(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(resource, Resources.Single.class).delete();

        return Response.noContent().build();
    }

    @DELETE
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteResourceInFeed(@PathParam("feedId") String feedId,
            @Encoded @PathParam("resourcePath") String resourcePath) {

        String tenantId = getTenantId();

        CanonicalPath resource = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        if (!security.canDelete(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.inspect(resource, Resources.Single.class).delete();

        return Response.noContent().build();
    }

    protected CanonicalPath composeCanonicalPath(String tenantId, String envId, String feedId, String resourcePath) {
        CanonicalPath.Extender parent = CanonicalPath.empty().extend(Tenant.class, tenantId);

        if (feedId != null) {
            parent = parent.extend(Feed.class, feedId);
        } else {
            parent = parent.extend(Environment.class, envId);
        }

        return CanonicalPath.fromPartiallyUntypedString(parent.get().toString() + "/" + resourcePath, parent.get(),
                Resource.class);
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/recursiveChildren")
    @ApiOperation("Recursively retrieves child resources of a resource of given type. Can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getRecursiveChildren(@PathParam("environmentId") String environmentId,
                                @Encoded @PathParam("resourcePath") String resourcePath,
                                @Encoded @QueryParam("typeId") String resourceTypeId,
                                         @Context UriInfo uriInfo) {

        Page<Resource> rs = getRecursiveChildren(environmentId, null, resourcePath, resourceTypeId, true,
                extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, rs).build();
    }


    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/recursiveChildren")
    @ApiOperation("Recursively retrieves child resources of a resource of given type. Can be paged.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "A list of child resources"),
            @ApiResponse(code = 404, message = "environment or the parent resource not found"),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getRecursiveChildren(@PathParam("feedId") String feedId,
                                         @Encoded @PathParam("resourcePath") String resourcePath,
                                         @Encoded @QueryParam("typeId") String resourceTypeId,
                                         @QueryParam("feedlessType") @DefaultValue("false") boolean feedlessType,
                                         @Context UriInfo uriInfo) {

        Page<Resource> rs = getRecursiveChildren(null, feedId, resourcePath, resourceTypeId, feedlessType,
                extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, rs).build();
    }

    private Page<Resource> getRecursiveChildren(String environmentId, String feedId, String
            resourcePath, String resourceTypeId, boolean feedlessResourceType, Pager pager) {

        String tenantId = getTenantId();
        CanonicalPath parent = composeCanonicalPath(tenantId, environmentId, feedId, resourcePath);

        Filter[][] resourceFilter = new Filter[0][];

        if (resourceTypeId != null) {
            CanonicalPath resourceTypePath = feedlessResourceType
                    ? CanonicalPath.of().tenant(tenantId).resourceType(resourceTypeId).get()
                    : CanonicalPath.of().tenant(tenantId).feed(feedId).resourceType(resourceTypeId).get();

            resourceFilter = new Filter[][]{{Related.asTargetBy(defines), With.path(resourceTypePath)}};
        }

        return inventory.inspect(parent, Resources.Single.class).recursiveResources().getAll(resourceFilter)
                .entities(pager);
    }
}
