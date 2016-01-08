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
package org.hawkular.inventory.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hawkular.inventory.paths.CanonicalPath;

/**
 * @author Lukas Krejci
 * @since 0.3.0
 */
public class ValidationException extends InventoryException {

    private final List<ValidationMessage> messages;
    private final CanonicalPath dataPath;

    public ValidationException(CanonicalPath dataPath, Iterable<ValidationMessage> messages, Throwable cause) {
        super(cause);
        this.dataPath = dataPath;

        ArrayList<ValidationMessage> tmp = new ArrayList<>();

        messages.forEach(tmp::add);

        this.messages = Collections.unmodifiableList(tmp);
    }

    public CanonicalPath getDataPath() {
        return dataPath;
    }

    public List<ValidationMessage> getMessages() {
        return messages;
    }

    @Override
    public String getMessage() {
        StringBuilder bld = new StringBuilder("Validation of data entity at '").append(dataPath);

        if (messages.isEmpty()) {
            bld.append("' failed without any explicitly mentioned problems.");
        } else {
            bld.append("' failed with the following problems found:\n");
            messages.forEach((m) -> bld.append(m.getSeverity()).append(": ").append(m.getMessage()).append("\n"));
        }

        return bld.toString();
    }

    public static final class ValidationMessage {
        private final String severity;
        private final String message;

        public ValidationMessage(String severity, String message) {
            this.message = message;
            this.severity = severity;
        }

        public String getMessage() {
            return message;
        }

        public String getSeverity() {
            return severity;
        }
    }
}
