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
import org.hawkular.inventory.base.spi.Transaction;

/**
 * An inventory backend where all operations on transactions are no-ops. All other methods are delegated to an
 * underlying backend.
 *
 * @author Lukas Krejci
 * @since 0.13.0
 */
public class TransactionIgnoringBackend<E> extends DelegatingInventoryBackend<E> {
    public TransactionIgnoringBackend(InventoryBackend<E> backend) {
        super(backend);
    }

    @Override public void commit(Transaction<E> transaction) throws CommitFailureException {
    }

    @Override public void rollback(Transaction<E> transaction) {
    }

    @Override public void startTransaction(Transaction<E> transaction) {
    }
}
