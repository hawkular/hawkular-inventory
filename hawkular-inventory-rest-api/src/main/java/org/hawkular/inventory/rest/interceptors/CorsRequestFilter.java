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
import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.jaxrs.filter.cors.CorsFilters;
import org.hawkular.jaxrs.filter.cors.Headers;
import org.hawkular.jaxrs.filter.cors.OriginPredicate;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;

/**
 * @author Lukas Krejci
 * @since 1.0.1
 */
@Provider
@PreMatching
@Priority(0)
@ServerInterceptor
public class CorsRequestFilter implements ContainerRequestFilter {

    @Inject
    private Configuration configuration;

    private Predicate<String> originValidation;

    @PostConstruct
    protected void initValidation() {
        String allowedOrigins = configuration.getProperty(CorsProperties.ALLOWED_CORS_ORIGINS,
                Headers.ALLOW_ALL_ORIGIN);
        originValidation = new OriginPredicate(allowedOrigins);
    }

    @Override public void filter(ContainerRequestContext requestContext) throws IOException {
        CorsFilters.filterRequest(requestContext, originValidation);
    }
}
