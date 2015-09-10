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

package org.hawkular.inventory.api.feeds;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.FeedAlreadyRegisteredException;
import org.hawkular.inventory.api.FeedIdStrategy;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.Feed;

/**
 * An ID strategy that by default accepts the proposed ID supplied by the caller.
 *
 * <p>If a feed with the proposed ID already exists in the given tenant and environment, an optional fallback strategy
 * is used to generate a new ID. If the fallback strategy is not defined, an exception is thrown.
 *
 * This strategy is useful for Hawkular deployments where some of the feeds are incapable of remembering their assigned
 * IDs and need to be pre-configured with one.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class AcceptWithFallbackFeedIdStrategy implements FeedIdStrategy {

    private final FeedIdStrategy fallback;

    public AcceptWithFallbackFeedIdStrategy() {
        this(null);
    }

    public AcceptWithFallbackFeedIdStrategy(FeedIdStrategy fallback) {
        this.fallback = fallback;
    }

    @Override
    public String generate(Inventory inventory, Feed proposedFeed) throws EntityAlreadyExistsException {
        try {
            if (!Feed.Blueprint.shouldAutogenerateId(proposedFeed)) {
                inventory.inspect(proposedFeed).entity();
            }

            if (fallback == null || !Feed.Blueprint.shouldAutogenerateId(proposedFeed)) {
                throw new FeedAlreadyRegisteredException(proposedFeed);
            } else {
                return fallback.generate(inventory, proposedFeed);
            }
        } catch (EntityNotFoundException e) {
            // the happy path
            return proposedFeed.getId();
        }
    }
}
