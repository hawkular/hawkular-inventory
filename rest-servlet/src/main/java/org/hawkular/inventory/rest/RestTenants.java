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
import org.hawkular.inventory.rest.json.IdJSON;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/tenants")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class RestTenants {

    @Inject @ForRest
    private Inventory inventory;

    @GET
    @Path("/")
    public Response getAll() {
        return Response.ok(inventory.tenants().getAll().entities()).build();
    }

    @POST
    @Path("/")
    public Response create(IdJSON tenantId) {
        return Response.ok(inventory.tenants().create(tenantId.getId()).entity()).build();
    }

    @DELETE
    @Path("/{tenantId}")
    public Response delete(@PathParam("tenantId") String tenantId) {
        inventory.tenants().delete(tenantId);
        return Response.ok().build();
    }
}
