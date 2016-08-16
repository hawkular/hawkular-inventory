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

import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.SyncHash;
import org.hawkular.inventory.api.model.SyncRequest;

/**
 * These interfaces are extended by accessor interfaces for {@link org.hawkular.inventory.api.model.IdentityHashable}
 * model entities.
 *
 * @author Lukas Krejci
 * @since 0.15.0
 */
public final class Synced {

    private Synced() {

    }

    public interface SingleWithRelationships<E extends Entity<B, U>, B extends Entity.Blueprint,
            U extends Entity.Update> extends Single<E, B, U>, ResolvableToSingleWithRelationships<E, U> {
    }

    public interface Single<E extends Entity<B, U>, B extends Entity.Blueprint,
            U extends Entity.Update> extends ResolvableToSingle<E, U> {

        /**
         * This is useful for figuring out what changed about the structure of the entity
         * @return the hash of the entity together with the hashes of all contained entities
         */
        SyncHash.Tree treeHash();

        /**
         * Synchronizes the entity and any of its children. By default the structure is considered to be complete - i.e.
         * any contained entity currently present in inventory that is not present in the supplied structure will be
         * deleted from the inventory.
         *
         * <p>For partial synchronizations please consult the {@link org.hawkular.inventory.api.model.SyncConfiguration}
         * class and its properties.
         *
         * @param syncRequest the synchronization request with configuration and actual data.
         */
        void synchronize(SyncRequest<B> syncRequest);
    }
}
