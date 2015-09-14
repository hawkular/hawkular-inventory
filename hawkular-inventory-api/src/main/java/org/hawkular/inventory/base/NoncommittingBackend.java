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
package org.hawkular.inventory.base;

import java.util.ArrayList;
import java.util.List;

import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * Helper to implement transaction framing. The {@link #startTransaction(boolean)}, {@link #commit(Transaction)} and
 * {@link #rollback(Transaction)} are a no-ops.
 *
 * @author Lukas Krejci
 * @since 0.4.0
 */
final class NoncommittingBackend<E> extends DelegatingInventoryBackend<E> {

    private final PayloadRememberingTransaction transaction;

    public NoncommittingBackend(InventoryBackend<E> backend, boolean mutating) {
        super(backend);
        this.transaction = new PayloadRememberingTransaction(mutating);
    }

    @Override
    public Transaction startTransaction(boolean mutating) {
        return transaction;
    }

    @Override
    public void rollback(Transaction transaction) {
    }

    @Override
    public void commit(Transaction transaction) throws CommitFailureException {
    }

    public List<PotentiallyCommittingPayload<?>> getRecordedPayloads() {
        return transaction.payloads;
    }

    public void clearRecordedPayloads() {
        transaction.payloads.clear();
    }

    private static class PayloadRememberingTransaction extends InventoryBackend.Transaction {

        private final ArrayList<PotentiallyCommittingPayload<?>> payloads = new ArrayList<>();

        public PayloadRememberingTransaction(boolean mutating) {
            super(mutating);
        }

        @Override
        public <R> R execute(PotentiallyCommittingPayload<R> payload) throws CommitFailureException {
            payloads.add(payload);
            return super.execute(payload);
        }
    }
}
