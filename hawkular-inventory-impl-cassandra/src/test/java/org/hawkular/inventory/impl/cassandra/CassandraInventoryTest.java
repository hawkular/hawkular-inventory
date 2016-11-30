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
import org.hawkular.inventory.api.test.AbstractBaseInventoryTestsuite;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.datastax.driver.core.Row;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public class CassandraInventoryTest extends AbstractBaseInventoryTestsuite<Row> {
    private static CassandraDaemon CASSANDRA_DAEMON;
    private static CassandraInventory INVENTORY;

    @Rule public TestName name = new TestName();

    @BeforeClass
    public static void init() throws Exception {
//        RxJavaPlugins.getInstance().registerObservableExecutionHook(new DebugHook<>(
//                new DebugNotificationListener<Object>() {
//                    @Override public <T> T onNext(DebugNotification<T> n) {
//                        Log.LOG.debugf("onNext on %s", n);
//                        return super.onNext(n);
//                    }
//
//                    @Override public <T> Object start(DebugNotification<T> n) {
//                        Log.LOG.debugf("start on %s", n);
//                        return super.start(n);
//                    }
//
//                    @Override public void complete(Object context) {
//                        Log.LOG.debugf("complete on %s", context);
//                        super.complete(context);
//                    }
//
//                    @Override public void error(Object context, Throwable e) {
//                        Log.LOG.debugf(e, "error on %s", context);
//                        super.error(context, e);
//                    }
//                }));

        URL cassandraConfigFile = CassandraInventoryTest.class.getResource("/cassandra-config.yaml");
        System.setProperty("cassandra.config", cassandraConfigFile.toString());
        CASSANDRA_DAEMON = new CassandraDaemon(true);
        CASSANDRA_DAEMON.activate();
        INVENTORY = new CassandraInventory();
        setupNewInventory(INVENTORY);
        setupData(INVENTORY);
    }

    @AfterClass
    public static void shutdown() throws Exception {
        try {
            teardownData(INVENTORY);
        } finally {
            if (CASSANDRA_DAEMON != null) {
                CASSANDRA_DAEMON.deactivate();
            }
        }
    }

    @Before
    public void reportStart() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> " + name.getMethodName());
    }

    @After
    public void reportEnd() {
        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<< " + name.getMethodName());
    }

    @Override protected CassandraInventory getInventoryForTest() {
        return INVENTORY;
    }
}
