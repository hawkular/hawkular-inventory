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
package org.hawkular.inventory.rest.deprecated;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.RequestUtil;
import org.hawkular.inventory.rest.ResponseUtil;
import org.hawkular.inventory.rest.RestBase;
import org.hawkular.inventory.rest.json.ApiError;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 0.8.0
 */
@Path("/deprecated/metadatapacks")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/deprecated/metadatapacks", description = "CRUD for the metadata packs.",
        tags = {"Deprecated"})
public class RestMetadataPacks extends RestBase {

    @GET
    @Path("/")
    @ApiOperation("Retrieves all metadata packs.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = Feed.class),
            @ApiResponse(code = 401, message = "Unauthorized access"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@Context UriInfo uriInfo) {
        String tenantId = getTenantId();

        return pagedResponse(Response.ok(), uriInfo, inventory.tenants().get(tenantId).metadataPacks().getAll()
                .entities(RequestUtil.extractPaging(uriInfo))).build();
    }

    @POST
    @Path("/")
    @ApiOperation("Create a metadata pack")
    @ApiResponses({})
    public Response create(@ApiParam(required = true) MetadataPack.Blueprint blueprint, @Context UriInfo ui) {
        String tenantId = getTenantId();

        if (!security.canCreate(MetadataPack.class).under(CanonicalPath.of().tenant(tenantId).get())) {
            return Response.status(FORBIDDEN).build();
        }
        MetadataPack ret = inventory.tenants().get(tenantId).metadataPacks().create(blueprint).entity();

        return ResponseUtil.created(ret, ui, ret.getId()).entity(ret).build();
    }

    @PUT
    @Path("/{id}")
    @ApiOperation("Update a metadata pack.")
    @ApiResponses({})
    public Response update(@PathParam("id") String id, @ApiParam(required = true) MetadataPack.Update update) {
        String tenantId = getTenantId();

        if (!security.canUpdate(CanonicalPath.of().tenant(tenantId).metadataPack(id).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).metadataPacks().update(id, update);

        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @ApiOperation("Deletes a metadata pack.")
    @ApiResponses({})
    public Response delete(@PathParam("id") String id) {
        String tenantId = getTenantId();

        if (!security.canDelete(CanonicalPath.of().tenant(tenantId).metadataPack(id).get())) {
            return Response.status(FORBIDDEN).build();
        }

        inventory.tenants().get(tenantId).metadataPacks().delete(id);

        return Response.noContent().build();
    }

    @GET
    @Path("/{id}")
    @ApiOperation("Get a single metadata pack by id.")
    @ApiResponses({})
    public MetadataPack get(@PathParam("id") String id) {
        String tenantId = getTenantId();
        return inventory.tenants().get(tenantId).metadataPacks().get(id).entity();
    }

    @GET
    @Path("/{id}/resourceTypes")
    @ApiOperation("Retrieve all the resource types of the metadata pack.")
    @ApiResponses({})
    public Response getResourceTypes(@PathParam("id") String id, @Context UriInfo ui) {
        String tenantId = getTenantId();

        return pagedResponse(Response.ok(), ui, inventory.tenants().get(tenantId).metadataPacks().get(id)
                .resourceTypes().getAll().entities(RequestUtil.extractPaging(ui))).build();
    }

    @GET
    @Path("/{id}/metricTypes")
    @ApiOperation("Retrieve all the metric types of the metadata pack.")
    @ApiResponses({})
    public Response getMetricTypes(@PathParam("id") String id, @Context UriInfo ui) {
        String tenantId = getTenantId();

        return pagedResponse(Response.ok(), ui, inventory.tenants().get(tenantId).metadataPacks().get(id)
                .metricTypes().getAll().entities(RequestUtil.extractPaging(ui))).build();
    }
}
