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

import java.time.Instant;
import java.util.List;

import org.hawkular.inventory.api.model.Change;
import org.hawkular.inventory.api.model.Entity;

/**
 * @author Lukas Krejci
 * @since 0.20.0
 */
public interface Versioned<E extends Entity<?, ?>> {
    /**
     * The list of the changes is sorted in the ascending order by the time of the change.
     *
     * @param from the date from which to retrieve history or null for not limiting the age of the changes
     * @param to the date to which to retrieve history or null for not limiting the age of the changes
     * @return the list of changes made to the entity so far.
     */
    List<Change<E>> history(Instant from, Instant to);

    default List<Change<E>> history() {
        return history(Instant.ofEpochMilli(0), Instant.ofEpochMilli(Long.MAX_VALUE));
    }
}
