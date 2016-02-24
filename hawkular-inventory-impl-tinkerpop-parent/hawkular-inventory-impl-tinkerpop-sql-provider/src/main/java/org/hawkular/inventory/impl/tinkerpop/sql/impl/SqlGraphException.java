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
package org.hawkular.inventory.impl.tinkerpop.sql.impl;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
public class SqlGraphException extends RuntimeException {

    public SqlGraphException() {
    }

    public SqlGraphException(String message) {
        super(message);
    }

    public SqlGraphException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqlGraphException(Throwable cause) {
        super(cause);
    }

    public SqlGraphException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
