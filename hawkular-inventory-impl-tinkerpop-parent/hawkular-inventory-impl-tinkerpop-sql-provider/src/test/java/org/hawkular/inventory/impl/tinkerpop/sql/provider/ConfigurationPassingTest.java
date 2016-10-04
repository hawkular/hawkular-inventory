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
package org.hawkular.inventory.impl.tinkerpop.sql.provider;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.impl.tinkerpop.sql.SqlGraphProvider;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * @author Lukas Krejci
 * @since 0.20.0
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Configuration.class)
@PowerMockIgnore("javax.management.*")
public class ConfigurationPassingTest {

    @Test
    public void testConfigurationPassing() throws Exception {
        PowerMockito.mockStatic(System.class);
        Mockito.when(System.getenv("HAWKULAR_INVENTORY_SQL_JDBC_URL")).thenReturn("kachny");

        try {
            new SqlGraphProvider()
                    .instantiateGraph(Configuration.builder().addConfigurationProperty("sql.jdbc.url", "voe").build());
            Assert.fail("Should not be possible to instantiate graph with invalid jdbc url");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause().getMessage().contains("kachny"));
        }
    }
}
