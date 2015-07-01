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

import org.hawkular.inventory.api.InventoryException;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public class TransactionFailureException extends InventoryException {
    private final int attemptsMade;

    public TransactionFailureException(int attemptsMade) {
        this.attemptsMade = attemptsMade;
    }

    public TransactionFailureException(Throwable cause, int attemptsMade) {
        super(cause);
        this.attemptsMade = attemptsMade;
    }

    @Override
    public String getMessage() {
        return "Failed to commit a transaction in " + attemptsMade + " attempts." + (getCause() == null ? "" :
                "The last error message was: " + getCause().getMessage());
    }
}
