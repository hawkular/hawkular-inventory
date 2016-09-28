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

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Synced;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.SyncRequest;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.json.ApiError;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
@Path("/sync")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
@Api(value = "/sync", description = "Synchronization of entity trees", tags = "Sync")
public class RestSync extends RestBase {

    public RestSync() {
        super("/sync".length());
    }

    @POST
    @Path("/{path:.+}")
    @ApiOperation("Make the inventory under given path match the provided inventory structure. Note that the " +
            "relationships specified in the provided entities will be ignored and will not be applied.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Synchronization success"),
            @ApiResponse(code = 400, message = "If the entity to be synchronized doesn't support synchronization",
                response = ApiError.class),
            @ApiResponse(code = 404, message = "Authorization problem", response = ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    @SuppressWarnings("unchecked")
    public Response sync(@Encoded @PathParam("path") List<PathSegment> path, SyncRequest<?> req,
                         @Context UriInfo uriInfo) {
        CanonicalPath cp = parsePath(path);

        if (!InventoryStructure.EntityType.supports(cp.getSegment().getElementType())) {
            throw new IllegalArgumentException("Entities of type " + cp.getSegment().getElementType().getSimpleName()
                    + " are not synchronizable.");
        }

        inventory(uriInfo).inspect(cp, Synced.SingleEntity.class).synchronize(req);

        return Response.noContent().build();
    }
}
