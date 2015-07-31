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
package org.hawkular.inventory.bus;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
@MessageLogger(projectCode = "HWKINVENT")
@ValidIdRange(min = 310000, max = 319999)
public interface Log extends BasicLogger {
    Log LOG = Logger.getMessageLogger(Log.class, Log.class.getPackage().getName());

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 310000, value = "Unknown configuration property [%s]")
    void unknownConfigurationProperty(String propertyName);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 310001, value = "Failed to send message: %s")
    void failedToSendMessage(String message);
}
