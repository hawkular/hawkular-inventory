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

import org.hawkular.inventory.api.model.Entity;

/**
 * Helper interface that melds {@link ReadInterface} and {@link WriteInterface}.
 *
 * <p>The write interface implies the use of {@link org.hawkular.inventory.api.Relationships.WellKnown#contains}
 * relationship between the "source" entity and the newly created one (which is possible through the methods in the
 * write interface). Because the "siblings" in the contains relationship must have mutually different IDs, it is
 * enough in this case to address them by merely string ids (as opposed to full canonical path).
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
interface ReadWriteInterface<Update, Blueprint extends Entity.Blueprint, Single, Multiple>
        extends ReadInterface<Single, Multiple, String>, WriteInterface<Update, Blueprint, Single> {
}
