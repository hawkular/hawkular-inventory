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
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.rest.json.ApiError;
import org.hawkular.inventory.rest.json.ResourceJSON;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Collection;
import java.util.Set;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
@Api(value = "/", description = "Resources CRUD")
public class RestResources {

    @Inject @ForRest
    private Inventory inventory;

    @POST
    @Path("/{tenantId}/{environmentId}/resources")
    @ApiOperation("Creates a new resource")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully created"),
            @ApiResponse(code = 400, message = "Invalid input data", response = ApiError.class),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 409, message = "Resource already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addResource(@PathParam("tenantId") String tenantId,
                                @PathParam("environmentId") String environmentId,
                                @ApiParam(required =  true) ResourceJSON resource,
                                @Context UriInfo uriInfo) {

        Tenants.Single tb = inventory.tenants().get(tenantId);
        ResourceType rt = tb.resourceTypes().get(resource.getType().getId()).entity();

        Resource.Blueprint b = new Resource.Blueprint(resource.getId(), rt);

        inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                .create(b);

        return ResponseUtil.created(uriInfo, resource.getId()).build();
    }

    // TODO the is one of the few bits of querying in the API. How should we go about it generally?
    // Copy the approach taken here on appropriate places or go with something more generic like a textual
    // representation of our Java API?
    @GET
    @Path("/{tenantId}/{environmentId}/resources")
    @ApiOperation("Retrieves resources in the environment, optionally filtering by resource type")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getResourcesByType(@PathParam("tenantId") String tenantId,
                                       @PathParam("environmentId") String environmentId,
                                       @QueryParam("type") String typeId,
                                       @QueryParam("typeVersion") String typeVersion) {
        Resources.ReadWrite rr = inventory.tenants().get(tenantId).environments().get(environmentId).resources();

        Set<Resource> rs;
        if (typeId != null && typeVersion != null) {
            ResourceType rt = new ResourceType(tenantId, typeId, typeVersion);
            rs = rr.getAll(Defined.by(rt)).entities();
        } else {
            rs = rr.getAll().entities();
        }
        return Response.ok(rs).build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Resource getResource(@PathParam("tenantId") String tenantId,
                                @PathParam("environmentId") String environmentId, @PathParam("resourceId") String uid) {
        return inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                .get(uid).entity();
    }


    @DELETE
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteResource(@PathParam("tenantId") String tenantId,
                                   @PathParam("environmentId") String environmentId,
                                   @PathParam("resourceId") String resourceId) {
        inventory.tenants().get(tenantId).environments().get(environmentId).resources().delete(resourceId);
        return Response.noContent().build();
    }


    @POST
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics/")
    @ApiOperation("Associates a pre-existing metric with a resource")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response addMetricToResource(@PathParam("tenantId") String tenantId,
                                        @PathParam("environmentId") String environmentId,
                                        @PathParam("resourceId") String resourceId,
                                        Collection<String> metricIds) {
        Metrics.ReadAssociate metricDao = inventory.tenants().get(tenantId).environments().get(environmentId)
                .resources().get(resourceId).metrics();

        metricIds.forEach(metricDao::associate);

        return Response.noContent().build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics")
    @ApiOperation("Retrieves all metrics associated with a resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metrics"),
            @ApiResponse(code = 404, message = "Tenant, environment or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response listMetricsOfResource(@PathParam("tenantId") String tenantId,
                                          @PathParam("environmentId") String environmentID,
                                          @PathParam("resourceId") String resourceId) {
        Set<Metric> ms = inventory.tenants().get(tenantId).environments().get(environmentID)
                .resources().get(resourceId).metrics().getAll().entities();

        return Response.ok(ms).build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics/{metricId}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The resource"),
            @ApiResponse(code = 404,
//Eff you, 120 chars enforcer
message = "Tenant, environment, resource or metric doesn't exist or if the metric is not associated with the resource",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMetricOfResource(@PathParam("tenantId") String tenantId,
                                        @PathParam("environmentId") String environmentId,
                                        @PathParam("resourceId") String resourceId,
                                        @PathParam("metricId") String metricId) {
        Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).resources().get(resourceId)
                .metrics().get(metricId).entity();
        return Response.ok(m).build();
    }
}
