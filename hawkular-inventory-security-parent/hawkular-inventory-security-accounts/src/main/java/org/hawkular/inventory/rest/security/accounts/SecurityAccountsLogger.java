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
package org.hawkular.inventory.rest.security.accounts;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@MessageLogger(projectCode = "HAWKINV")
public interface SecurityAccountsLogger extends BasicLogger {
    static SecurityAccountsLogger getLogger(Class<?> clazz) {
        return Logger.getMessageLogger(SecurityAccountsLogger.class, clazz.getName());
    }

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 3001, value = "Security check failed on entity: [%s]")
    void securityCheckFailed(String entityId, @Cause Throwable cause);

}
