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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import static org.hawkular.inventory.rest.RequestUtil.extractPaging;
import static org.hawkular.inventory.rest.ResponseUtil.pagedResponse;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToSingleWithRelationships;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.CanonicalPath;

/**
 * @author Jiri Kremser
 * @since 0.1.0
 */
@Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/.*/relationships", description = "Work with the relationships.")
public class RestRelationships extends RestBase {

    public static Map<String, Class<? extends Entity>> entityMap;

    static {
        try {
            entityMap = new HashMap<>(6);
            entityMap.put(Tenant.class.getSimpleName(), Tenant.class);
            entityMap.put(Environment.class.getSimpleName(), Environment.class);
            entityMap.put(ResourceType.class.getSimpleName(), ResourceType.class);
            entityMap.put(MetricType.class.getSimpleName(), MetricType.class);
            entityMap.put(Resource.class.getSimpleName(), Resource.class);
            entityMap.put(Metric.class.getSimpleName(), Metric.class);
            entityMap.put(Feed.class.getSimpleName(), Feed.class);
        } catch (Exception e) {
            // just to make sure class loading can't fail
        }
    }

    @GET
    @Path("{path:.*}/relationships")
    @ApiOperation("Retrieves relationships")
    public Response get(@PathParam("path") String path,
                        @DefaultValue("both") @QueryParam("direction") String direction,
                        @DefaultValue("") @QueryParam("property") String propertyName,
                        @DefaultValue("") @QueryParam("propertyValue") String propertyValue,
                        @DefaultValue("") @QueryParam("named") String named,
                        @DefaultValue("") @QueryParam("sourceType") String sourceType,
                        @DefaultValue("") @QueryParam("targetType") String targetType,
                        @Context UriInfo uriInfo) {
        String securityId = toSecurityId(path);
        if (!Security.isValidId(securityId)) {
            return Response.status(NOT_FOUND).build();
        }
        CanonicalPath cPath = Security.getCanonicalPath(securityId);
        ResolvableToSingleWithRelationships<Relationship> resolvable = getResolvableFromCanonicalPath(cPath);
        Pager pager = extractPaging(uriInfo);
        RelationFilter[] filters = extractFilters(propertyName, propertyValue, named, sourceType,
                                                  targetType, uriInfo);
        Relationships.Direction directed = Relationships.Direction.valueOf(direction);
        Page<Relationship> relations = resolvable.relationships(directed).getAll(filters).entities(pager);

        return pagedResponse(Response.ok(), uriInfo, relations).build();
    }

    @DELETE
    @Path("{path:.*}/relationships")
    @ApiOperation("Deletes a relationship")
    public Response delete(@PathParam("path") String path,
                           @ApiParam(required = true) Relationship relation,
                           @Context UriInfo uriInfo) {
        String securityId = toSecurityId(path);
        if (!Security.isValidId(securityId)) {
            return Response.status(NOT_FOUND).build();
        }
        if (Arrays.asList(Relationships.WellKnown.values()).contains(relation.getName())) {
            throw new IllegalArgumentException("Unable to delete a relationship with well defined name. Restricted " +
                                                       "names: " + Arrays.asList(Relationships.WellKnown.values()));
        }
        CanonicalPath cPath = Security.getCanonicalPath(securityId);
        ResolvableToSingleWithRelationships<Relationship> resolvable = getResolvableFromCanonicalPath(cPath);

        // delete the relationship
        resolvable.relationships(Relationships.Direction.both).delete(relation.getId());
        if (RestApiLogger.LOGGER.isDebugEnabled()) {
            RestApiLogger.LOGGER.debug("deleting relationship with id: " + relation.getId() + " and name: " +
                                               relation.getName());
        }

        return Response.noContent().build();
    }

    @POST
    @Path("{path:.*}/relationships")
    @ApiOperation("Creates a relationship")
    public Response create(@PathParam("path") String path,
                           @ApiParam(required = true) Relationship relation,
                           @Context UriInfo uriInfo) {
        String securityId = toSecurityId(path);
        if (!Security.isValidId(securityId)) {
            return Response.status(NOT_FOUND).build();
        }
        if (Arrays.asList(Relationships.WellKnown.values()).contains(relation.getName())) {
            throw new IllegalArgumentException("Unable to create a relationship with well defined name. Restricted " +
                                                       "names: " + Arrays.asList(Relationships.WellKnown.values()));
        }
        CanonicalPath cPath = Security.getCanonicalPath(securityId);
        ResolvableToSingleWithRelationships<Relationship> resolvable = getResolvableFromCanonicalPath(cPath);

        Relationships.Direction directed;
        Entity theOtherSide;
        String[] chunks = path.split("/");
        String currentEntityId = chunks[chunks.length - 1];
        if (relation.getSource().getId() == currentEntityId) {
            directed = Relationships.Direction.outgoing;
            theOtherSide = relation.getTarget();
        } else if (relation.getTarget().getId() == currentEntityId) {
            directed = Relationships.Direction.incoming;
            theOtherSide = relation.getSource();
        } else {
            throw new IllegalArgumentException("Either source or target of the relationship must correspond with the " +
                                                       "resource being modified.");
        }

        // link the current entity with the target or the source of the relationship
        resolvable.relationships(directed).linkWith(relation.getName(), theOtherSide, relation.getProperties());
        if (RestApiLogger.LOGGER.isDebugEnabled()) {
            RestApiLogger.LOGGER.debug("creating relationship with id: " + relation.getId() + " and name: " +
                                               relation.getName());
        }

        return ResponseUtil.created(uriInfo, relation.getId()).build();
    }

    private String toSecurityId(String urlPath) {
        if (urlPath == null) return null;
        return urlPath.startsWith("tenants") ? urlPath : getTenantId() + "/" + urlPath;
    }

    private ResolvableToSingleWithRelationships getResolvableFromCanonicalPath(CanonicalPath cPath) {
        Tenants.Single tenant = inventory.tenants().get(cPath.getTenantId());
        ResolvableToSingleWithRelationships resolvable = tenant;
        if (cPath.getEnvironmentId() != null) {
            Environments.Single env = tenant.environments().get(cPath.getEnvironmentId());
            if (cPath.getFeedId() != null) {
                if (cPath.getResourceId() != null) {
                    resolvable = env.feeds().get(cPath.getFeedId()).resources().get(cPath.getResourceId());
                } else if (cPath.getMetricId() != null) {
                    resolvable = env.feeds().get(cPath.getFeedId()).metrics().get(cPath.getMetricId());
                } else {
                    resolvable = env.feeds().get(cPath.getFeedId());
                }
            } else if (cPath.getResourceId() != null) {
                resolvable = env.feedlessResources().get(cPath.getResourceId());
            } else if (cPath.getMetricId() != null) {
                resolvable = env.feedlessMetrics().get(cPath.getMetricId());
            }
        } else if (cPath.getResourceTypeId() != null) {
            resolvable = tenant.resourceTypes().get(cPath.getResourceTypeId());
        } else if (cPath.getMetricTypeId() != null) {
            resolvable = tenant.resourceTypes().get(cPath.getMetricTypeId());
        }
        return resolvable;
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
            Class<? extends Entity<?, ?>>[] types = sourceParam.stream()
                    .map(typeString -> entityMap.get(typeString))
                    .toArray(size -> new Class[size]);
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
            Class<? extends Entity<?, ?>>[] types = (Class<? extends Entity<?, ?>>[]) targetParam.stream()
                    .map(typeString -> entityMap.get(typeString))
                    .toArray();
            if (!targetParam.isEmpty()) {
                filters.add(RelationWith.targetsOfTypes(types));
            }
        }
        return filters.isEmpty() ? RelationFilter.all() : filters.toArray(new RelationFilter[filters.size()]);
    }

}
