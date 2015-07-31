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
package org.hawkular.inventory.impl.tinkerpop.provider;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Range: 1500-1599
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
@MessageLogger(projectCode = "HAWKINV")
public interface Log {
    Log LOG = Logger.getMessageLogger(Log.class, "org.hawkular.inventory.impl");

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 1500, value = "Commencing re-indexing of Titan database index '%s'.")
    void iReindexing(String indexName);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 1501, value = "Re-indexing of Titan database index '%s' finished in %dms.")
    void iReindexingFinished(String indexName, long durationInMillis);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 1502, value = "Waiting for the index '%s' to become registered.")
    void iWaitingForIndexRegistration(String indexName);
}

