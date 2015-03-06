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
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
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

    @Inject @ForRest
    private Inventory inventory;

    @POST
    @Path("/{tenantId}/{environmentId}/resources")
    public Response addResource(@PathParam("tenantId") String tenantId,
                                @PathParam("environmentId") String environmentId,
                                ResourceJSON resource) {

        Tenants.Single tb = inventory.tenants().get(tenantId);
        ResourceType rt = tb.resourceTypes().get(resource.getType().getId()).entity();

        Resource.Blueprint b = new Resource.Blueprint(resource.getId(), rt);

        Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                .create(b).entity();

        return Response.ok(r).build();
    }


    @GET
    @Path("/{tenantId}/{environmentId}/resources")
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
    @Path("/{tenantId}/{environmentId}/resources/{uid}")
    public Response getResource(@PathParam("tenantId") String tenantId,
                                @PathParam("environmentId") String environmentId, @PathParam("uid") String uid) {
        Resource def = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                .get(uid).entity();

        if (def != null) {
            return Response.ok(def).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }


    @DELETE
    @Path("/{tenantId}/{environmentId}/resources/{uid}")
    public Response deleteResource(@PathParam("tenantId") String tenantId,
                                   @PathParam("environmentId") String environmentId,
                                   @PathParam("uid") String uid) {
        inventory.tenants().get(tenantId).environments().get(environmentId).resources().delete(uid);
        return Response.ok().build();
    }


    @POST
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics/")
    public Response addMetricToResource(@PathParam("tenantId") String tenantId,
                                        @PathParam("environmentId") String environmentId,
                                        @PathParam("resourceId") String resourceId,
                                        Collection<String> metricIds) {
        Metrics.ReadRelate metricDao = inventory.tenants().get(tenantId).environments().get(environmentId)
                .resources().get(resourceId).metrics();

        metricIds.forEach(metricDao::add);

        return Response.ok().build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics")
    public Response listMetricsOfResource(@PathParam("tenantId") String tenantId,
                                          @PathParam("environmentId") String environmentID,
                                          @PathParam("resourceId") String resourceId) {
        Set<Metric> ms = inventory.tenants().get(tenantId).environments().get(environmentID)
                .resources().get(resourceId).metrics().getAll().entities();

        return Response.ok(ms).build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/resources/{resourceId}/metrics/{metricId}")
    public Response getMetricOfResource(@PathParam("tenantId") String tenantId,
                                        @PathParam("environmentId") String environmentId,
                                        @PathParam("resourceId") String resourceId,
                                        @PathParam("metricId") String metricId) {
        Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).resources().get(resourceId)
                .metrics().get(metricId).entity();
        return Response.ok(m).build();
    }
}
