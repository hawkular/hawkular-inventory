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
package org.hawkular.inventory.api;

/**
 * Using a transaction frame one can perform multiple inventory commands within a single transaction that can then be
 * committed or rolled back.
 *
 * @author Lukas Krejci
 * @since 0.4.0
 */
public interface TransactionFrame {

    /**
     * Commits this transaction frame. Any other action on the bound inventory performed after commit has been
     * called will start a new transaction that can then later be committed again.
     *
     * @throws CommitException
     */
    void commit() throws CommitException;

    /**
     * Rolls back the current transaction. As with {@link #commit()} any other action on the bound inventory will start
     * a new transaction.
     */
    void rollback();

    /**
     * Generally speaking, users should NOT hold the return value of this method is some long-lived variable, because
     * the transaction semantics of the returned inventory is governed by the transaction frame. I.e. without the frame
     * any changes made through the returned inventory will not have a lasting effect because they will never be
     * committed.
     *
     * @return inventory that relies on this transaction frame with the transaction handling
     */
    Inventory boundInventory();

    class CommitException extends InventoryException {
        public CommitException() {
        }

        public CommitException(Throwable cause) {
            super(cause);
        }

        public CommitException(String message) {
            super(message);
        }

        public CommitException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
