package org.hawkular.inventory.api.test;

import static java.util.Collections.emptyList;

import java.util.Arrays;
import java.util.Collections;
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
