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

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
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
public class RestResources {

    @Inject
    private Inventory inventory;

    @POST
    @Path("/{tenantId}/{environmentId}/resources")
    public Response addResource(@PathParam("tenantId") String tenantId,
                                @PathParam("environmentId") String environmentId,
                                @QueryParam("id") String resourceId,
                                @QueryParam("resourceType") String resourceTypeId) {

        try {
            Tenants.Single tb = inventory.tenants().get(tenantId);
            ResourceType rt = tb.types().get(resourceTypeId).entity();

            Resource.Blueprint b = new Resource.Blueprint(resourceId, rt);

            Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                    .create(b).entity();

            return Response.ok(r).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }


    @GET
    @Path("/{tenantId}/{environmentId}/resources")
    public Response getResourcesByType(@PathParam("tenantId") String tenantId,
                                       @PathParam("environmentId") String environmentId,
                                       @QueryParam("type") String typeId,
                                       @QueryParam("typeVersion") String typeVersion) {
        try {
            Resources.ReadWrite rr = inventory.tenants().get(tenantId).environments().get(environmentId).resources();

            Set<Resource> rs;
            if (typeId != null && typeVersion != null) {
                ResourceType rt = new ResourceType(tenantId, typeId, typeVersion);
                rs = rr.getAll(Related.definedBy(rt)).entities();
            } else {
                rs = rr.getAll().entities();
            }
            return Response.ok(rs).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{uid}")
    public Response getResource(@PathParam("tenantId") String tenantId,
                                @PathParam("environmentId") String environmentId, @PathParam("uid") String uid) {

        try {
            Resource def = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                    .get(uid).entity();

            if (def != null) {
                return Response.ok(def).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().build();
        }
    }


    @DELETE
    @Path("/{tenantId}/{environmentId}/resources/{uid}")
    public Response deleteResource(@PathParam("tenantId") String tenantId,
                                   @PathParam("environmentId") String environmentId,
                                   @PathParam("uid") String uid) {

        try {
            inventory.tenants().get(tenantId).environments().get(environmentId).resources().delete(uid);
            return Response.ok().build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().build();
        }
    }


    @POST
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics/")
    public Response addMetricToResource(@PathParam("tenantId") String tenantId,
                                        @PathParam("environmentId") String environmentId,
                                        @PathParam("resourceId") String resourceId,
                                        Collection<String> metricIds) {


        try {
            Metrics.ReadRelate metricDao = inventory.tenants().get(tenantId).environments().get(environmentId)
                    .resources().get(resourceId).metrics();

            metricIds.forEach(metricDao::add);

            return Response.ok().build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics")
    public Response listMetricsOfResource(@PathParam("tenantId") String tenantId,
                                          @PathParam("environmentId") String environmentID,
                                          @PathParam("resourceId") String resourceId) {


        try {
            Set<Metric> ms = inventory.tenants().get(tenantId).environments().get(environmentID)
                    .resources().get(resourceId).metrics().getAll().entities();

            return Response.ok(ms).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }

    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics/{metricId}")
    public Response getMetricOfResource(@PathParam("tenantId") String tenantId,
                                        @PathParam("environmentId") String environmentId,
                                        @PathParam("resourceId") String resourceId,
                                        @PathParam("metricId") String metricId
    ) {


        try {
            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).resources().get(resourceId)
                    .metrics().get(metricId).entity();
            return Response.ok(m).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }
}
