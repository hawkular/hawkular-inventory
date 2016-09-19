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

import org.apache.tinkerpop.gremlin.structure.Element;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.impl.tinkerpop.TinkerpopInventory;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author Lukas Krejci
 * @since 0.15.1
 */
public class SmallTransactionsTinkerGraphTest extends AbstractTinkerGraphTest {
    private static TinkerpopInventory INVENTORY;

    @BeforeClass
    public static void setup() throws Exception {
        System.setProperty("TinkerGraphProvider.prefersBigTxs", "false");
        String configPath = System.getProperty("small-tx.config");
        System.setProperty("graph.config", configPath);
        INVENTORY = new TinkerpopInventory();
        setupNewInventory(INVENTORY);
        setupData(INVENTORY);
    }

    @AfterClass
    public static void teardownData() throws Exception {
        teardownData(INVENTORY);
        teardown(INVENTORY);
    }

    @Override
    protected BaseInventory<Element> getInventoryForTest() {
        return INVENTORY;
    }
}
