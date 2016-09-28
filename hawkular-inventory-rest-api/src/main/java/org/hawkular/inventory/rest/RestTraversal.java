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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

/**
 * @author Lukas Krejci
 * @since 0.16.0
 */
@Path("/traversal")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class RestTraversal extends RestBase {

    public RestTraversal() {
        super("/traversal".length());
    }

    @GET
    @Path("{path:.+}")
    public Response get(@Context UriInfo uriInfo) throws Exception {

        Traverser traverser = getTraverser(uriInfo);

        Query q = traverser.navigate(getPath(uriInfo));

        Pager pager = RequestUtil.extractPaging(uriInfo);

        @SuppressWarnings("unchecked")
        Page<AbstractElement<?, ?>> results = inventory(uriInfo).execute(q, (Class) AbstractElement.class, pager);

        return pagedResponse(Response.ok(), uriInfo, results).build();
    }
}
