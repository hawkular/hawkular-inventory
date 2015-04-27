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

import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.PageContext;
import org.hawkular.inventory.rest.json.Link;
import org.hawkular.inventory.rest.json.PagingCollection;

import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

/**
 * @author Lukas Krejci
 * @author Heiko W. Rupp
 * @since 0.0.1
 */
final class ResponseUtil {

    public static final MediaType WRAPPED_COLLECTION_JSON_TYPE = new MediaType("application",
            "vnd.hawkular.wrapped+json");

    /**
     * This method exists solely to concentrate usage of {@link javax.ws.rs.core.Response#created(java.net.URI)} into
     * one place until <a href="https://issues.jboss.org/browse/RESTEASY-1162">this JIRA</a> is resolved somehow.
     *
     * @param info the UriInfo instance of the current request
     * @param id   the ID of a newly created entity under the base
     * @return the response builder with status 201 and location set to the entity with the provided id.
     */
    public static Response.ResponseBuilder created(UriInfo info, String id) {
        return Response.status(Response.Status.CREATED).location(info.getRequestUriBuilder().path(id).build());
    }

    /**
     * Add the first acceptable media type from the provided headers to the response builder.
     *
     * @param builder the response builder
     * @param headers the headers containing the acceptable media types
     * @return the response builder
     */
    public static Response.ResponseBuilder withMediaType(Response.ResponseBuilder builder, HttpHeaders headers) {
        MediaType mediaType = headers.getAcceptableMediaTypes().get(0);
        builder.type(mediaType);
        return builder;
    }

    /**
     * Creates a response builder with "ok" response containing the provided list and applied pagination
     * as prescribed by the {@code page}.
     *
     * @param headers     the http headers
     * @param uriInfo     uri info
     * @param page        the "original" list coming from the backend containing the paging info
     * @param results     the result list with REST-ready entities
     * @param elementType the type of the entities contained in the result list
     * @param <T>         the type of the entities contained in the result list
     * @return the builder for the response
     */
    public static <T> Response.ResponseBuilder paginate(HttpHeaders headers, UriInfo uriInfo, Page<?> page,
            List<T> results, final Class<T> elementType) {
        Response.ResponseBuilder builder = Response.ok();
        withMediaType(builder, headers);

        MediaType mediaType = headers.getAcceptableMediaTypes().get(0);

        if (mediaType.equals(WRAPPED_COLLECTION_JSON_TYPE)) {
            wrapForPaging(builder, uriInfo, page, results);
        } else {
            ParameterizedType myType = new ParameterizedType() {
                final Type[] params = new Type[]{elementType};

                @Override
                public Type[] getActualTypeArguments() {
                    return params;
                }

                @Override
                public Type getRawType() {
                    return List.class;
                }

                @Override
                public Type getOwnerType() {
                    return null;
                }
            };
            GenericEntity<List<T>> list = new GenericEntity<>(results, myType);
            builder.entity(list);
            createPagingHeader(builder, uriInfo, page);
        }

        return builder;
    }

    /**
     * Create the paging headers for collections and attach them to the passed builder. Those are represented as
     * <i>Link:</i> http headers that carry the URL for the pages and the respective relation.
     * <br/>In addition a <i>X-Total-Count</i> header is created that contains the whole collection size.
     * <p/>
     * If you need no further "building" of the response apart from paginating, you should look into using
     * the {@link #paginate(javax.ws.rs.core.HttpHeaders, javax.ws.rs.core.UriInfo,
     * org.hawkular.inventory.api.paging.Page, java.util.List, Class)}.
     *
     * @param builder    The ResponseBuilder that receives the headers
     * @param uriInfo    The uriInfo of the incoming request to build the urls
     * @param resultList The collection with its paging information
     */
    public static void createPagingHeader(final Response.ResponseBuilder builder, final UriInfo uriInfo,
            final Page<?> resultList) {

        UriBuilder uriBuilder;

        PageContext pc = resultList.getPageContext();
        int page = pc.getPageNumber();

        if (resultList.getTotalSize() > (pc.getPageNumber() + 1) * pc.getPageSize()) {
            int nextPage = page + 1;
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
            uriBuilder.replaceQueryParam("page", nextPage);

            builder.header("Link", new Link("next", uriBuilder.build().toString()).rfc5988String());
        }

        if (page > 0) {
            int prevPage = page - 1;
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
            uriBuilder.replaceQueryParam("page", prevPage);
            builder.header("Link", new Link("prev", uriBuilder.build().toString()).rfc5988String());
        }

        // A link to the last page
        if (pc.isLimited()) {
            long lastPage = (resultList.getTotalSize() / pc.getPageSize()) - 1;
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
            uriBuilder.replaceQueryParam("page", lastPage);
            builder.header("Link", new Link("last", uriBuilder.build().toString()).rfc5988String());
        }

        // A link to the current page
        uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
        builder.header("Link", new Link("current", uriBuilder.build().toString()).rfc5988String());


        // Create a total size header
        builder.header("X-Total-Count", resultList.getTotalSize());
    }

    /**
     * Wrap the passed collection #resultList in an object with paging information
     * <p/>
     * If you need no further "building" of the response apart from paginating, you should look into using
     * the {@link #paginate(javax.ws.rs.core.HttpHeaders, javax.ws.rs.core.UriInfo,
     * org.hawkular.inventory.api.paging.Page, java.util.List, Class)}.
     *
     * @param builder      ResponseBuilder to add the entity to
     * @param uriInfo      UriInfo to construct paging links
     * @param originalList The original list to obtain the paging info from
     * @param resultList   The list of result items
     */
    public static <T> void wrapForPaging(Response.ResponseBuilder builder, UriInfo uriInfo, final Page<?> originalList,
            final Collection<T> resultList) {

        PagingCollection<T> pColl = new PagingCollection<T>(resultList);
        pColl.setTotalSize(originalList.getTotalSize());
        PageContext pageControl = originalList.getPageContext();
        pColl.setPageSize(pageControl.getPageSize());
        int page = pageControl.getPageNumber();
        pColl.setCurrentPage(page);
        long lastPage = (originalList.getTotalSize() / pageControl.getPageSize()) - 1; // -1 as page # is 0 based
        pColl.setLastPage(lastPage);

        UriBuilder uriBuilder;
        if (originalList.getTotalSize() > (page + 1) * pageControl.getPageSize()) {
            int nextPage = page + 1;
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
            uriBuilder.replaceQueryParam("page", nextPage);
            pColl.addLink(new Link("next", uriBuilder.build().toString()));
        }
        if (page > 0) {
            int prevPage = page - 1;
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
            uriBuilder.replaceQueryParam("page", prevPage);
            pColl.addLink(new Link("prev", uriBuilder.build().toString()));
        }

        // A link to the last page
        if (pageControl.isLimited()) {
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
            uriBuilder.replaceQueryParam("page", lastPage);
            pColl.addLink(new Link("last", uriBuilder.build().toString()));
        }

        // A link to the current page
        uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?ps and ?category if needed
        pColl.addLink(new Link("current", uriBuilder.build().toString()));

        builder.entity(pColl);
    }
}
