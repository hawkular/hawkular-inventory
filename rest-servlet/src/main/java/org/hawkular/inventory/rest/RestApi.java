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
import org.hawkular.inventory.api.MetricDefinition;
import org.hawkular.inventory.api.Resource;
import org.hawkular.inventory.api.ResourceType;

import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.Collection;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * The Rest api for Hawkular Inventory
 * @author Heiko Rupp
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
public class RestApi {


    @EJB
    Inventory inventory;


    @GET
    @Path("/")
    public StringWrapper ping() {
        return new StringWrapper("Hello World");
    }

    @POST
    @Path("/{tenantId}/resources")
    public Response addResource(@PathParam("tenantId") String tenantId,
                            Resource definition) {

        try {
            String id = inventory.addResource(tenantId, definition);

            return Response.ok(new IdWrapper(id)).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }


    @GET
    @Path("/{tenantId}/resources")
    public Response getResourcesByType(@PathParam("tenantId") String tenantId,
                                       @QueryParam("type") String type) {

        ResourceType rtype = ResourceType.valueOf(type.toUpperCase());

        try {
            Collection<Resource> resources = inventory.getResourcesForType(tenantId, rtype);
            return Response.ok(resources).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }

    @GET
    @Path("/{tenantId}/resource/{uid}")
    public Response getResource(@PathParam("tenantId") String tenantId, @PathParam
            ("uid") String uid) {

        try {
            Resource def = inventory.getResource(tenantId, uid);

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
    @Path("/{tenantId}/resource/{uid}")
    public Response deleteResource(@PathParam("tenantId") String tenantId, @PathParam
            ("uid") String uid) {

        try {
            boolean def = inventory.deleteResource(tenantId, uid);

            if (def) {
                return Response.ok(def).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().build();
        }
    }


    @PUT
    @Path("/{tenantId}/resource/{resourceId}/metrics/")
    public Response addMetricToResource(@PathParam("tenantId") String tenantId,
                                        @PathParam("resourceId") String resourceId,
                                        Collection<MetricDefinition> payload) {


        try {

            if (inventory.getResource(tenantId, resourceId)==null) {
                return Response.status(404).entity("Resource with ID " + resourceId + " not found for tenant").build();
            }

            if (payload.size() == 0) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            boolean def = inventory.addMetricsToResource(tenantId, resourceId, payload);

            if (def) {
                return Response.ok(def).build();
            } else {
                return Response.status(Response.Status.NOT_MODIFIED).build();
            }

        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{tenantId}/resource/{resourceId}/metrics")
    public Response listMetricsOfResource(@PathParam("tenantId") String tenantId,
                                            @PathParam("resourceId") String resourceId) {


        try {

            if (inventory.getResource(tenantId, resourceId)==null) {
                return Response.status(404).entity("Resource with ID " + resourceId + " not found for tenant").build();
            }

            Collection<MetricDefinition> bla = inventory.listMetricsForResource(tenantId, resourceId);
            return Response.ok(bla).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }

    }

    @GET
    @Path("/{tenantId}/resource/{resourceId}/metric/{metricId}")
    public Response getMetricOfResource(@PathParam("tenantId") String tenantId,
                                            @PathParam("resourceId") String resourceId,
                                            @PathParam("metricId") String metricId
    ) {


        try {

            if (inventory.getResource(tenantId, resourceId)==null) {
                return Response.status(404).entity("Resource with ID " + resourceId + " not found for tenant").build();
            }

            MetricDefinition bla = inventory.getMetric(tenantId, resourceId, metricId);
            if (bla==null) {
                return Response.status(404).entity("Metric {" + metricId + "} for " +
                        "Resource with ID " + resourceId + " not found for tenant")
                        .build();
            }
            return Response.ok(bla).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }

    @PUT
    @Path("/{tenantId}/resource/{resourceId}/metric/{metricId}")
    public Response getMetricOfResource(@PathParam("tenantId") String tenantId,
                                            @PathParam("resourceId") String resourceId,
                                            MetricDefinition payload) {

        try {
            if (inventory.getResource(tenantId, resourceId)==null) {
                return Response.status(404).entity("Resource with ID " + resourceId + " not found for tenant").build();
            }

            boolean updated = inventory.updateMetric(tenantId,resourceId,payload);

            if (updated) {
                return Response.ok().build();
            } else {
                return Response.notModified().build();
            }
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }

}
