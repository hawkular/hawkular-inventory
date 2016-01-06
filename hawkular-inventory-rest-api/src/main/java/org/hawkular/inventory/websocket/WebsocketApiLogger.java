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


import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Logger definitions for Jboss Logging for the websockets api
 * <p>
 * Code range is 2900-2999
 *
 * @author Jirka Kremser
 */
@MessageLogger(projectCode = "HAWKINV")
public interface WebsocketApiLogger extends BasicLogger {

    WebsocketApiLogger LOGGER = Logger.getMessageLogger(WebsocketApiLogger.class, "org.hawkular.inventory.ws");

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 2900, value = "Websocket session opened: %s") void sessionOpened(String session);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 2901, value = "Websocket session closed: %s") void sessionClosed(String session);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 2902, value = "Something bad has happened.") void errorHappened(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 2903, value = "Websocket Session [%s]: Got message: %s") void onMessage(String session, String
            message);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 2904, value = "Problem with JSON serialization.") void serializationFailed(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 2905, value = "Unable to close the Websocket session.") void sessionCloseFailed(@Cause Throwable
                                                                                                          cause);
}
