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
import org.hawkular.inventory.api.Resource;
import org.hawkular.inventory.api.ResourceType;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * The Rest api for Hawkular Inventory
 * @author Heiko Rupp
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
public class RestApi {


    @Inject
    Inventory inventory;

    /*
    [14:23:18]  <theute>   POST /hawkular/inventory/resources { type:url; parameters { url="http://www.google.com" }
        and return an id would do...
    [14:23:47]  <theute>    GET /hawkular/inventory/resources?type=url to return all URLs typed resources
     */


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
    public Response getResources(@PathParam("tenantId") String tenantId,
                             @PathParam("type") ResourceType type) {

        try {
            List<Resource> bla = inventory.getResourcesForType(tenantId, type);
            return Response.ok(bla).build();
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


}
