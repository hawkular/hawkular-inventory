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
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class InventoryException extends RuntimeException {

    public InventoryException() {
    }

    public InventoryException(Throwable cause) {
        super(cause);
    }

    public InventoryException(String message) {
        super(message);
    }

    public InventoryException(String message, Throwable cause) {
        super(message, cause);
    }

    protected InventoryException(String message, Throwable cause, boolean enableSuppression,
                                 boolean writableStackTrace) {

        super(message, cause, enableSuppression, writableStackTrace);
    }
}
