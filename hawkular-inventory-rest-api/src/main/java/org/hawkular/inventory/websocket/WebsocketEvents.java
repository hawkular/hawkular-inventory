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

package org.hawkular.inventory.websocket;

import java.util.Arrays;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.rest.RestApiLogger;
import org.hawkular.inventory.rest.cdi.AutoTenant;
import org.hawkular.inventory.rest.cdi.Our;
import org.hawkular.inventory.rest.security.SecurityIntegration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import rx.Subscription;

/**
 * @author Jirka Kremser
 */

@ApplicationScoped
@ServerEndpoint("/ws/popelnicek")
public class WebsocketEvents {

    @Inject
    @AutoTenant
    protected Inventory inventory;

    @Inject @Our
    protected ObjectMapper mapper;

    @OnOpen
    public void open(Session session) {

        RestApiLogger.LOGGER.error("\n\n\nWS opened..: " + session.getId() + "\n\n\n");
        final String type = "resource";
        final String actionString = "created";
        session.getQueryString();

        Class cls = SecurityIntegration.getClassFromName(type);
        if (cls == null) {
            session.getAsyncRemote().sendText("Unknown type: " + type);
        }
        Action.Enumerated actionEnumItem;
        try {
            actionEnumItem = Action.Enumerated.valueOf(actionString.toUpperCase());
        } catch (IllegalArgumentException iae) {
            Optional<String> allowedValues = Arrays.stream(Action.Enumerated.values())
                    .map((a) -> a.name().toLowerCase() + " ")
                    .reduce(String::concat);
            session.getAsyncRemote().sendText("Unknown action: " + actionString +
                    ", allowed values: " + allowedValues.get());
            return;
        }
        Action<?, ?> action = actionEnumItem.getAction();

        final Subscription subscribe = inventory.observable(Interest.in(cls).being(action))
//                .filter(getFilter(action, tenantId))
//                .buffer(20, TimeUnit.SECONDS)
                .subscribe((x) -> {
                    try {
                        session.getAsyncRemote().sendText(mapper.writeValueAsString(x));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                });

    }

    @OnClose
    public void close(Session session) {
        RestApiLogger.LOGGER.error("\n\n\nWS closed.." + session.getId() + "\n\n\n");
    }

    @OnError
    public void onError(Throwable error) {
        RestApiLogger.LOGGER.error("\n\n\nWS error..\n\n\n");
    }

    @OnMessage
    public void handleMessage(String message, Session session) {
        RestApiLogger.LOGGER.error("\n\n\nWS message: " + message + "\n\n\n");
    }

}
