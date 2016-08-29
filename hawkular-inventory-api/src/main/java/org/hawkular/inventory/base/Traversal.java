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

import java.util.List;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.ResultFilter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.paths.SegmentType;

/**
 * A base class for all the inventory traversal interfaces. Contains only a minimal set of helper methods and holds the
 * traversal context.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public abstract class Traversal<BE, E extends AbstractElement<?, ?>> {

    protected final TraversalContext<BE, E> context;

    protected Traversal(TraversalContext<BE, E> context) {
        this.context = context;
    }

    /**
     * If the inventory configuration provided a {@link ResultFilter}, this calls it to tell whether provided element
     * is applicable. If the result filter is not provided by the configuration, true will always be returned.
     *
     * @param result the potential result to be checked for applicability in the result set
     * @return true or false (!!!)
     */
    protected boolean isApplicable(AbstractElement<?, ?> result) {
        ResultFilter filter = context.configuration.getResultFilter();
        return filter == null || filter.isApplicable(result);
    }

    /**
     * A helper method to retrieve a single result from the query or throw an exception if the query yields no results.
     *
     * @param query      the query to run
     * @param entityType the expected type of the entity (used only for error reporting)
     * @return the single result
     * @throws EntityNotFoundException if the query doesn't return any results
     */
    protected BE getSingle(Query query, SegmentType entityType) {
        return inTx(tx -> Util.getSingle(context.discriminator(), tx, query, entityType));
    }

    /**
     * Runs the payload in transaction. It is the payload's responsibility to commit the transaction at some point
     * during its execution. If the payload throws an exception the transaction is automatically rolled back and
     * the exception rethrown.
     * <p>
     * <p><b>WARNING:</b> the payload might be called multiple times if the transaction it runs within fails. It is
     * therefore dangerous to keep any mutable state outside of the payload function that the function depends on.
     *
     * @param payload the payload to execute in transaction
     * @param <R>     the return type
     * @return the return value provided by the payload
     */
    protected <R> R inTx(TransactionPayload<R, BE> payload) {
        return inTx(context, payload);
    }

    /**
     * Identical to {@link #inTx(TransactionPayload)} but also returns the notifications emitted from the transaction.
     * The list of notifications is final and they have already been sent. The caller should NOT send them again.
     *
     * @param payload the payload to run within a transaction
     * @param <R> the type of the result returned from the payload
     * @return the result of the payload together with the notifications sent as a result of the transaction
     */
    protected <R> ResultWithNofifications<R, BE> inTxWithNotifications(TransactionPayload<R, BE> payload) {
        return inCommittableTxWithNotifications(context, TransactionPayload.Committing.committing(payload));
    }

    protected static <R, BE, E extends AbstractElement<?, ?>>
    R inTx(TraversalContext<BE, E> context, TransactionPayload<R, BE> payload) {
        return inCommittableTx(context, TransactionPayload.Committing.committing(payload));
    }

    protected <R> R inCommittableTx(TransactionPayload.Committing<R, BE> payload) {
        return inCommittableTx(context, payload);
    }

    protected static <R, BE, E extends AbstractElement<?, ?>>
    R inCommittableTx(TraversalContext<BE, E> context, TransactionPayload.Committing<R, BE> payload) {
        return Util.inCommittableTx(context, tx -> {
            R v = payload.run(tx);

            //k, now the transaction finished, we can send out the notifications
            tx.getPreCommit().getFinalNotifications().forEach(context::notifyAll);

            return v;
        });
    }

    protected static <R, BE, E extends AbstractElement<?, ?>>
    ResultWithNofifications<R, BE> inCommittableTxWithNotifications(TraversalContext<BE, E> context,
                                                                       TransactionPayload.Committing<R, BE> payload) {
        return Util.inCommittableTx(context, tx -> {
            R v = payload.run(tx);

            List<EntityAndPendingNotifications<BE, ?>> notifs = tx.getPreCommit().getFinalNotifications();

            //k, now the transaction finished, we can send out the notifications
            notifs.forEach(context::notifyAll);

            return new ResultWithNofifications<>(v, notifs);
        });
    }

    protected static final class ResultWithNofifications<R, BE> {
        private final R result;
        private final List<EntityAndPendingNotifications<BE, ?>> sentNotifications;


        private ResultWithNofifications(R result, List<EntityAndPendingNotifications<BE, ?>> sentNotifications) {
            this.result = result;
            this.sentNotifications = sentNotifications;
        }

        public R getResult() {
            return result;
        }

        public List<EntityAndPendingNotifications<BE, ?>> getSentNotifications() {
            return sentNotifications;
        }
    }
}
