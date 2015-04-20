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

/**
 * A {@code FeedIdStrategy} is a service that can supply unique IDs for feeds registering with the inventory.
 *
 * <p>Implementations of this interface are fed to the Inventory, which then uses it in the implementation of the
 * {@link Feeds.ReadUpdateRegister#register(String, java.util.Map)} method.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface FeedIdStrategy {

    /**
     * Generates a unique ID for the provided feed. The id of the provided feed is the one proposed by the caller.
     *
     * @param inventory the inventory implementation to optionally consult some constraints
     * @param proposedFeed the feed to be generated with the ID proposed by the caller
     * @return the ID to be used for the feed, returned to the caller.
     * @throws EntityAlreadyExistsException if a feed with the proposed ID exists and no alternatives can be proposed
     *                                      by this strategy.
     */
    String generate(Inventory inventory, Feed proposedFeed) throws EntityAlreadyExistsException;
}
