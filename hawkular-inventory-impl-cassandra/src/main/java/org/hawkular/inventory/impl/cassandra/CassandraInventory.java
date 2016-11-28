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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import javax.net.ssl.SSLContext;

import org.cassalog.core.Cassalog;
import org.cassalog.core.CassalogBuilder;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.base.TransactionConstructor;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.JdkSSLOptions;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.SSLOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.google.common.collect.ImmutableMap;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class CassandraInventory extends BaseInventory<CElement> {
    public CassandraInventory() {
    }

    private CassandraInventory(CassandraInventory orig, TransactionConstructor<CElement> txCtor) {
        super(orig, null, txCtor);
    }

    @Override protected CassandraInventory cloneWith(TransactionConstructor<CElement> transactionCtor) {
        return new CassandraInventory(this, transactionCtor);
    }

    @Override protected CassandraBackend doInitialize(Configuration configuration) {
        try {
            Session session = connect(configuration);
            initSchema(session, configuration.getProperty(Prop.KEYSPACE, "hawkular_inventory"));

            //TODO implement
            return null;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not initialize Cassandra connection using the provided configuration.", e);
        }
    }

    private Session connect(Configuration configuration) {
        Cluster.Builder clusterBuilder = new Cluster.Builder();
        int port;
        try {
            port = Integer.parseInt(configuration.getProperty(Prop.PORT, "9042"));
        } catch (NumberFormatException nfe) {
            Log.LOG.warnInvalidCqlPort(configuration.getProperty(Prop.PORT, null), "9042", nfe);
            port = 9042;
        }
        clusterBuilder.withPort(port);
        Arrays.stream(configuration.getProperty(Prop.NODES, "127.0.0.1").split(","))
                .forEach(clusterBuilder::addContactPoint);

        if (Boolean.parseBoolean(configuration.getProperty(Prop.USE_SSL, "false"))) {
            SSLOptions sslOptions = null;
            try {
                String[] defaultCipherSuites = { "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA" };
                sslOptions = JdkSSLOptions.builder().withSSLContext(SSLContext.getDefault())
                        .withCipherSuites(defaultCipherSuites).build();
                clusterBuilder.withSSL(sslOptions);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SSL support is required but is not available in the JVM.", e);
            }
        }

        clusterBuilder.withoutJMXReporting();

        int newMaxConnections;
        try {
            newMaxConnections = Integer.parseInt(configuration.getProperty(Prop.MAX_CONN_HOST, "10"));
        } catch (NumberFormatException nfe) {
            Log.LOG.warnInvalidMaxConnections(configuration.getProperty(Prop.MAX_CONN_HOST, null), "10", nfe);
            newMaxConnections = 10;
        }
        int newMaxRequests;
        try {
            newMaxRequests = Integer.parseInt(configuration.getProperty(Prop.MAX_REQUEST_CONN, "5000"));
        } catch (NumberFormatException nfe) {
            Log.LOG.warnInvalidMaxRequests(configuration.getProperty(Prop.MAX_REQUEST_CONN, null), "5000", nfe);
            newMaxRequests = 5000;
        }
        int driverRequestTimeout;
        try {
            driverRequestTimeout = Integer.parseInt(configuration.getProperty(Prop.REQUEST_TIMEOUT, "12000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidRequestTimeout(configuration.getProperty(Prop.REQUEST_TIMEOUT, null), "12000", e);
            driverRequestTimeout = 12000;
        }
        int driverConnectionTimeout;
        try {
            driverConnectionTimeout = Integer.parseInt(configuration.getProperty(Prop.CONNECTION_TIMEOUT, "5000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidConnectionTimeout(configuration.getProperty(Prop.CONNECTION_TIMEOUT, null), "5000", e);
            driverConnectionTimeout = 5000;
        }
        int driverSchemaRefreshInterval;
        try {
            driverSchemaRefreshInterval = Integer.parseInt(
                    configuration.getProperty(Prop.SCHEMA_REFRESH_INTERVAL, "1000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidSchemaRefreshInterval(configuration.getProperty(Prop.SCHEMA_REFRESH_INTERVAL, null),
                    "1000", e);
            driverSchemaRefreshInterval = 1000;
        }
        int driverPageSize;
        try {
            driverPageSize = Integer.parseInt(configuration.getProperty(Prop.PAGE_SIZE, "1000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidPageSize(configuration.getProperty(Prop.PAGE_SIZE, null), "1000", e);
            driverPageSize = 1000;
        }
        clusterBuilder.withPoolingOptions(new PoolingOptions()
                .setMaxConnectionsPerHost(HostDistance.LOCAL, newMaxConnections)
                .setMaxConnectionsPerHost(HostDistance.REMOTE, newMaxConnections)
                .setMaxRequestsPerConnection(HostDistance.LOCAL, newMaxRequests)
                .setMaxRequestsPerConnection(HostDistance.REMOTE, newMaxRequests)
        ).withSocketOptions(new SocketOptions()
                .setReadTimeoutMillis(driverRequestTimeout)
                .setConnectTimeoutMillis(driverConnectionTimeout)
        ).withQueryOptions(new QueryOptions()
                .setFetchSize(driverPageSize)
                .setRefreshSchemaIntervalMillis(driverSchemaRefreshInterval));

        Cluster cluster = clusterBuilder.build();
        cluster.init();
        Session createdSession = null;
        try {
            createdSession = cluster.connect();
            return createdSession;
        } finally {
            if (createdSession == null) {
                cluster.close();
            }
        }
    }

    private void initSchema(Session session, String keyspace) throws URISyntaxException {
        session.execute("USE system");

        CassalogBuilder builder = new CassalogBuilder();
        Cassalog cassalog = builder.withKeyspace(keyspace).withSession(session).build();
        Map<String, ?> vars  = ImmutableMap.of(
                "keyspace", keyspace,
                "reset", true,
                "session", session
        );

        URI script = getClass().getResource("/schema/cassalog-schema.groovy").toURI();
        cassalog.execute(script, vars);

        //CQL injection, anyone?
        session.execute("USE " + keyspace);

        session.execute("INSERT INTO " + keyspace + ".sys_config (config_id, name, value) VALUES " +
                "('org.hawkular.metrics', 'version', '" + getCassandraInventoryVersion() + "')");

    }

    private enum Prop implements Configuration.Property {
        NODES("hawkular.inventory.cassandra.nodes",
                Arrays.asList("hawkular.inventory.cassandra.nodes", "hawkular.metrics.cassandra.nodes"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_NODES", "CASSANDRA_NODES")),

        PORT("hawkular.inventory.cassandra.port",
                Arrays.asList("hawkular.inventory.cassandra.port", "hawkular.metrics.cassandra.cql-port"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_PORT", "CASSANDRA_CQL_PORT")),

        KEYSPACE("hawkular.inventory.cassandra.keyspace",
                Arrays.asList("hawkular.inventory.cassandra.keyspace", "hawkular.metrics.cassandra.keyspace"),
                null),

        USE_SSL("hawkular.inventory.cassandra.use-ssl",
                Arrays.asList("hawkular.inventory.cassandra.use-ssl", "hawkular.metrics.cassandra.use-ssl"),
                Arrays.asList("HAWKULA_INVENTORY_CASSANDRA_USE_SSL", "CASSANDRA_USESSL")),

        MAX_CONN_HOST("hawkular.inventory.cassandra.max-connections-per-host",
                Arrays.asList("hawkular.inventory.cassandra.max-connections-per-host",
                        "hawkular.metrics.cassandra.max-connections-per-host"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_MAX_CONN_HOST", "CASSANDRA_MAX_CONN_HOST")),

        MAX_REQUEST_CONN("hawkular.inventory.cassandra.max-requests-per-connection",
                Arrays.asList("hawkular.inventory.cassandra.max-requests-per-connection",
                        "hawkular.metrics.cassandra.max-requests-per-connection"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_MAX_REQUEST_CONN", "CASSANDRA_MAX_REQUEST_CONN")),

        REQUEST_TIMEOUT("hawkular.inventory.cassandra.request-timeout",
                Arrays.asList("hawkular.inventory.cassandra.request-timeout",
                        "hawkular.metrics.cassandra.request-timeout"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_REQUEST_TIMEOUT", "CASSANDRA_REQUEST_TIMEOUT")),

        CONNECTION_TIMEOUT("hawkular.inventory.cassandra.connection-timeout",
                Arrays.asList("hawkular.inventory.cassandra.connection-timeout",
                        "hawkular.metrics.cassandra.connection-timeout"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_CONNECTION_TIMEOUT", "CASSANDRA_CONNECTION_TIMEOUT")),

        SCHEMA_REFRESH_INTERVAL("hawkular.inventory.cassandra.schema.refresh-interval",
                Arrays.asList("hawkular.inventory.cassandra.schema.refresh-interval",
                        "hawkular.metrics.cassandra.schema.refresh-interval"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_SCHEMA_REFRESH_INTERVAL",
                        "CASSANDRA_SCHEMA_REFRESH_INTERVAL")),

        PAGE_SIZE("hawkular.inventory.cassandra.page-size",
                Arrays.asList("hawkular.inventory.cassandra.page-size",
                        "hawkular.metrics.cassandra.page-size"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_PAGE_SIZE", "PAGE_SIZE"));

        private final String propertyName;
        private final List<String> systemProperties;
        private final List<String> envVars;

        Prop(String propertyName, List<String> systemProperties,
             List<String> envVars) {
            this.propertyName = propertyName;
            this.systemProperties =
                    systemProperties == null ? Collections.singletonList(propertyName) : systemProperties;
            this.envVars = envVars == null ? Collections.emptyList() : envVars;
        }

        @Override public String getPropertyName() {
            return propertyName;
        }

        @Override public List<String> getSystemPropertyNames() {
            return systemProperties;
        }

        @Override public List<String> getEnvironmentVariableNames() {
            return envVars;
        }
    }

    private String getCassandraInventoryVersion() {
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                Manifest manifest = new Manifest(resource.openStream());
                String vendorId = manifest.getMainAttributes().getValue("Implementation-Vendor-Id");
                if (vendorId != null && vendorId.equals("org.hawkular.inventory")) {
                    return manifest.getMainAttributes().getValue("Implementation-Version");
                }
            }
            throw new IllegalStateException("Failed to extract the version of Cassandra backend for Hawkular" +
                    " Inventory from the manifest file.");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to extract the version of Cassandra backend for Hawkular Inventory from the manifest file.",
                    e);
        }
    }


}
