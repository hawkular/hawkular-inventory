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
package org.hawkular.inventory.rest.interceptors;

import java.util.Collections;
import java.util.List;

import org.hawkular.inventory.api.Configuration;

/**
 * @author Joel Takvorian
 */
enum CorsProperties implements Configuration.Property {
    ALLOWED_CORS_ORIGINS("hawkular.allowed-cors-origins",
            Collections.singletonList("hawkular.allowed-cors-origins"),
            Collections.singletonList("ALLOWED_CORS_ORIGINS")),
    ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS("hawkular.allowed-cors-access-control-allow-headers",
            Collections.singletonList("hawkular.allowed-cors-access-control-allow-headers"),
            Collections.singletonList("ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS"));

    final String propertyName;
    final List<String> systemPropertyNames;
    final List<String> environmentVariableNames;

    CorsProperties(String propertyName, List<String> systemPropertyNames, List<String> environmentVariableNames) {
        this.propertyName = propertyName;
        this.systemPropertyNames = systemPropertyNames;
        this.environmentVariableNames = environmentVariableNames;
    }

    @Override public String getPropertyName() {
        return propertyName;
    }

    @Override public List<String> getSystemPropertyNames() {
        return systemPropertyNames;
    }

    @Override public List<String> getEnvironmentVariableNames() {
        return environmentVariableNames;
    }
}