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

import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.RestBase;
import org.hawkular.inventory.rest.json.ApiError;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;


/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
@Path("/deprecated")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/deprecated", description = "Manages associations between resource types and metric types",
        tags = {"Deprecated"})
public class RestResourceTypesMetricTypes extends RestBase {

    @GET
    @Path("/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Retrieves all metric types associated with the resource type. Accepts paging query params.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of metric types associated with the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMetricTypes(@PathParam("resourceTypeId") String resourceTypeId, @Context UriInfo uriInfo) {
        Page<MetricType> ret = inventory.tenants().get(getTenantId()).resourceTypes().
                get(resourceTypeId).metricTypes().getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @POST
    @Path("/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Associates a pre-existing metric type with a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, resource type or metric type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetricTypes(@PathParam("resourceTypeId") String resourceTypeId,
                                         @ApiParam("A list of paths to metric types to be associated with the" +
                                                 " resource type. They can either be canonical or relative to the" +
                                                 " resource type.")
                                         Collection<String> metricTypePaths) {
        String tenantId = getTenantId();

        if (!security.canAssociateFrom(CanonicalPath.of().tenant(tenantId).resourceType(resourceTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rt = tenant.extend(ResourceType.SEGMENT_TYPE, resourceTypeId).get();

        MetricTypes.ReadAssociate
                metricTypesDao = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                .metricTypes();

        metricTypePaths.stream()
                .map((p) -> org.hawkular.inventory.paths.Path.fromPartiallyUntypedString(p, tenant, rt,
                        MetricType.SEGMENT_TYPE)).forEach(metricTypesDao::associate);

        return Response.noContent().build();
    }

    @GET
    @javax.ws.rs.Path("/resourceTypes/{resourceTypeId}/metricTypes/{metricTypePath:.+}")
    @ApiOperation("Retrieves the given metric type associated with the given resource type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metric types"),
            @ApiResponse(code = 404, message = "Tenant or resource type does not exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public MetricType getAssociatedMetricType(@PathParam("resourceTypeId") String resourceTypeId,
                                              @Encoded @PathParam("metricTypePath") String metricTypePath,
                                              @QueryParam("canonical") @DefaultValue("false")
                                              @ApiParam("True if metric type path should be considered canonical," +
                                                      " false by default.")
                                              boolean isCanonical) {

        CanonicalPath tenant = CanonicalPath.of().tenant(getTenantId()).get();
        CanonicalPath rt = tenant.extend(ResourceType.SEGMENT_TYPE, resourceTypeId).get();

        if (isCanonical) {
            metricTypePath = "/" + metricTypePath;
        }

        org.hawkular.inventory.paths.Path mtPath = org.hawkular.inventory.paths.Path
                .fromPartiallyUntypedString(metricTypePath, tenant, rt, MetricType.SEGMENT_TYPE);

        return inventory.inspect(rt, ResourceTypes.Single.class).metricTypes().get(mtPath).entity();
    }

    @GET
    @Path("/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Retrieves metric types associated with the given resource type. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metric types"),
            @ApiResponse(code = 404, message = "Tenant or resource type does not exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetricTypes(@PathParam("resourceTypeId") String resourceTypeId,
                                             @Context UriInfo uriInfo) {
        String tenantId = getTenantId();
        Page<MetricType> mTypes = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                .metricTypes().getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, mTypes).build();
    }

    @DELETE
    @Path("/resourceTypes/{resourceTypeId}/metricTypes/{metricTypePath:.+}")
    @ApiOperation("Disassociates the given resource type from the given metric type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or resource type does not exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetricType(@PathParam("resourceTypeId") String resourceTypeId,
                                           @Encoded @PathParam("metricTypePath") String metricTypePath,
                                           @QueryParam("canonical") @DefaultValue("false")
                                           @ApiParam("True if metric path should be considered canonical, false by" +
                                                   " default.")
                                           boolean isCanonical) {

        CanonicalPath tenant = CanonicalPath.of().tenant(getTenantId()).get();
        CanonicalPath rt = tenant.extend(ResourceType.SEGMENT_TYPE, resourceTypeId).get();

        if (!security.canAssociateFrom(rt)) {
            return Response.status(FORBIDDEN).build();
        }

        if (isCanonical) {
            metricTypePath = "/" + metricTypePath;
        }

        org.hawkular.inventory.paths.Path mtPath = org.hawkular.inventory.paths.Path
                .fromPartiallyUntypedString(metricTypePath, tenant, rt, MetricType.SEGMENT_TYPE);

        inventory.inspect(rt, ResourceTypes.Single.class).metricTypes().disassociate(mtPath);

        return Response.noContent().build();
    }

    @GET
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Retrieves all metric types associated with the resource type. Accepts paging query params.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "the list of metric types associated with the resource type"),
            @ApiResponse(code = 404, message = "Tenant or resource type doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMetricTypes(@PathParam("feedId") String feedId,
                                   @PathParam("resourceTypeId") String resourceTypeId, @Context UriInfo uriInfo) {

        Page<MetricType> ret = inventory.tenants().get(getTenantId()).feeds().get(feedId).resourceTypes()
                .get(resourceTypeId).metricTypes().getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @POST
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Associates a pre-existing metric type with a resource type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, resource type or metric type doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateMetricTypes(@PathParam("feedId") String feedId,
                                         @PathParam("resourceTypeId") String resourceTypeId,
                                         @ApiParam("A list of paths to metric types to be associated with the" +
                                                 " resource type. They can either be canonical or relative to the" +
                                                 " resource type.")
                                         Collection<String> metricTypePaths) {
        String tenantId = getTenantId();

        if (!security.canAssociateFrom(CanonicalPath.of().tenant(tenantId).feed(feedId)
                .resourceType(resourceTypeId).get())) {
            return Response.status(FORBIDDEN).build();
        }

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rt =
                tenant.extend(Feed.SEGMENT_TYPE, feedId).extend(ResourceType.SEGMENT_TYPE, resourceTypeId).get();

        MetricTypes.ReadAssociate metricTypesDao = inventory.tenants().get(tenantId).feeds().get(feedId).resourceTypes()
                .get(resourceTypeId).metricTypes();

        metricTypePaths.stream()
                .map((p) -> org.hawkular.inventory.paths.Path.fromPartiallyUntypedString(p, tenant, rt,
                        MetricType.SEGMENT_TYPE)).forEach(metricTypesDao::associate);

        return Response.noContent().build();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/metricTypes/{metricTypePath:.+}")
    @ApiOperation("Retrieves the given metric type associated with the given resource type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metric types"),
            @ApiResponse(code = 404, message = "Tenant or resource type does not exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public MetricType getAssociatedMetricType(@PathParam("feedId") String feedId,
                                              @PathParam("resourceTypeId") String resourceTypeId,
                                              @Encoded @PathParam("metricTypePath") String metricTypePath,
                                              @QueryParam("canonical") @DefaultValue("false")
                                              @ApiParam("True if metric type path should be considered canonical," +
                                                      " false by default.")
                                              boolean isCanonical) {

        CanonicalPath tenant = CanonicalPath.of().tenant(getTenantId()).get();
        CanonicalPath rt =
                tenant.extend(Feed.SEGMENT_TYPE, feedId).extend(ResourceType.SEGMENT_TYPE, resourceTypeId).get();

        if (isCanonical) {
            metricTypePath = "/" + metricTypePath;
        }

        org.hawkular.inventory.paths.Path mtPath = org.hawkular.inventory.paths.Path
                .fromPartiallyUntypedString(metricTypePath, tenant, rt, MetricType.SEGMENT_TYPE);

        return inventory.inspect(rt, ResourceTypes.Single.class).metricTypes().get(mtPath).entity();
    }

    @GET
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/metricTypes")
    @ApiOperation("Retrieves metric types associated with the given resource type. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metric types"),
            @ApiResponse(code = 404, message = "Tenant or resource type does not exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetricTypes(@PathParam("feedId") String feedId,
                                             @PathParam("resourceTypeId") String resourceTypeId,
                                             @Context UriInfo uriInfo) {
        String tenantId = getTenantId();
        Page<MetricType> mTypes = inventory.tenants().get(tenantId).feeds().get(feedId).resourceTypes()
                .get(resourceTypeId).metricTypes().getAll().entities(extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, mTypes).build();
    }

    @DELETE
    @Path("/feeds/{feedId}/resourceTypes/{resourceTypeId}/metricTypes/{metricTypePath:.+}")
    @ApiOperation("Disassociates the given resource type from the given metric type")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or resource type does not exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetricType(@PathParam("feedId") String feedId,
                                           @PathParam("resourceTypeId") String resourceTypeId,
                                           @Encoded @PathParam("metricTypePath") String metricTypePath,
                                           @QueryParam("canonical") @DefaultValue("false")
                                           @ApiParam("True if metric path should be considered canonical, false by" +
                                                   " default.")
                                           boolean isCanonical) {

        CanonicalPath tenant = CanonicalPath.of().tenant(getTenantId()).get();
        CanonicalPath rt =
                tenant.extend(Feed.SEGMENT_TYPE, feedId).extend(ResourceType.SEGMENT_TYPE, resourceTypeId).get();

        if (!security.canAssociateFrom(rt)) {
            return Response.status(FORBIDDEN).build();
        }

        if (isCanonical) {
            metricTypePath = "/" + metricTypePath;
        }

        org.hawkular.inventory.paths.Path mtPath = org.hawkular.inventory.paths.Path
                .fromPartiallyUntypedString(metricTypePath, tenant, rt, MetricType.SEGMENT_TYPE);

        inventory.inspect(rt, ResourceTypes.Single.class).metricTypes().disassociate(mtPath);

        return Response.noContent().build();
    }
}
