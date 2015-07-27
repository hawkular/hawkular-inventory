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
package org.hawkular.inventory.rest.interceptors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.io.IOUtils;
import org.hawkular.inventory.rest.RestApiLogger;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;

/**
 * @author Jirka Kremser
 * @since 0.2.0
 */

@Provider
@ServerInterceptor
public class LoggingInterceptor implements ContainerRequestFilter {

    private static final ObjectWriter WRITER = new ObjectMapper().writerWithDefaultPrettyPrinter();
    static {
        WRITER.with(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        // perhaps better than logger lvl could be a system property passed by -Dx=y
        if (RestApiLogger.LOGGER.isDebugEnabled()) {
            final String method = containerRequestContext.getMethod();
            final String url = containerRequestContext.getUriInfo().getRequestUri().toString();
            final StringBuilder headersStr = new StringBuilder();
            MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
            for (MultivaluedMap.Entry<String, List<String>> header : headers.entrySet()) {
                headersStr.append(header.getKey()).append(": ").append(header.getValue()).append('\n');
            }
            String json = null;
            if ("POST".equals(method) || "PUT".equals(method)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(containerRequestContext.getEntityStream(), baos);
                byte[] jsonBytes = baos.toByteArray();
                json = new String(jsonBytes, "UTF-8");

                json = WRITER.writeValueAsString(json);
                containerRequestContext.setEntityStream(new ByteArrayInputStream(jsonBytes));
            }
            RestApiLogger.LOGGER.restCall(method, url, headersStr.toString(), json == null ? "empty" : json);
        }
    }
}
