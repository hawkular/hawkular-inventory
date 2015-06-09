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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.lazy.spi.LazyInventoryBackend;

/**
 * An implementation of the {@link Inventory} that converts the API traversals into trees of filters that it then passes
 * for evaluation to a {@link LazyInventoryBackend backend}.
 *
 * <p>This class is meant to be inherited by the implementation that should provide the initialization and cleanup
 * logic.
 *
 * @author Lukas Krejci
 * @since 0.0.6
 */
public abstract class LazyInventory<E> implements Inventory {

    private final LazyInventoryBackend<E> backend;

    protected LazyInventory(LazyInventoryBackend<E> backend) {
        this.backend = backend;
    }

    @Override
    public Tenants.ReadWrite tenants() {
        //TODO implement
        return null;
    }

}
