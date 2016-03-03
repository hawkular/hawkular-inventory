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

import static org.hawkular.inventory.rest.RequestUtil.extractPaging;

import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.rest.json.ApiError;
import org.hawkular.inventory.rest.security.EntityIdUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 0.5.0
 */
@javax.ws.rs.Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/", description = "Manages associations between environments and feeds", tags = "Environments Feeds")
public class RestEnvironmentsFeeds extends RestBase {

    @POST
    @javax.ws.rs.Path("/{environmentId}/feeds")
    @ApiOperation("Associates a pre-existing feed with an environment")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 400, message = "Feed is already associated with another environment",
                    response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant, environment or one of the feeds doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateFeeds(@PathParam("environmentId") String environmentId,
                                   @ApiParam("A list of paths to feeds to be associated with the environment. They" +
                                           " can either be canonical or relative to the environment.")
                                   Collection<String> feedPaths) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath env = tenant.extend(Environment.class, environmentId).get();

        if (!security.canAssociateFrom(env)) {
            return Response.status(FORBIDDEN).build();
        }

        Feeds.ReadAssociate feeds = inventory.inspect(env, Environments.Single.class).feeds();

        feedPaths.stream().map((p) -> Path.fromPartiallyUntypedString(p, tenant, env, Feed.class))
                .forEach(feeds::associate);

        return Response.noContent().build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/feeds")
    @ApiOperation("Retrieves all feeds associated with an environment. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of feeds"),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedFeeds(@PathParam("environmentId") String environmentId, @Context UriInfo uriInfo) {
        Page<Feed> ms = inventory.tenants().get(getTenantId()).environments().get(environmentId).feeds().getAll()
                .entities(extractPaging(uriInfo));
        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    @GET
    @javax.ws.rs.Path("/{environmentId}/feeds/{feedPath:.+}")
    @ApiOperation("Retrieves a single feed associated with an environment")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The feed"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment or feed does not exist or the feed is not associated with the" +
                            " environment", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedFeed(@PathParam("environmentId") String environmentId,
                                      @Encoded @PathParam("feedPath") String feedPath,
                                      @QueryParam("canonical") @DefaultValue("false")
                                      @ApiParam(
                                              "True if feed path should be considered canonical, false by default.")
                                      boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath env = CanonicalPath.of().tenant(tenantId).environment(environmentId).get();

        if (isCanonical) {
            feedPath = "/" + feedPath;
        }

        Path fp = Path.fromPartiallyUntypedString(feedPath, tenant, env, Feed.class);

        if (EntityIdUtils.isTenantEscapeAttempt(env, fp)) {
            Response.status(FORBIDDEN).build();
        }

        Feed f = inventory.inspect(env, Environments.Single.class).feeds().get(fp).entity();

        return Response.ok(f).build();
    }

    @DELETE
    @javax.ws.rs.Path("/{environmentId}/feeds/{feedPath:.+}")
    @ApiOperation("Disassociates the given resource from the given metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateFeed(@PathParam("environmentId") String environmentId,
                                     @Encoded @PathParam("feedPath") String feedPath,
                                     @QueryParam("canonical") @DefaultValue("false")
                                     @ApiParam(
                                             "True if metric path should be considered canonical, false by default.")
                                     boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath env = CanonicalPath.of().tenant(tenantId).environment(environmentId).get();

        if (isCanonical) {
            feedPath = "/" + feedPath;
        }

        Path fp = Path.fromPartiallyUntypedString(feedPath, tenant, env, Feed.class);

        if (EntityIdUtils.isTenantEscapeAttempt(env, fp)) {
            Response.status(FORBIDDEN).build();
        }

        inventory.inspect(env, Environments.Single.class).feeds().disassociate(fp);

        return Response.noContent().build();
    }
}
