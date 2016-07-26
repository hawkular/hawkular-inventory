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

import static org.hawkular.inventory.api.filters.With.path;
import static org.hawkular.inventory.rest.Utils.createUnder;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.TransactionFrame;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.json.DetypedPathDeserializer;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.hawkular.inventory.rest.cdi.AutoTenant;
import org.hawkular.inventory.rest.cdi.Our;
import org.hawkular.inventory.rest.cdi.TenantAware;
import org.hawkular.inventory.rest.security.Security;
import org.hawkular.inventory.rest.security.TenantId;
import org.jboss.resteasy.annotations.GZIP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
    private ObjectMapper deprecatedMapper;

    @Inject @TenantAware
    private ObjectMapper defaultMapper;

    private final int pathLength;

    /**
     * @deprecated used by the deprecated REST API
     */
    @Deprecated
    protected RestBase() {
        this(0);
    }

    protected RestBase(int pathLength) {
        this.pathLength = pathLength;
    }

    protected <T> Response.ResponseBuilder pagedResponse(Response.ResponseBuilder response,
                                                         UriInfo uriInfo, Page<T> page) {
        return pagedResponse(response, uriInfo, getMapper(), page);
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

    protected CanonicalPath getTenantPath() {
        return CanonicalPath.of().tenant(getTenantId()).get();
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

    protected Traverser getTraverser(UriInfo ctx) {
        Query.Builder queryPrefix = Query.builder().path().with(
                path(CanonicalPath.of().tenant(getTenantId()).get()));

        return new Traverser(ctx.getBaseUri().getPath().length() + pathLength, queryPrefix,
                str -> CanonicalPath.fromPartiallyUntypedString(str, getTenantPath(), (SegmentType) null));
    }

    protected String getPath(UriInfo uriInfo) {
        return getPath(uriInfo, 0);
    }

    protected String getPath(UriInfo uriInfo, int excludeFromEnd) {
        String chopped = uriInfo.getPath(false).substring(pathLength);
        if (excludeFromEnd > 0) {
            chopped = chopped.substring(0, chopped.length() - excludeFromEnd);
        }

        return chopped;
    }

    protected Object create(CanonicalPath parentPath, SegmentType elementType, Reader input)
            throws IOException {
        Class<?> blueprintType = Inventory.types().bySegment(elementType).getBlueprintType();

        JsonNode data = getMapper().readTree(input);

        setupMapper(parentPath);

        if (data.isArray()) {
            TransactionFrame frame = inventory.newTransactionFrame();

            Inventory inv = frame.boundInventory();
            try {
                List<Object> result = new ArrayList<>(data.size());
                for (JsonNode datum : data) {
                    Object blueprint = getMapper().reader().forType(blueprintType).readValue(datum);
                    Object entity = createUnder(inv, parentPath, elementType, blueprint);
                    result.add(entity);
                }

                frame.commit();

                return result;
            } catch (Throwable t) {
                frame.rollback();
                throw t;
            }
        } else {
            Object blueprint = getMapper().reader().forType(blueprintType).readValue(data);
            return createUnder(inventory, parentPath, elementType, blueprint);
        }
    }

    protected ObjectMapper getMapper() {
        //hackity hack - we detect whether we need an object mapper for the deprecated or new API by checking the
        //advertised path prefix length - the deprecated API sets this to 0 (through the use of the default ctor of
        //this class), while the classes in the new API declare their context prefix length correctly.

        if (pathLength == 0) {
            return deprecatedMapper;
        } else {
            return defaultMapper;
        }
    }

    protected void setupMapper(CanonicalPath relativePathOrigin) {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(getTenantPath());
        DetypedPathDeserializer.setCurrentRelativePathOrigin(relativePathOrigin);
        DetypedPathDeserializer.setCurrentEntityType(null);
    }
}
