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
package org.hawkular.inventory.cdi;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

/**
 * This class is to be produced by some CDI producer. It is used to encapsulate the configuration file data.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class InventoryConfigurationData {

    private final URL location;
    private final Map<String, String> definedProperties;

    public InventoryConfigurationData(URL location, Map<String, String> definedProperties) {
        this.location = location;
        this.definedProperties = definedProperties;
    }

    public Reader open() throws IOException {
        return new TokenReplacingReader(new InputStreamReader(location.openStream(), "UTF-8"), definedProperties);
    }
}
