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
package org.hawkular.inventory.base;

import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * A transaction constructor can instantiate a new transaction implementation with some behavior. This is then used
 * in {@link TraversalContext} to create new transactions upon request of the base inventory impl.
 *
 * @author Lukas Krejci
 * @since 0.13.0
 */
public interface TransactionConstructor<BE> {

    /**
     * Returns a transaction constructor that both instantiates a new transaction object and starts the transaction
     * in the backend.
     *
     * <p>This should be used in majority of cases.
     *
     * @param <BE> the type of the backend representation of entities
     * @return a transaction constructor that initializes the transaction also in the backend
     */
    static <BE> TransactionConstructor<BE> startInBackend() {
        return (backend, preCommit) -> new BackendTransaction<>(backend.startTransaction(), preCommit);
    }

    Transaction<BE> construct(InventoryBackend<BE> backend, Transaction.PreCommit<BE> preCommit);
}
