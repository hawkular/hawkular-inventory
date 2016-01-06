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

import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * @author Lukas Krejci
 * @since 0.10.0
 */
final class TransactionFixedBackend<E> extends DelegatingInventoryBackend<E> {

    private final Transaction transaction;

    public TransactionFixedBackend(InventoryBackend<E> backend, Transaction transaction) {
        super(backend);
        this.transaction = transaction;
    }

    @Override
    public Transaction startTransaction(boolean mutating) {
        return transaction;
    }

    @Override
    public void commit(Transaction transaction) throws CommitFailureException {
    }

    @Override
    public void rollback(Transaction transaction) {
    }
}
