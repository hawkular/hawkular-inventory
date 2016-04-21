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

import static org.hawkular.commons.cassandra.EmbeddedConstants.CASSANDRA_YAML;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;

import org.apache.cassandra.service.EmbeddedCassandraService;
import org.hawkular.commons.cassandra.CassandraYaml;
import org.hawkular.commons.cassandra.CassandraYaml.CassandraYamlKey;
import org.hawkular.inventory.api.test.AbstractBaseInventoryTestsuite;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.impl.tinkerpop.TinkerpopInventory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.tinkerpop.blueprints.Element;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public class TitanTest extends AbstractBaseInventoryTestsuite<Element> {

    private static EmbeddedCassandraService service;
    private static final int cassandraPortOffset = 0;
    private static final int cassandraPort;

    static {
        cassandraPort = cassandraPortOffset + ((Integer) CassandraYamlKey.native_transport_port.getDefaultValue());
    }
    private static TinkerpopInventory INVENTORY;

    @Rule public TestName name = new TestName();

    @BeforeClass
    public static void startCassandra() throws Exception {
        if (service == null) {
            File baseDir = Files.createTempDirectory(TitanTest.class.getName() + ".cassandra").toFile();
            File cassandraYaml = new File(baseDir, "cassandra.yaml");

            URL defaultCassandraYamlUrl = CassandraYaml.class.getResource("/" + CASSANDRA_YAML);
            CassandraYaml.builder()
                    .load(defaultCassandraYamlUrl)//
                    .baseDir(baseDir)//
                    .clusterName("hawkular-accounts-api-cassandra")//
                    .portOffset(cassandraPortOffset)//
                    .store(cassandraYaml)//
                    .mkdirs()//
                    .setCassandraConfigProp()//
                    .setTriggersDirProp();

            service = new EmbeddedCassandraService();
            service.start();

        }

        INVENTORY = new TinkerpopInventory();
        setupNewInventory(INVENTORY);
    }

//    @AfterClass
//    public static void stopCassandra() throws Exception {
//        if (CASSANDRA_DAEMON != null) {
//            CASSANDRA_DAEMON.deactivate();
//        }
//    }

    @Before
    public void reportStart() {
        //System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> " + name.getMethodName());
    }

    @After
    public void reportEnd() {
        //System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<< " + name.getMethodName());
    }

    @Override protected BaseInventory<Element> getInventoryForTest() {
        return INVENTORY;
    }
}
