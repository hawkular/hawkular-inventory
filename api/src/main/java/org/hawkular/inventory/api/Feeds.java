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

import org.hawkular.inventory.api.model.AbstractPath;
import org.hawkular.inventory.api.model.Feed;

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
            BrowserBase<Resources.ReadContained, Metrics.ReadContained> {}

    public interface ReadContained extends ReadInterface<Single, Multiple, String> {}

    public interface Read extends ReadInterface<Single, Multiple, AbstractPath<?>> {}

    public interface ReadWrite extends ReadWriteInterface<Feed.Update, Feed.Blueprint, Single, Multiple> {

        /**
         * Registers a new feed.
         * The id in the blueprint is merely a suggestion and does not need to be honored by the server. The caller is
         * advised to use the returned access interface to check what the actual ID was assigned to the feed.
         *
         * @param blueprint the blueprint of the feed
         * @return the access interface to the newly created feed
         */
        Single create(Feed.Blueprint blueprint);
    }
}
