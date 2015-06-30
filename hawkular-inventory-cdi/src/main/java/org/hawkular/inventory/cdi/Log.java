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
package org.hawkular.inventory.cdi;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
@MessageLogger(projectCode = "HAWKINV")
@ValidIdRange(min = 3500, max = 3999)
public interface Log extends BasicLogger {

    Log LOG = Logger.getMessageLogger(Log.class, "org.hawkular.inventory.cdi");

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 3500, value = "Cannot read the Hawkular Inventory configuration file at '%s'.")
    void wCannotReadConfigurationFile(String fileName, @Cause Throwable cause);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 3501, value = "Inventory backend failed to initialize in an attempt %d of %d.")
    void wInitializationFailure(int attempt, int maxAttempts);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 3502, value = "Using inventory implementation: %s")
    void iUsingImplementation(String className);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 3503, value = "Inventory initialized.")
    void iInitialized();
}
