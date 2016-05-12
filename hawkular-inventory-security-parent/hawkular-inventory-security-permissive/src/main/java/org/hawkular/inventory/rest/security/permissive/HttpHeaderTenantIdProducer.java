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
package org.hawkular.inventory.rest.security.permissive;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.hawkular.inventory.rest.security.TenantId;

/**
 * @author Jirka Kremser
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @since 0.3.4
 */
@RequestScoped
public class HttpHeaderTenantIdProducer {
    public static final String TENANT_HEADER_NAME = "Hawkular-Tenant";

    @Inject
    private HttpServletRequest request;

    public HttpHeaderTenantIdProducer() {
    }

    @Produces
    @TenantId
    public String getTenantId() {
        if (request == null) {
            throw new IllegalStateException("request was not injected");
        }
        String tenantId = request.getHeader(TENANT_HEADER_NAME);
        if (tenantId == null) {
            /* IllegalArgumentException because it gets mapped to return code 400
             * by org.hawkular.inventory.rest.exception.mappers.IllegalArgumentExceptionMapper */
            throw new IllegalArgumentException(TENANT_HEADER_NAME + " HTTP header must be provided");
        }
        return tenantId;
    }

}
