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
package org.hawkular.inventory.websocket;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
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
import org.hawkular.inventory.rest.RestEvents;
import org.hawkular.inventory.rest.cdi.AutoTenant;
import org.hawkular.inventory.rest.cdi.Our;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

import rx.Subscription;

/**
 * @author Jirka Kremser
 */

@ApplicationScoped
@ServerEndpoint("/ws/events")
public class WebsocketEvents {

    private Map<Session, Subscription> subscriptions = Maps.newHashMap();

    @Inject
    @AutoTenant
    protected Inventory inventory;

    @Inject @Our
    protected ObjectMapper mapper;

    @OnOpen
    public void open(Session session) {

        WebsocketApiLogger.LOGGER.sessionOpened(session.getId());
        Map<String, String> queryParamStringMap = parseQueryParams(session);
        final String type = queryParamStringMap.getOrDefault(QueryParam.type.name(), QueryParam.type.getDefaultValue());
        final String actionString = queryParamStringMap.getOrDefault(QueryParam.action.name(),
                QueryParam.action.getDefaultValue());
        final String tenantId = queryParamStringMap.getOrDefault(QueryParam.tenantId.name(),
                QueryParam.tenantId.getDefaultValue());

        if (tenantId == null) {
            session.getAsyncRemote().sendText("Provide the tenantId query parameter.");
            closeSession(session);
            return;
        }

        Class cls = Inventory.getEntityType(type);
        if (cls == null) {
            session.getAsyncRemote().sendText("Unknown type: " + type);
            closeSession(session);
            return;
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
            closeSession(session);
            return;
        }
        Action<?, ?> action = actionEnumItem.getAction();

        final Subscription subscription = inventory.observable(Interest.in(cls).being(action))
                .filter(RestEvents.getFilter(action, tenantId))
                .subscribe((x) -> {
                    try {
                        session.getAsyncRemote().sendText(mapper.writeValueAsString(x));
                    } catch (JsonProcessingException e) {
                        session.getAsyncRemote().sendText("Unable to serialize JSON.");
                        WebsocketApiLogger.LOGGER.serializationFailed(e);
                        closeSession(session);
                    }
                });
        subscriptions.put(session, subscription);
    }

    @OnClose
    public void close(Session session) {
        WebsocketApiLogger.LOGGER.sessionClosed(session.getId());
        Subscription subscription = subscriptions.get(session);
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
    }

    @OnError
    public void onError(Throwable error) {
        WebsocketApiLogger.LOGGER.errorHappened(error);
    }

    @OnMessage
    public void handleMessage(String message, Session session) {
        WebsocketApiLogger.LOGGER.onMessage(session.getId(), message);
    }

    private void closeSession(Session session) {
        try {
            session.close();
        } catch (IOException exception) {
            WebsocketApiLogger.LOGGER.sessionCloseFailed(exception);
        }
    }

    private Map<String, String> parseQueryParams(Session session) {
        Map<String, String> retMap = Maps.newHashMap();
        String query = session.getQueryString();
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] nameval = param.split("=");
                retMap.put(nameval[0], nameval[1]);
            }
        }
        return retMap;
    }

    public static Map<String, String> getQueryMap(String query) {
        Map<String, String> map = Maps.newHashMap();
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] nameval = param.split("=");
                map.put(nameval[0], nameval[1]);
            }
        }
        return map;
    }

    private enum QueryParam {
        tenantId,
        type("resource"),
        action("created");

        private String defaultValue;

        QueryParam() {
            this(null);
        }

        QueryParam(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }
}
