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

import org.hawkular.inventory.api.model.Feed;

import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Feeds {

    private Feeds() {

    }

    private interface BrowserBase<Resources, Metrics> {
        Resources resources();

        Metrics metrics();
    }

    public interface Single extends ResolvableToSingleWithRelationships<Feed>,
            BrowserBase<Resources.ReadWrite, Metrics.ReadWrite> {}

    public interface Multiple extends ResolvableToManyWithRelationships<Feed>,
            BrowserBase<Resources.Read, Metrics.Read> {}

    public interface Read extends ReadInterface<Single, Multiple> {}

    public interface ReadUpdateRegister extends ReadInterface<Single, Multiple> {
        /**
         * Registers a new feed.
         * The proposed ID is merely a suggestion and does not need to be honored by the server. The caller is advised
         * to use the returned access interface to check what the actual ID was assigned to the feed.
         *
         * @param proposedId the ID the feed would like to have
         * @param properties the properties of the feed (or null if none needed)
         * @return the access interface to the newly created feed
         */
        Single register(String proposedId, Map<String, Object> properties);

        /**
         * Updates the feed.
         *
         * @param id the id of the feed
         * @param update the update data
         *
         * @throws EntityNotFoundException if the entity is not found in the database
         * @throws java.lang.IllegalArgumentException if the supplied entity could not be updated for some reason
         */
        void update(String id, Feed.Update update) throws EntityNotFoundException, IllegalArgumentException;
    }
}
