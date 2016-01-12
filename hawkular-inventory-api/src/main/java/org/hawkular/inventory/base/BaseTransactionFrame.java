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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.TransactionFrame;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.base.spi.Transaction;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
final class BaseTransactionFrame<BE> implements TransactionFrame {
    private final InventoryBackend<BE> origBackend;
    private Transaction<BE> transaction;
    private Inventory boundInventory;
    private final int maxRetries;
    private final TraversalContext<BE, ?> traversalContext;

    private final List<PotentiallyCommittingPayload<?, BE>> payloads = new ArrayList<>();
    private final List<EntityAndPendingNotifications<BE, ?>> notifications = new ArrayList<>();

    BaseTransactionFrame(InventoryBackend<BE> origBackend, ObservableContext observableContext,
                         TraversalContext<BE, ?> traversalContext) {
        this.origBackend = origBackend;
        this.maxRetries = traversalContext.getTransactionRetriesCount();
        boundInventory = new BaseInventory.Initialized<>(new RecordingBackend(origBackend),
                observableContext, traversalContext.configuration, new PayloadRecordingTransactionConstructor());

        this.traversalContext = traversalContext;

        reset();
    }

    @Override
    public void commit() throws CommitException {
        //this will just commit the transaction on the backend without running any payload again (they already ran).
        //in case of failure, we re-run all the payloads.
        Util.commitOrRetry(transaction, origBackend, (t) -> {
            //this is finishing up the payloads that already ran - we're actually done here, because the notifications
            //have been collected and pre-commit actions ran.

            origBackend.commit(t);

            return null;
        }, (t) -> {
            //the transaction failed.
            //we will be re-running all the payloads that we've recorded, so we need to reset the pre-commit actions and
            //notifications so that the payloads can re-declare them again...
            t.getPreCommit().reset();
            NotificationsRememberingPreCommit preCommit = new NotificationsRememberingPreCommit(t.getPreCommit());

            //we need to fake a new transaction that will not commit anything to backend - we do it ourselves
            Transaction<BE> tx = TransactionConstructor.<BE>ignoreBackend().construct(traversalContext,
                    transaction.isMutating(), preCommit);

            //k, stuff's set up... let's run the payloads so that notifications are re-declared and we can commit
            for (PotentiallyCommittingPayload<?, BE> p : payloads) {
                tx.execute(p);
            }

            origBackend.commit(tx);

            //gather the notifications that were collected in the payloads
            notifications.clear();
            notifications.addAll(preCommit.getRealFinalNotifications());

            return null;
        }, maxRetries);

        //at last, everything is committed.. emit the recorded notifications
        notifications.forEach(traversalContext::notifyAll);
    }

    @Override
    public void rollback() {
        origBackend.rollback(transaction);
        reset();
    }

    @Override
    public Inventory boundInventory() {
        return boundInventory;
    }

    private void reset() {
        transaction = traversalContext.startTransaction(true);
        notifications.clear();
    }

    private class PayloadRecordingTransactionConstructor implements TransactionConstructor<BE> {

        @Override public Transaction<BE> construct(TraversalContext<BE, ?> traversalContext, boolean mutating,
                                                   Transaction.PreCommit<BE> preCommit) {
            //for reasons not entirely clear to me, instanceof gives compilation error
            if (!NotificationsRememberingPreCommit.class.isAssignableFrom(preCommit.getClass())) {
                preCommit = new NotificationsRememberingPreCommit(preCommit);
            }
            return new PayloadRecordingTransaction(mutating, preCommit);
        }
    }

    private class PayloadRecordingTransaction extends BaseTransaction<BE> {
        PayloadRecordingTransaction(boolean mutating) {
            super(mutating);
        }

        PayloadRecordingTransaction(boolean mutating, PreCommit<BE> preCommit) {
            super(mutating, preCommit);
        }

        @Override public <R> R execute(PotentiallyCommittingPayload<R, BE> payload) throws CommitFailureException {
            payloads.add(payload);
            return super.execute(payload);
        }
    }

    private class NotificationsRememberingPreCommit implements Transaction.PreCommit<BE> {
        private final Transaction.PreCommit<BE> wrapped;

        private NotificationsRememberingPreCommit(Transaction.PreCommit<BE> wrapped) {
            this.wrapped = wrapped;
        }

        @Override public void addNotifications(EntityAndPendingNotifications<BE, ?> element) {
            wrapped.addNotifications(element);
        }

        @Override public void addAction(Consumer<Transaction<BE>> action) {
            wrapped.addAction(action);
        }

        @Override public List<Consumer<Transaction<BE>>> getActions() {
            return wrapped.getActions();
        }

        @Override public List<EntityAndPendingNotifications<BE, ?>> getFinalNotifications() {
            return Collections.emptyList();
        }

        @Override public void reset() {
            wrapped.reset();
        }

        List<EntityAndPendingNotifications<BE, ?>> getRealFinalNotifications() {
            return wrapped.getFinalNotifications();
        }
    }

    private class RecordingBackend extends TransactionIgnoringBackend<BE> {

        public RecordingBackend(InventoryBackend<BE> backend) {
            super(backend);
        }

        @Override public void commit(Transaction<BE> transaction) throws CommitFailureException {
            super.commit(transaction);
            //be sure to do this AFTER the "real" commit, so that failed commits are not recorded
            notifications.addAll(transaction.getPreCommit().getFinalNotifications());
        }
    }
}
