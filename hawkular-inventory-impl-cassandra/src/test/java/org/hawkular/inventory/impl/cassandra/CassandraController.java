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
package org.hawkular.inventory.impl.cassandra;

import java.net.URL;

import org.apache.cassandra.service.CassandraDaemon;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class CassandraController {
    private static CassandraController INSTANCE;

    private CassandraDaemon daemon;

    private CassandraController() {
        URL cassandraConfigFile = CassandraInventoryTest.class.getResource("/cassandra-config.yaml");
        System.setProperty("cassandra.config", cassandraConfigFile.toString());
        daemon = new CassandraDaemon(true);
        daemon.activate();
    }

    synchronized static void start() {
        if (INSTANCE == null) {
            INSTANCE = new CassandraController();
        }
    }

    synchronized static void stop() {
        //nothing to do... it seems like cassandra cannot be restarted multiple times within the same JVM, so let's
        //not try that.
    }

    protected void finalize() throws Exception {
        if (daemon != null) {
            daemon.deactivate();
        }
    }
}
