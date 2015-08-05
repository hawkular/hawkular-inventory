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

import java.util.UUID;

import org.hawkular.inventory.api.FeedIdStrategy;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.Feed;

/**
 * A simple ID strategy that will return a random UUID. This is useful for feeds that can remember their assigned IDs
 * and don't have to be pre-configured by the (human) deployer.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class RandomUUIDFeedIdStrategy implements FeedIdStrategy {
    @Override
    @SuppressWarnings("UnusedParameters")
    public String generate(Inventory inventory, Feed proposedFeed) {
        return UUID.randomUUID().toString();
    }
}
