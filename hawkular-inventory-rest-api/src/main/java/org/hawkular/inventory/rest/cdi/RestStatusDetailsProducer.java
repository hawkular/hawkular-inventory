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
package org.hawkular.inventory.rest.cdi;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.hawkular.commons.rest.status.RestStatusInfo;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.cdi.InventoryConfigurationData;
import org.hawkular.inventory.cdi.OfficialInventoryProducer;

/**
 * @author Lukas Krejci
 * @since 0.9.0
 */
@RequestScoped
public class RestStatusDetailsProducer {

    @Inject
    @AutoTenant
    private Instance<Inventory> inventory;

    @Inject
    private Instance<InventoryConfigurationData> configData;

    @Produces
    @RestStatusInfo
    public Map<String, String> getRestStatusDetails() throws IOException {
        Map<String, String> ret = new HashMap<>();
        ret.put("Initialized", Boolean.toString(!inventory.isUnsatisfied()));

        if (!configData.isUnsatisfied()) {
            Properties props = new Properties();

            try (Reader conf = configData.get().open()) {
                props.load(conf);

                Configuration config = Configuration.builder().withConfiguration(props).build();

                String implClass = config.getProperty(OfficialInventoryProducer.IMPL_PROPERTY, null);
                if (implClass == null) {
                    implClass = ServiceLoader.load(Inventory.class).iterator().next().getClass().getName();
                }
                ret.put("Inventory-Implementation", implClass);
            }
        }

        return ret;
    }
}
