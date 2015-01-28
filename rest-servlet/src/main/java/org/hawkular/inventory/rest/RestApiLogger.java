package org.hawkular.inventory.rest;

import org.jboss.logging.*;

/**
 * Logger definitions for Jboss Logging
 *
 * @author Heiko W. Rupp
 */
@MessageLogger(projectCode = "HAWK")
public interface RestApiLogger {

    RestApiLogger LOGGER = Logger.getMessageLogger(RestApiLogger.class,"org.hawkular.inventory.rest");


    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 3000, value = "Hawkular-Inventory REST Api is starting...")
    void apiStarting();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 3001, value = "Something bad has happened")
    void warn(@Cause Throwable t);

}
