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
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.container.TimeoutHandler;
import javax.ws.rs.core.Response;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.rest.json.ApiError;
import org.hawkular.inventory.rest.security.SecurityIntegration;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import rx.Subscription;
import rx.functions.Func1;

/**
 * @author Jirka Kremser
 */
@Path("/events")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/events", description = "Work with the events emitted by inventory", tags = "Events")
public class RestEvents extends RestBase {

    @Inject
    private RestRelationships restRelationships;

    @GET
    @Path("/")
    @ApiOperation("Listen on stream of the events")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized access"),
            @ApiResponse(code = 404, message = "Tenant doesn't exist", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public void getEvents(@Suspended AsyncResponse asyncResponse,
                          @QueryParam("type") @DefaultValue("resource") String type,
                          @QueryParam("action") @DefaultValue("created") String actionString) {

        String tenantId = getTenantId();

        Class cls = SecurityIntegration.getClassFromName(type);
        if (cls == null) {
            asyncResponse.resume(Response.status(BAD_REQUEST).entity("Unknown type: " + type)
                    .build());
        }
        Action.Enumerated actionEnumItem;
        try {
            actionEnumItem = Action.Enumerated.valueOf(actionString.toUpperCase());
        } catch (IllegalArgumentException iae) {
            Optional<String> allowedValues = Arrays.stream(Action.Enumerated.values())
                    .map((a) -> a.name().toLowerCase() + " ")
                    .reduce(String::concat);
            asyncResponse.resume(Response.status(BAD_REQUEST).entity("Unknown action: " + actionString +
                    ", allowed values: " + allowedValues.get()).build());
            return;
        }
        Action<?, ?> action = actionEnumItem.getAction();

        final Subscription subscribe = inventory.observable(Interest.in(cls).being(action))
                .filter(getFilter(action, tenantId))
                .buffer(20, TimeUnit.SECONDS)
                .subscribe((x) -> {
                    asyncResponse.resume(x);
                });

        asyncResponse.setTimeout(21, TimeUnit.SECONDS);
        asyncResponse.setTimeoutHandler(new TimeoutHandler() {
            @Override public void handleTimeout(AsyncResponse asyncResponse) {
                if (!subscribe.isUnsubscribed()) {
                    subscribe.unsubscribe();
                }
            }
        });
    }

    public static Func1<Object, Boolean> getFilter(Action<?, ?> action, String tenantId) {
        if (action == Action.updated()) {
            return (e) -> tenantId.equals(((AbstractElement) ((Action.Update) e).getOriginalEntity())
                    .getPath().ids().getTenantId());
        } else if (action == Action.copied()) {
            return (e) -> tenantId.equals(((Action.EnvironmentCopy) e).getSource().getPath().ids().getTenantId());
        } else {
            return (e) -> {
                if (e instanceof Relationship) {
                    return tenantId.equals(((Relationship) e).getSource().ids().getTenantId());
                }
                return tenantId.equals(((AbstractElement) e).getPath().ids().getTenantId());
            };
        }
    }
}
