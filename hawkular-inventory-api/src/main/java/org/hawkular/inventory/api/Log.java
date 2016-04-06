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
package org.hawkular.inventory.api;

import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
@MessageLogger(projectCode = "HAWKINV")
@ValidIdRange(min = 1, max = 999)
public interface Log extends BasicLogger {
    Log LOGGER = Logger.getMessageLogger(Log.class, "org.hawkular.inventory.api");

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1, value = "Error while sending inventory event.")
    void wErrorSendingEvent(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 2, value = "No data associated with data entity on path %s that is being deleted.")
    void wNoDataAssociatedWithEntity(CanonicalPath dataEntityPath);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 3, value = "Thread interrupted while waiting for a next retry of a previously failed transaction.")
    void wInterruptedWhileWaitingForTransactionRetry();

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 4, value = "Transaction failed: %s")
    void dTransactionFailed(String msg);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 5, value = "Silent rollback.")
    void wSilentRollback();
}
