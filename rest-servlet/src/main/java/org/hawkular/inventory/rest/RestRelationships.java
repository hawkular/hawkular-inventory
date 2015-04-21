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

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.rest.json.ApiError;
import org.hawkular.inventory.rest.json.RelationshipDeserializer;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author jkremser
 * @since 0.0.2
 */
@Path("/relationships")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/relationships", description = "REST service for relationships")
public class RestRelationships {

    @Inject @ForRest
    private Inventory inventory;

    @GET
    @Path("/")
    @ApiOperation("Lists all relationships")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of tenants"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getAll(@DefaultValue("both") @QueryParam("direction") String direction,
                           @DefaultValue("") @QueryParam("property") String propertyName,
                           @DefaultValue("") @QueryParam("propertyValue") String propertyValue,
                           @DefaultValue("") @QueryParam("named") String named,
                           @DefaultValue("") @QueryParam("sourceType") String sourceType,
                           @DefaultValue("") @QueryParam("targetType") String targetType,
                           @Context UriInfo info) {

        RelationFilter[] filters = RestRelationships.extractFilters(propertyName, propertyValue, named, sourceType,
                targetType, info);

        // this will throw IllegalArgumentException on undefined values
        Relationships.Direction directed = Relationships.Direction.valueOf(direction);

        return Response.ok(inventory.tenants().getAll().relationships(directed).getAll(filters).entities()).build();
    }

    @GET
    @Path("/{relationshipId}")
    @ApiOperation("Lists all relationships")
    @ApiResponses({
            @ApiResponse(code = 200, message = "The list of tenants"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Relationship getRelationship(@PathParam("relationshipId") String relationshipId) {
        // this method assumes all entities to be connected with at least 1 tenant
        return inventory.tenants().getAll().relationships().get(relationshipId).entity();
    }

    public static String getUrl(String id) {
        return String.format("/relationships/%s", id);
    }

    public static RelationFilter[] extractFilters(String propertyName,
                                                  String propertyValue,
                                                  String named,
                                                  String sourceType,
                                                  String targetType,
                                                  UriInfo info) {
        List<RelationFilter> filters = new ArrayList<>();
        if (!propertyName.isEmpty() && !propertyValue.isEmpty()) {
            filters.add(RelationWith.property(propertyName, propertyValue));
        }
        if (!named.isEmpty()) {
            List<String> namedParam = info.getQueryParameters().get("named");
            if (namedParam == null || namedParam.isEmpty()) {
                throw new IllegalArgumentException("Malformed URL param, the right format is: " +
                        "named=label1&named=label2&named=labelN");
            }
            filters.add(RelationWith.names(namedParam.toArray(new String[namedParam.size()])));
        }
        if (!sourceType.isEmpty()) {
            List<String> sourceParam = info.getQueryParameters().get("sourceType");
            if (sourceParam == null || sourceParam.isEmpty()) {
                throw new IllegalArgumentException("Malformed URL param, the right format is: " +
                        "sourceType=type1&sourceType=type2&sourceType=typeN");
            }
            Class<? extends Entity>[] types = (Class<? extends Entity>[]) sourceParam.stream()
                    .map(typeString -> RelationshipDeserializer.entityMap.get(typeString))
                    .toArray();
            if (!sourceParam.isEmpty()) {
                filters.add(RelationWith.sourcesOfTypes(types));
            }
        }
        if (!targetType.isEmpty()) {
            List<String> targetParam = info.getQueryParameters().get("targetType");
            if (targetParam == null || targetParam.isEmpty()) {
                throw new IllegalArgumentException("Malformed URL param, the right format is: " +
                        "targetType=type1&targetType=type2&targetType=typeN");
            }
            Class<? extends Entity>[] types = (Class<? extends Entity>[]) targetParam.stream()
                    .map(typeString -> RelationshipDeserializer.entityMap.get(typeString))
                    .toArray();
            if (!targetParam.isEmpty()) {
                filters.add(RelationWith.targetsOfTypes(types));
            }
        }
        return filters.isEmpty() ? RelationFilter.all() : filters.toArray(new RelationFilter[filters.size()]);
    }
}
