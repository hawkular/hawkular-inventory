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

import java.io.UnsupportedEncodingException;
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

    @Produces
    public InventoryConfigurationData getConfigurationFileName() throws UnsupportedEncodingException {
        //TODO this should probably be read from somewhere else than from a config file embedded in this jar.
        //that file should be more of a default than a real means of configuration

        Map<String, String> props = new HashMap<>();
        System.getProperties().stringPropertyNames().forEach((k) -> props.put(k, System.getProperty(k)));

        return new InventoryConfigurationData(getClass().getClassLoader().getResource("hawkular-inventory.properties"),
                props);
    }
}
