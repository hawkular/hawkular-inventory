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
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToSingleWithRelationships;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.model.Relationship;
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
    @GET
    @Path("{path:.*}/relationships")
    public Response get(@PathParam("path") String path,
                        @Context UriInfo uriInfo) {
        String securityId = toSecurityId(path);
        if (!Security.isValidId(securityId)) {
            return Response.status(NOT_FOUND).build();
        }
        CanonicalPath cPath = Security.getCanonicalPath(securityId);
        ResolvableToSingleWithRelationships<Relationship> resolvable = getResolvableFromCanonicalPath(cPath);
        Pager pager = extractPaging(uriInfo);
        Page<Relationship> relations = resolvable.relationships(Relationships.Direction.both).getAll().entities(pager);

        return pagedResponse(Response.ok(), uriInfo, relations).build();
    }

    private String toSecurityId(String urlPath) {
        if (urlPath == null) return null;
        return getTenantId() + "/" + urlPath;
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

}
