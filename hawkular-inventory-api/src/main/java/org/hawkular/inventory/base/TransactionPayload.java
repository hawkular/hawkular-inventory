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

/**
 * Represents a payload to be run within a context of a transaction. The payload might or might not commit the
 * transaction.
 *
 * @author Lukas Krejci
 * @since 0.4.0
 */
@FunctionalInterface
public interface TransactionPayload<R, BE> {
    R run(Transaction<BE> tx) throws Exception;

    @FunctionalInterface
    interface Committing<R, BE> extends TransactionPayload<R, BE> {
        static <R, BE> Committing<R, BE> committing(TransactionPayload<R, BE> payload) {
            return tx -> {
                R ret = payload.run(tx);
                tx.commit();
                return ret;
            };
        }

        default R run(Transaction<BE> tx) throws Exception {
            return run(Transaction.Committable.from(tx));
        }

        R run(Transaction.Committable<BE> tx) throws Exception;
    }
}
