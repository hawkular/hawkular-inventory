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

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.rest.cdi.AutoTenant;
import org.hawkular.inventory.rest.cdi.Our;
import org.hawkular.inventory.rest.security.RestConfiguration;
import org.hawkular.inventory.rest.security.Security;
import org.hawkular.inventory.rest.security.TenantIdProducer;
import org.jboss.resteasy.annotations.GZIP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
@GZIP
public class RestBase {

    @Inject
    @AutoTenant
    protected Inventory inventory;

    // using the @AllPermissive annotation the access will be always granted
    @Inject
    protected Security security;

    @Inject
    // using the @AllPermissive annotation the tenant will be always the same
    private TenantIdProducer tenantIdProducer;

    @Inject
    Configuration config;

    @Inject @Our
    protected ObjectMapper mapper;

    protected <T> Response.ResponseBuilder pagedResponse(Response.ResponseBuilder response,
                                                         UriInfo uriInfo, Page<T> page) {
        return pagedResponse(response, uriInfo, mapper, page);
    }

    protected <T> Response.ResponseBuilder pagedResponse(Response.ResponseBuilder response, UriInfo uriInfo,
                                                         ObjectMapper mapper, Page<T> page) {
        boolean streaming = config.getFlag(RestConfiguration.Keys.STREAMING_SERIALIZATION, RestConfiguration.Keys
                .STREAMING_SERIALIZATION.getDefaultValue());
        if (streaming) {
            return ResponseUtil.pagedResponse(response, uriInfo, mapper, page);
        } else {
            try {
                RestApiLogger.LOGGER.debug("Fetching data from backend");
                List<?> data = page.toList();
                RestApiLogger.LOGGER.debug("Finished fetching data from backend");
                return ResponseUtil.pagedResponse(response, uriInfo, page, mapper.writeValueAsString(data));
            } catch (JsonProcessingException e) {
                RestApiLogger.LOGGER.warn(e);
                // fallback to the default object mapper
                return ResponseUtil.pagedResponse(response, uriInfo, page, page.toList());
            } finally {
                RestApiLogger.LOGGER.debug("Finished building paged response (no data sent to client yet)");
            }
        }
    }


    protected String getTenantId() {
        return tenantIdProducer.getTenantId().get();
    }
}
