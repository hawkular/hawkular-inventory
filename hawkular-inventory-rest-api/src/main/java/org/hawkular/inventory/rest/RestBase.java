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
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.cdi.AutoTenant;
import org.hawkular.inventory.rest.cdi.Our;
import org.hawkular.inventory.rest.security.Security;
import org.hawkular.inventory.rest.security.TenantId;
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

    @Inject
    protected Security security;

    @Inject @TenantId
    private String tenantId;

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
        return tenantId;
    }

    protected CanonicalPath parsePath(List<PathSegment> uriPath) {
        StringBuilder bld = new StringBuilder("/");

        for (PathSegment seg : uriPath) {
            if (seg.getPath() != null) {
                bld.append(seg.getPath());
            }
            if (seg.getMatrixParameters() != null) {
                for (Map.Entry<String, List<String>> e : seg.getMatrixParameters().entrySet()) {
                    String param = e.getKey();
                    List<String> values = e.getValue();
                    if (values != null && !values.isEmpty()) {
                        for (String val : values) {
                            bld.append(";").append(param);
                            if (val != null) {
                                bld.append("=").append(val);
                            }
                        }
                    } else {
                        bld.append(";").append(param);
                    }
                }
            }
            bld.append("/");
        }

        bld.replace(bld.length() - 1, bld.length(), "");

        return CanonicalPath.fromPartiallyUntypedString(bld.toString(), CanonicalPath.of().tenant(getTenantId()).get
                (), Entity.class);
    }
}
