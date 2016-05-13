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

import static org.hawkular.inventory.rest.Utils.getSegmentTypeFromSimpleName;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.IdentityHashed;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.IdentityHash;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 0.16.0
 */
@Path("/entity")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class RestEntity extends RestBase {

    public RestEntity() {
        super("/entity".length());
    }

    @GET
    @Path("{path:.+}/treeHash")
    @SuppressWarnings("unchecked")
    public IdentityHash.Tree getTreeHash(@Context UriInfo uriInfo)
            throws Exception {

        CanonicalPath path = CanonicalPath.fromPartiallyUntypedString(getPath(uriInfo, "/treeHash".length()),
                getTenantPath(), AbstractElement.class);

        return inventory.inspect(path, IdentityHashed.Single.class).treeHash();
    }

    @GET
    @Path("{path:.+}")
    @SuppressWarnings("unchecked")
    public Object get(@Context UriInfo uriInfo)
            throws Exception {

        CanonicalPath path = CanonicalPath.fromPartiallyUntypedString(getPath(uriInfo), getTenantPath(),
                AbstractElement.class);

        return inventory.inspect(path, ResolvableToSingle.class).entity();
    }


    @POST
    @Path("{path:.+}")
    @SuppressWarnings("unchecked")
    public Response post(@Context UriInfo uriInfo, Reader input) throws Exception {
        String pathAndType = getPath(uriInfo);

        int slashIdx = pathAndType.lastIndexOf('/');

        String parent = pathAndType.substring(0, slashIdx);
        String entityType = pathAndType.substring(slashIdx + 1);

        SegmentType st = getSegmentTypeFromSimpleName(entityType);

        if (parent.isEmpty()) {
            parent = "/";
        }

        CanonicalPath parentPath = CanonicalPath.fromPartiallyUntypedString(parent, getTenantPath(),
                AbstractElement.class);

        if (st == SegmentType.rl) {
            if (!security.canAssociateFrom(parentPath)) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        } else {
            if (!security.canCreate(Inventory.types().bySegment(st).getElementType()).under(parentPath)) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        Object toReport = create(parentPath, st, input);
        if (toReport instanceof Collection) {
            return ResponseUtil.created((Collection<? extends AbstractElement<?, ?>>) toReport, uriInfo).build();
        } else {
            return ResponseUtil.created((AbstractElement<?, ?>) toReport, uriInfo).build();
        }
    }

    @PUT
    @Path("{path:.+}")
    public Response put(@Context UriInfo uriInfo, Reader input) throws Exception {
        String path = getPath(uriInfo);

        CanonicalPath entityPath = CanonicalPath.fromPartiallyUntypedString(path, getTenantPath(),
                AbstractElement.class);

        if (!security.canUpdate(entityPath)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Class<? extends AbstractElement.Update> updateType = Inventory.types().byPath(entityPath).getUpdateType();

        doPut(entityPath, updateType, input);

        return Response.noContent().build();
    }

    @DELETE
    @Path("{path:.+}")
    @ApiOperation("Deletes an inventory entity on the given location.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Entity deleted."),
            @ApiResponse(code = 404, message = "No entity found on given traversal URI."),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    public Response delete(@Context UriInfo uriInfo) {
        String path = getPath(uriInfo);

        CanonicalPath entityPath = CanonicalPath.fromPartiallyUntypedString(path, getTenantPath(),
                AbstractElement.class);

        if (entityPath.getSegment().getElementType() == SegmentType.rl) {
            Relationship rl = inventory.inspect(entityPath, Relationships.Single.class).entity();
            if (!security.canAssociateFrom(rl.getSource())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        } else {
            if (!security.canDelete(entityPath)) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        inventory.inspect(entityPath, Inventory.types().byPath(entityPath).getSingleAccessorType()).delete();

        return Response.noContent().build();
    }

    private <U extends AbstractElement.Update> void doPut(CanonicalPath path, Class<U> updateType, Reader data)
            throws IOException {
        setupMapper(path);
        U update = getMapper().reader().forType(updateType).readValue(data);
        inventory.inspect(path, Inventory.types().byUpdate(updateType).getSingleAccessorType()).update(update);
    }
}
