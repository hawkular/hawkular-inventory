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

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Entity;

import static org.hawkular.inventory.api.Relationships.Direction.outgoing;

/**
 * Base for Multiple implementations on entities.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
class MultipleEntityFetcher<BE, E extends Entity<?, ?>> extends Fetcher<BE, E> {
    public MultipleEntityFetcher(TraversalContext<BE, E> context) {
        super(context);
    }

    public Relationships.Read relationships() {
        return relationships(outgoing);
    }

    public Relationships.Read relationships(Relationships.Direction direction) {
        return new BaseRelationships.Read<>(context.proceedToRelationships(direction).get()
        );
    }
}
