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
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.rest.json.MetricJSON;
import org.hawkular.inventory.rest.json.MetricTypeIdJSON;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import java.util.Set;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
public class RestMetrics {

    @Inject @ForRest
    private Inventory inventory;

    @POST
    @Path("/{tenantId}/{environmentId}/metrics")
    public Response createMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 MetricJSON metric) {
        MetricType mt = inventory.tenants().get(tenantId).metricTypes().get(metric.getMetricTypeId()).entity();

        Metric.Blueprint b = new Metric.Blueprint(mt, metric.getId());

        Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).metrics().create(b).entity();

        return Response.ok(m).build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    public Metric getMetric(@PathParam("tenantId") String tenantId,
                            @PathParam("environmentId") String environmentId,
                            @PathParam("metricId") String metricId) {

        return inventory.tenants().get(tenantId).environments().get(environmentId).metrics().get(metricId).entity();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/metrics")
    public Set<Metric> getMetrics(@PathParam("tenantId") String tenantId,
                            @PathParam("environmentId") String environmentId) {

        return inventory.tenants().get("tenantId").environments().get("environmentId").metrics().getAll().entities();
    }

    @PUT
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    public Response updateMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @PathParam("metricId") String metricId,
                                 MetricTypeIdJSON newDef) {
        MetricType mt = inventory.tenants().get(tenantId).metricTypes().get(newDef.getMetricTypeId()).entity();

        Metric updatedMetric = new Metric(tenantId, environmentId, metricId, mt);
        inventory.tenants().get(tenantId).environments().get(environmentId).metrics().update(updatedMetric);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    public Response deleteMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @PathParam("metricId") String metricId) {

        inventory.tenants().get("tenantId").environments().get("environmentId").metrics().delete(metricId);
        return Response.ok().build();
    }
}
