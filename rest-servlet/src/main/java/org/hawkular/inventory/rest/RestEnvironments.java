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
import org.hawkular.inventory.api.model.Environment;

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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
public class RestEnvironments {

    @Inject
    private Inventory inventory;

    @GET
    @Path("/{tenantId}/environments")
    public Response getAll(@PathParam("tenantId") String tenantId) {
        try {
            return Response.ok(inventory.tenants().get(tenantId).environments().getAll()).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }

    @POST
    @Path("/{tenantId}/environments")
    public Response update(@PathParam("tenantId") String tenantId, @QueryParam("environmentId") String environmentId) {
        try {
            Environment e = inventory.tenants().get(tenantId).environments().create(environmentId).entity();
            return Response.ok(e).build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }

    @DELETE
    @Path("/{tenantId}/environments/{environmentId}")
    public Response delete(@PathParam("tenantId") String tenantId, @PathParam("environmentId") String environmentId) {
        try {
            inventory.tenants().get(tenantId).environments().delete(environmentId);
            return Response.ok().build();
        } catch (Exception e) {
            RestApiLogger.LOGGER.warn(e);
            return Response.serverError().entity(e).build();
        }
    }
}
