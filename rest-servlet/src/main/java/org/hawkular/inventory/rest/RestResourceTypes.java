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
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Version;
import org.hawkular.inventory.rest.json.IdJSON;
import org.hawkular.inventory.rest.json.ResourceTypeJSON;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class RestResourceTypes {

    @Inject @ForRest
    private Inventory inventory;

    @GET
    @Path("/{tenantId}/resourceTypes")
    public Response getAll(@PathParam("tenantId") String tenantId) {
        return Response.ok(inventory.tenants().get(tenantId).resourceTypes().getAll().entities()).build();
    }

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}")
    public ResourceType get(@PathParam("tenantId") String tenantId,
                            @PathParam("resourceTypeId") String resourceTypeId) {
        return inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).entity();
    }

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/metricTypes")
    public Set<MetricType> getMetricTypes(@PathParam("tenantId") String tenantId,
                                          @PathParam("resourceTypeId") String resourceTypeId) {
        return inventory.tenants().get("tenantId").resourceTypes().get(resourceTypeId).metricTypes().getAll()
                .entities();
    }

    @GET
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/resources")
    public Set<Resource> getResources(@PathParam("tenantId") String tenantId,
                                      @PathParam("resourceTypeId") String resourceTypeId) {

        return inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                .resources().getAll().entities();
    }

    @POST
    @Path("/{tenantId}/resourceTypes")
    public Response create(@PathParam("tenantId") String tenantId, ResourceTypeJSON resourceType) {
        ResourceType.Blueprint b = new ResourceType.Blueprint(resourceType.getId(),
                new Version(resourceType.getVersion()));

        return Response.ok(inventory.tenants().get(tenantId).resourceTypes().create(b).entity()).build();
    }

    @DELETE
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}")
    public Response delete(@PathParam("tenantId") String tenantId,
                           @PathParam("resourceTypeId") String resourceTypeId) {
        inventory.tenants().get(tenantId).resourceTypes().delete(resourceTypeId);
        return Response.ok().build();
    }

    @POST
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/metricTypes")
    public Response addMetricType(@PathParam("tenantId") String tenantId,
                                  @PathParam("resourceTypeId") String resourceTypeId,
                                  IdJSON metricTypeId) {
        inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).metricTypes()
                .add(metricTypeId.getId());
        return Response.ok().build();
    }

    @DELETE
    @Path("/{tenantId}/resourceTypes/{resourceTypeId}/metricTypes/{metricTypeId}")
    public Response removeMetricType(@PathParam("tenantId") String tenantId,
                                     @PathParam("resourceTypeId") String resourceTypeId,
                                     @PathParam("metricTypeId") String metricTypeId) {
        inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId).metricTypes().remove(metricTypeId);
        return Response.ok().build();
    }
}
