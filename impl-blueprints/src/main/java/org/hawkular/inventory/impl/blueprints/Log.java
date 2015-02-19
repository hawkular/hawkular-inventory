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
package org.hawkular.inventory.impl.blueprints;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Logger for the Inventory impl.
 *
 * Code range is 1000-1999
 *
 * @author Heiko W. Rupp
 */
@MessageLogger(projectCode = "HAWKINV")
interface Log {

    Log LOG = Logger.getMessageLogger(Log.class, "org.hawkular.inventory.impl");

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1000, value = "Something bad has happened: %s")
    void warn(String s);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1001, value = "No Topic Connection found (is 'java:/topic/HawkularNotifications' bound?), not " +
            "sending")
    void wNoTopicConnection();
}
