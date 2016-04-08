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
package org.hawkular.inventory.api.test;

import static java.util.Collections.emptyList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.hawkular.inventory.api.Configuration;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
public class ConfigurationLoadingTest {

    @Test
    public void testLoadFromMap() throws Exception {
        HashMap<String, String> props = new HashMap<>();
        props.put("a", "a");
        props.put("b", "b");

        Configuration config = Configuration.builder().withConfiguration(props).build();

        Assert.assertEquals(2, config.getImplementationConfiguration(emptyList()).size());
        Assert.assertEquals("a", config.getImplementationConfiguration(emptyList()).get("a"));
        Assert.assertEquals("b", config.getImplementationConfiguration(emptyList()).get("b"));
    }

    @Test
    public void testLoadFromProperties() throws Exception {
        Properties props = new Properties();
        props.put("a", "a");
        props.put("b", "b");

        Configuration config = Configuration.builder().withConfiguration(props).build();

        Assert.assertEquals(2, config.getImplementationConfiguration(emptyList()).size());
        Assert.assertEquals("a", config.getImplementationConfiguration(emptyList()).get("a"));
        Assert.assertEquals("b", config.getImplementationConfiguration(emptyList()).get("b"));
    }

    @Test
    public void testGetPropertyFromSysProps() throws Exception {
        System.setProperty("a", "SYS_PROP");
        Properties props = new Properties();
        props.put("a", "a");
        props.put("b", "b");

        Configuration config = Configuration.builder().withConfiguration(props).build();

        String val = config.getProperty(Configuration.Property.builder().withPropertyNameAndSystemProperty("a")
                .build(), null);

        Assert.assertEquals("SYS_PROP", val);
        Assert.assertEquals("b", config.getProperty(Configuration.Property.builder().withPropertyName("b").build(),
                null));
    }

    @Test
    public void testGetPropertyFromEnvironment() throws Exception {
        Configuration config = Configuration.builder().addConfigurationProperty("HOME", "asdf").build();

        Assert.assertNotEquals("asdf", config.getProperty(Configuration.Property.builder().withPropertyName("home")
                .withEnvironmentVariables("HOME").build(), null));
    }

    @Test
    public void testLoadImplementationConfigContainsAllInfo() throws Exception {
        Configuration empty = Configuration.builder().build();

        Map<String, String> config = empty.getImplementationConfiguration(Arrays.asList(
                Configuration.Property.builder().withPropertyName("javaHome").withSystemProperties("java.home").build(),
                Configuration.Property.builder().withPropertyName("home").withEnvironmentVariables("HOME").build()
        ));

        Assert.assertEquals(2, config.size());
    }
}
