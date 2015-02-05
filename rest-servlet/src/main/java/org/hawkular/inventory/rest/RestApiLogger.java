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
package org.hawkular.inventory.rest;


import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Logger definitions for Jboss Logging for the rest api
 *
 * Code range is 2000-2999
 *
 * @author Heiko W. Rupp
 */
@MessageLogger(projectCode = "HAWKINV")
public interface RestApiLogger {

    RestApiLogger LOGGER = Logger.getMessageLogger(RestApiLogger.class, "org.hawkular.inventory.rest");


    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 2000, value = "Hawkular-Inventory REST Api is starting...")
    void apiStarting();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 2001, value = "Something bad has happened")
    void warn(@Cause Throwable t);

}
