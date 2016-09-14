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
package org.hawkular.inventory.impl.tinkerpop.provider;

import java.net.URL;

import org.apache.cassandra.service.CassandraDaemon;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.hawkular.inventory.api.test.AbstractBaseInventoryTestsuite;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.impl.tinkerpop.TinkerpopInventory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public class TitanTest extends AbstractBaseInventoryTestsuite<Element> {

    private static CassandraDaemon CASSANDRA_DAEMON;
    private static TinkerpopInventory INVENTORY;

    @Rule public TestName name = new TestName();

    @BeforeClass
    public static void startCassandra() throws Exception {
        URL cassandraConfigFile = TitanTest.class.getResource("/cassandra-config.yaml");
        System.setProperty("cassandra.config", cassandraConfigFile.toString());
        CASSANDRA_DAEMON = new CassandraDaemon(true);
        CASSANDRA_DAEMON.activate();
        INVENTORY = new TinkerpopInventory();
        setupNewInventory(INVENTORY);
        setupData(INVENTORY);
    }

    @AfterClass
    public static void stopCassandra() throws Exception {
        teardownData(INVENTORY);
        if (CASSANDRA_DAEMON != null) {
            CASSANDRA_DAEMON.deactivate();
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

    @Override protected BaseInventory<Element> getInventoryForTest() {
        return INVENTORY;
    }
}
