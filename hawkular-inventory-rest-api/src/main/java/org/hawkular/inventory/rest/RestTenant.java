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

import static org.hawkular.inventory.rest.RequestUtil.extractPaging;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Change;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.16.0
 */
@Path("/tenant")
public class RestTenant extends RestBase {

    public RestTenant() {
        super("/tenant".length());
    }

    @GET
    public Tenant get(@Context UriInfo uriInfo) {
        return inventory(uriInfo).tenants().get(getTenantId()).entity();
    }

    @PUT
    public Response put(Tenant.Update update, @Context UriInfo uriInfo) {
        inventory(uriInfo).tenants().get(getTenantId()).update(update);
        return Response.noContent().build();
    }

    @GET
    @Path("/relationships")
    public Response getRelationships(@Context UriInfo uriInfo) {
        Traverser traverser = getTraverser(uriInfo);
        Query q = traverser.navigate(getPath(uriInfo));

        @SuppressWarnings("unchecked")
        Page<AbstractElement<?, ?>> results = inventory(uriInfo).execute(q, (Class) AbstractElement.class,
                extractPaging(uriInfo));

        return pagedResponse(Response.ok(), uriInfo, results).build();
    }

    @GET
    @Path("/history")
    public List<Change<?>> getHistory(@Context UriInfo uriInfo) {
        return getHistory(uriInfo, getTenantPath());
    }

    @POST
    @Path("/relationship")
    @SuppressWarnings("unchecked")
    public Response createRelationships(@Context UriInfo uriInfo, Reader input)
            throws IOException, URISyntaxException {
        CanonicalPath tenant = getTenantPath();

        Object toReport = create(tenant, SegmentType.rl, uriInfo, input);
        if (toReport instanceof Collection) {
            return ResponseUtil.created((Collection<? extends AbstractElement<?, ?>>) toReport, uriInfo).build();
        } else {
            return ResponseUtil.created((AbstractElement<?, ?>) toReport, uriInfo).build();
        }
    }
}
