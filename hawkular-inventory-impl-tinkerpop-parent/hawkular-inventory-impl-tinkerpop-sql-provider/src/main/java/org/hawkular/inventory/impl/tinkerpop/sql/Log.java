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
package org.hawkular.inventory.impl.tinkerpop.sql;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
@MessageLogger(projectCode = "HAWKINV")
@ValidIdRange(min = 30000, max = 30099)
public interface Log {
    Log LOG = Logger.getMessageLogger(Log.class, "org.hawkular.inventory.impl.tinkerpop.sql");

    @Message(id = 30000, value = "Using datasource: %s")
    @LogMessage(level = Logger.Level.INFO)
    void iUsingDatasource(String jndi);

    @Message(id = 30001, value = "Using JDBC URL: %s")
    @LogMessage(level = Logger.Level.INFO)
    void iUsingJdbcUrl(String connectionString);
}
