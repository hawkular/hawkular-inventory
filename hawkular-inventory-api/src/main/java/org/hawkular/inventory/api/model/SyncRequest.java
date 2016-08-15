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
package org.hawkular.inventory.api.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Lukas Krejci
 * @since 0.18.0
 */
public final class SyncRequest<B extends Entity.Blueprint> implements Serializable {

    private final SyncConfiguration configuration;

    @JsonProperty("structure")
    private final InventoryStructure<B> inventoryStructure;

    public static <B extends Entity.Blueprint> SyncRequest<B> syncEverything(InventoryStructure<B> structure) {
        return new SyncRequest<>(SyncConfiguration.builder().withAllTypes().build(), structure);
    }

    @JsonCreator
    public SyncRequest(@JsonProperty("configuration") SyncConfiguration configuration,
                       @JsonProperty("structure") InventoryStructure<B> inventoryStructure) {
        this.configuration = configuration;
        this.inventoryStructure = inventoryStructure;
    }

    public SyncConfiguration getConfiguration() {
        if (configuration == null) {
            return SyncConfiguration.DEFAULT;
        }
        return configuration;
    }

    public InventoryStructure<B> getInventoryStructure() {
        return inventoryStructure;
    }
}
