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

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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

import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.rest.json.ApiError;
import org.hawkular.inventory.rest.security.EntityIdUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;


/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
@javax.ws.rs.Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/", description = "Resource Metrics CRUD", tags = "Resources Metrics")
public class RestResourcesMetrics extends RestResources {

    @POST
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/metrics")
    @ApiOperation("Either creates a new metric owned by the resource or associates a pre-existing metric with the " +
            "resource. This depends on what you pass as the the body of the request. A JSON array of strings is " +
            "understood as a list of pre-existing metric paths that are associated with the resource. If the body is" +
            " a JSON object or an array of JSON objects, the new metric or metrics are created \"underneath\" the " +
            "resource.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "New metric created under the resource"),
            @ApiResponse(code = 204, message = "Existing metric successfully associated"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource doesn't exist. Also when an array of " +
                    "strings is supplied and one of the metrics in that array doesn't exist.",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateOrCreateMetrics(@PathParam("environmentId") String environmentId,
                                             @Encoded @PathParam("resourcePath") String resourcePath,
                                             @ApiParam("This is either a metric blueprint or a list of paths to " +
                                                     "metrics to be associated with the resource. They can either be " +
                                                     "canonical or relative to the resource.")
                                                 Object metricPathsOrBlueprint,
                                             @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath resource = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        return createOrAssociateMetric(tenant, resource, metricPathsOrBlueprint, uriInfo);
    }

    @POST
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/metrics")
    @ApiOperation("Either creates a new metric owned by the resource or associates a pre-existing metric with the " +
            "resource. This depends on what you pass as the the body of the request. A JSON array of strings is " +
            "understood as a list of pre-existing metric paths that are associated with the resource. If the body is" +
            " a JSON object or an array of JSON objects, the new metric or metrics are created \"underneath\" the " +
            "resource.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "New metric created under the resource"),
            @ApiResponse(code = 204, message = "Existing metric successfully associated"),
            @ApiResponse(code = 404, message = "Tenant, environment, resource doesn't exist. Also when an array of " +
                    "strings is supplied and one of the metrics in that array doesn't exist.",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response associateOrCreateMetricsUnderFeed(@PathParam("feedId") String feedId,
                                      @Encoded @PathParam("resourcePath") String resourcePath,
                                      @ApiParam("This is either a metric blueprint or a list of paths to metrics to " +
                                              "be associated with the resource. They can either be canonical or " +
                                              "relative to the resource.")
                                          Object metricPathsOrBlueprint, @Context UriInfo uriInfo) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();

        CanonicalPath resource = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        return createOrAssociateMetric(tenant, resource, metricPathsOrBlueprint, uriInfo);
    }

    private Response createOrAssociateMetric(CanonicalPath tenant, CanonicalPath resource,
                                             Object metricPathsOrBlueprint, UriInfo uriInfo) {
        BiFunction<Map<?, ?>, Metrics.ReadWrite, Metric> createMetric = (map, access) -> {
            Metric.Blueprint blueprint = mapper.convertValue(map, Metric.Blueprint.class);
            return access.create(blueprint).entity();
        };

        if (metricPathsOrBlueprint instanceof List) {
            List<?> list = (List<?>) metricPathsOrBlueprint;
            if (list.isEmpty()) {
                return Response.noContent().build();
            }

            if (list.get(0) instanceof Map) {
                if (!security.canCreate(Metric.class).under(resource)) {
                    return Response.status(FORBIDDEN).build();
                }

                Metrics.ReadWrite metricDao = inventory.inspect(resource, Resources.Single.class).metrics();

                List<Metric> createdMetrics = list.stream().map(m -> createMetric.apply((Map<?, ?>) m, metricDao))
                        .collect(Collectors.toList());

                if (createdMetrics.size() == 1) {
                    Metric.Blueprint blueprint = mapper.convertValue(list.get(0), Metric.Blueprint.class);
                    return ResponseUtil.created(createdMetrics.get(0), uriInfo, blueprint.getId()).build();
                } else {
                    return ResponseUtil.created(uriInfo, createdMetrics.stream().map(Metric::getId).spliterator())
                            .build();
                }
            } else {
                if (!security.canAssociateFrom(resource)) {
                    return Response.status(FORBIDDEN).build();
                }

                Metrics.ReadAssociate metricDao = inventory.inspect(resource, Resources.Single.class).allMetrics();
                list.stream()
                        .map((p) -> Path.fromPartiallyUntypedString((String) p, tenant, resource, Metric.SEGMENT_TYPE))
                        .forEach(metricDao::associate);

                return Response.noContent().build();
            }
        } else if (metricPathsOrBlueprint instanceof Map) {
            if (!security.canCreate(Metric.class).under(resource)) {
                return Response.status(FORBIDDEN).build();
            }

            Metrics.ReadWrite metricDao = inventory.inspect(resource, Resources.Single.class).metrics();
            Metric entity = createMetric.apply((Map<?, ?>) metricPathsOrBlueprint, metricDao);

            Metric.Blueprint blueprint = mapper.convertValue(metricPathsOrBlueprint, Metric.Blueprint.class);
            return ResponseUtil.created(entity, uriInfo, blueprint.getId()).build();
        } else {
            throw new IllegalArgumentException("Unhandled type of input");
        }
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
            @Encoded @PathParam("resourcePath") String resourcePath, @Context UriInfo uriInfo) {
        CanonicalPath resource = composeCanonicalPath(getTenantId(), environmentID, null, resourcePath);
        Page<Metric> ms = inventory.inspect(resource, Resources.Single.class).allMetrics().getAll().entities(
                extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, ms).build();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/metrics")
    @ApiOperation("Retrieves all metrics associated with a resource. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of metrics"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or resource doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetricsF(@PathParam("feedId") String feedId,
                                          @Encoded @PathParam("resourcePath") String resourcePath,
                                          @Context UriInfo uriInfo) {
        CanonicalPath resource = composeCanonicalPath(getTenantId(), null, feedId, resourcePath);
        Page<Metric> ms = inventory.inspect(resource, Resources.Single.class).allMetrics().getAll().entities(
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
            @Encoded @PathParam("resourcePath") String resourcePath,
            @Encoded @PathParam("metricPath") String metricPath, @QueryParam("canonical") @DefaultValue("false")
    @ApiParam("True if metric path should be considered canonical, false by default.") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        if (isCanonical) {
            metricPath = "/" + metricPath;
        }

        Path mp = Path.fromPartiallyUntypedString(metricPath, tenant, rp, Metric.SEGMENT_TYPE);

        if (EntityIdUtils.isTenantEscapeAttempt(rp, mp)) {
            Response.status(FORBIDDEN).build();
        }

        Metric m = inventory.inspect(rp, Resources.Single.class).allMetrics().get(mp).entity();

        return Response.ok(m).build();
    }

    @GET
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Retrieves a single resource")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The resource"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, feed, resource or metric doesn't exist or if the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAssociatedMetricF(
            @PathParam("feedId") String feedId, @Encoded @PathParam("resourcePath") String resourcePath,
            @Encoded @PathParam("metricPath") String metricPath, @QueryParam("canonical") @DefaultValue("false")
    @ApiParam("True if metric path should be considered canonical, false by default.") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        if (isCanonical) {
            metricPath = "/" + metricPath;
        }

        Path mp = Path.fromPartiallyUntypedString(metricPath, tenant, rp, Metric.SEGMENT_TYPE);

        if (EntityIdUtils.isTenantEscapeAttempt(rp, mp)) {
            Response.status(FORBIDDEN).build();
        }

        Metric m = inventory.inspect(rp, Resources.Single.class).allMetrics().get(mp).entity();
        return Response.ok(m).build();
    }

    @DELETE
    @javax.ws.rs.Path("/{environmentId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Disassociates the given resource from the given metric. If the metric is contained within the " +
            "resource, it is also deleted.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetric(@PathParam("environmentId") String environmentId,
            @Encoded @PathParam("resourcePath") String resourcePath,
            @Encoded @PathParam("metricPath") String metricPath, @QueryParam("canonical") @DefaultValue("false")
            @ApiParam("True if metric path should be considered canonical, false by default.") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, environmentId, null, resourcePath);

        return disassociateOrDelete(tenant, rp, metricPath, isCanonical);
    }

    @DELETE
    @javax.ws.rs.Path("/feeds/{feedId}/resources/{resourcePath:.+}/metrics/{metricPath:.+}")
    @ApiOperation("Disassociates the given resource from the given metric. If the metric is contained within the " +
            "resource, it is also deleted.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404,
                    message = "Tenant, environment, feed, resource or metric does not exist or the metric is not " +
                            "associated with the resource", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response disassociateMetricF(
            @PathParam("feedId") String feedId, @Encoded @PathParam("resourcePath") String resourcePath,
            @Encoded @PathParam("metricPath") String metricPath, @QueryParam("canonical") @DefaultValue("false")
    @ApiParam("True if metric path should be considered canonical, false by default.") boolean isCanonical) {

        String tenantId = getTenantId();

        CanonicalPath tenant = CanonicalPath.of().tenant(tenantId).get();
        CanonicalPath rp = composeCanonicalPath(tenantId, null, feedId, resourcePath);

        return disassociateOrDelete(tenant, rp, metricPath, isCanonical);
    }

    private Response disassociateOrDelete(CanonicalPath tenant, CanonicalPath resource, String metricPath, boolean
            isCanonical) {

        if (!security.canAssociateFrom(resource)) {
            return Response.status(FORBIDDEN).build();
        }

        if (isCanonical) {
            metricPath = "/" + metricPath;
        }

        Path mp = Path.fromPartiallyUntypedString(metricPath, tenant, resource, Metric.SEGMENT_TYPE);

        if (EntityIdUtils.isTenantEscapeAttempt(resource, mp)) {
            Response.status(FORBIDDEN).build();
        }

        if (mp.isRelative()) {
            mp = mp.toRelativePath().applyTo(resource);
        }

        if (mp.toCanonicalPath().up().equals(resource)) {
            inventory.inspect(resource, Resources.Single.class).metrics().delete(mp.getSegment().getElementId());
        } else {
            inventory.inspect(resource, Resources.Single.class).allMetrics().disassociate(mp);
        }

        return Response.noContent().build();
    }
}
