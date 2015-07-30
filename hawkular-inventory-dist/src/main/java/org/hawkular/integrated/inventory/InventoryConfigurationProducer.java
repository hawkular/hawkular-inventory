/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.integrated.inventory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.hawkular.inventory.cdi.InventoryConfigurationData;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
@Singleton
public class InventoryConfigurationProducer {

    public static final String EXTERNAL_CONF_FILE_PROPERTY_NAME = "hawkular-inventory.conf";

    @Produces
    public InventoryConfigurationData getConfigurationData() throws IOException {
        Map<String, String> props = new HashMap<>();

        for (String prop : System.getProperties().stringPropertyNames()) {
            props.put(prop, System.getProperties().getProperty(prop));
        }

        return new InventoryConfigurationData(getConfigurationFile(), props);
    }

    private URL getConfigurationFile() throws IOException {
        String confFileName = System.getProperty(EXTERNAL_CONF_FILE_PROPERTY_NAME);

        File confFile;

        if (confFileName == null) {
            confFile = new File(System.getProperty("user.home"), "." + EXTERNAL_CONF_FILE_PROPERTY_NAME);
            if (!confFile.exists()) {
                confFile = null;
            }
        } else {
            confFile = new File(confFileName);
        }

        return confFile == null ? getClass().getClassLoader().getResource("hawkular-inventory.properties")
                : confFile.toURI().toURL();
    }
}
