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
package org.hawkular.inventory.rest.interceptors;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.rest.RestApiLogger;
import org.hawkular.inventory.rest.cdi.AutoTenant;
import org.hawkular.inventory.rest.security.Security;
import org.hawkular.inventory.rest.security.TenantId;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;

/**
 * A {@link ContainerRequestFilter} that auto-creates a new tenant based on the {@code tenantId} provided by the current
 * {@link Security} SPI. {@link AutocreateTenantRequestFilter} uses a simple local cache of {@code tenantId}s so that
 * the existence of the given {@code tenantId} does not need to be checked on every request.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@Provider
@ServerInterceptor
public class AutocreateTenantRequestFilter implements ContainerRequestFilter {

    /* URI chunks to which this filter should not be applied */
    private static final List<Pattern> uriExceptionPatterns = Stream.of(".*/inventory/status/?",
            ".*/inventory/ping/?", ".*/inventory/?").map(Pattern::compile).collect(Collectors.toList());

    private static final RestApiLogger log =
            Logger.getMessageLogger(RestApiLogger.class, AutocreateTenantRequestFilter.class.getName());

    private final Set<String> existingTenantIds = ConcurrentHashMap.newKeySet();

    @Inject
    @TenantId
    private Instance<String> tenantIdProducer;

    @Inject
    @AutoTenant
    private Inventory inventory;

    /**
     * @see javax.ws.rs.container.ContainerRequestFilter#filter(javax.ws.rs.container.ContainerRequestContext)
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        boolean shouldSkip = shouldSkip(requestContext.getUriInfo().getRequestUri().getPath());
        if (shouldSkip) {
            return;
        }

        String tenantId = tenantIdProducer.get();
        log.tracef("Checking if tenant [%s] needs to be auto-created", tenantId);
        /* do nothing if tenantId is unknown */
        if (tenantId != null) {
            if (!existingTenantIds.contains(tenantId)) {
                log.tracef("Tenant [%s] needs to be created", tenantId);
                try {
                    inventory.tenants().create(Tenant.Blueprint.builder().withId(tenantId).build());
                    log.tracef("Tenant [%s] auto-created successfully", tenantId);
                    existingTenantIds.add(tenantId);
                } catch (EntityAlreadyExistsException e) {
                    /* Probably created by another thread or during a previous run of the server */
                    log.tracef("Tenant [%s] could not be auto-created because it existed in the backend already",
                            tenantId);
                    existingTenantIds.add(tenantId);
                }
            } else {
                log.tracef("Tenant [%s] exists already", tenantId);
            }
        }
    }

    private boolean shouldSkip(String uri) {
        return uriExceptionPatterns.stream().anyMatch((p -> p.matcher(uri).matches()));
    }

}
