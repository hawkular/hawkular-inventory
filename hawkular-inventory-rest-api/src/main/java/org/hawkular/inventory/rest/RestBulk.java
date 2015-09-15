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
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.TransactionFrame;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.ElementTypeVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Resource;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
@javax.ws.rs.Path("/bulk")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/bulk", description = "Endpoint for bulk operations on inventory entities")
public class RestBulk extends RestBase {
    @POST
    @javax.ws.rs.Path("/resources")
    @ApiOperation("Bulk creation of new resources. The response body contains details about results of creation" +
            " of individual resources. The return value is a map where keys are canonical paths of the resources" +
            " to be created and values are HTTP status codes - 201 OK, 400 if invalid path is supplied, 409 if " +
            " a resource already exists on given path or 500 in case of internal error.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resources successfully created"),
    })
    public Response addResources(@ApiParam("This is a map where keys are paths to the parents under which resources " +
            "should be created. The values are arrays of resource blueprint objects.")
                                 Map<String, List<Resource.Blueprint>> resources, @Context UriInfo uriInfo) {

        CanonicalPath rootPath = CanonicalPath.of().tenant(getTenantId()).get();

        Map<CanonicalPath, Integer> statuses = bulkCreate(resources, rootPath);

        return Response.status(CREATED).entity(statuses).build();
    }


    private Map<CanonicalPath, Integer> bulkCreate(Map<String, List<Resource.Blueprint>> resources,
                                                   CanonicalPath rootPath) {
        Map<CanonicalPath, Integer> statuses = new HashMap<>();

        TransactionFrame transaction = inventory.newTransactionFrame();
        Inventory binv = transaction.boundInventory();

        try {
            for (Map.Entry<String, List<Resource.Blueprint>> e : resources.entrySet()) {
                List<Resource.Blueprint> newChildren = e.getValue();

                CanonicalPath parentPath = canonicalize(e.getKey(), rootPath);

                if (!parentPath.modified().canExtendTo(Resource.class)) {
                    statuses.put(parentPath, BAD_REQUEST.getStatusCode());
                    continue; //map iteration
                }

                if (!security.canCreate(Resource.class).under(parentPath)) {
                    for (Resource.Blueprint b : newChildren) {
                        statuses.put(parentPath.extend(Resource.class, b.getId()).get(), FORBIDDEN.getStatusCode());
                    }
                    continue; //map iteration
                }

                for (Resource.Blueprint b : newChildren) {
                    CanonicalPath childPath = parentPath.extend(Resource.class, b.getId()).get();

                    parentPath.accept(new ElementTypeVisitor.Simple<Void, Void>() {
                        @Override
                        protected Void defaultAction() {
                            RestApiLogger.LOGGER.resourceCreationNotSupported(parentPath);
                            statuses.put(childPath, INTERNAL_SERVER_ERROR.getStatusCode());
                            return null;
                        }

                        @Override
                        public Void visitEnvironment(Void parameter) {
                            Environments.Single env = binv.inspect(parentPath, Environments.Single.class);
                            statuses.put(childPath, createWithStatus(env.feedlessResources()));
                            return null;
                        }

                        @Override
                        public Void visitFeed(Void parameter) {
                            Feeds.Single feed = binv.inspect(parentPath, Feeds.Single.class);
                            statuses.put(childPath, createWithStatus(feed.resources()));
                            return null;
                        }

                        @Override
                        public Void visitResource(Void parameter) {
                            Resources.Single resource = binv.inspect(parentPath, Resources.Single.class);
                            statuses.put(childPath, createWithStatus(resource.containedChildren()));
                            return null;
                        }

                        private int createWithStatus(Resources.ReadWrite rw) {
                            try {
                                rw.create(b);
                                return CREATED.getStatusCode();
                            } catch (EntityAlreadyExistsException e) {
                                return CONFLICT.getStatusCode();
                            } catch (Exception e) {
                                RestApiLogger.LOGGER.failedToCreateBulkResource(childPath, e);
                                return INTERNAL_SERVER_ERROR.getStatusCode();
                            }
                        }
                    }, null);
                }
            }
            transaction.commit();
            return statuses;
        } catch (Throwable t) {
            transaction.rollback();
            throw t;
        }
    }

    private static CanonicalPath canonicalize(String path, CanonicalPath rootPath) {
        Path p;
        if (path == null || path.isEmpty()) {
            p = rootPath;
        } else {
            p = Path.fromPartiallyUntypedString(path, rootPath, rootPath, Entity.class);
        }
        if (p.isRelative()) {
            p = p.toRelativePath().applyTo(rootPath);
        }
        return p.toCanonicalPath();
    }
}
