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

import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.rest.json.ApiError;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Set;

import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.hawkular.inventory.rest.RequestUtil.extractPaging;
import static org.hawkular.inventory.rest.ResponseUtil.pagedResponse;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
@Path("/")
public class RestFeeds extends RestBase {

    @POST
    @Path("{tenantId}/{environmentId}/feeds")
    @ApiOperation("Registers a feed with the inventory, giving it a unique ID.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "OK", response = Feed.class),
            @ApiResponse(code = 400, message = "Invalid inputs", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response register(@PathParam("tenantId") String tenantId, @PathParam("environmentId") String environmentId,
            @ApiParam(required = true) Feed.Blueprint blueprint, @Context UriInfo uriInfo) {

        if (!security.canCreate(Feed.class).under(Environment.class, tenantId, environmentId)) {
            return Response.status(UNAUTHORIZED).build();
        }

        Feed feed = inventory.tenants().get(tenantId).environments().get(environmentId).feeds().create(blueprint)
                .entity();
        return ResponseUtil.created(uriInfo, feed.getId()).entity(feed).build();
    }

    @GET
    @Path("{tenantId}/{environmentId}/feeds")
    @ApiOperation("Return all the feeds registered with the inventory")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = Set.class),
            @ApiResponse(code = 400, message = "Invalid inputs", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@PathParam("tenantId") String tenantId, @PathParam("environmentId") String environmentId,
            @Context UriInfo uriInfo) {
        Page<Feed> ret = inventory.tenants().get(tenantId).environments().get(environmentId).feeds().getAll()
                .entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @Path("{tenantId}/{environmentId}/feeds/{feedId}")
    @ApiOperation("Return a single feed by its ID.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = Set.class),
            @ApiResponse(code = 400, message = "Invalid inputs", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Feed get(@PathParam("tenantId") String tenantId, @PathParam("environmentId") String environmentId,
            @PathParam("feedId") String feedId) {

        return inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).entity();
    }

    @PUT
    @Path("/{tenantId}/{environmentId}/feeds/{feedId}")
    @ApiOperation("Updates a metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or the feed doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The update failed because of invalid data"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response update(@PathParam("tenantId") String tenantId,
            @PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId, Feed.Update update) {

        if (!security.canUpdate(Feed.class, tenantId, environmentId, feedId)) {
            return Response.status(UNAUTHORIZED).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().update(feedId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}/{environmentId}/feeds/{feedId}")
    @ApiOperation("Deletes a feed")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or the feed doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The delete failed because it would make inventory invalid"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response delete(@PathParam("tenantId") String tenantId,
            @PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId) {

        if (!security.canDelete(Feed.class, tenantId, environmentId, feedId)) {
            return Response.status(UNAUTHORIZED).build();
        }

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().delete(feedId);
        return Response.noContent().build();
    }
}
