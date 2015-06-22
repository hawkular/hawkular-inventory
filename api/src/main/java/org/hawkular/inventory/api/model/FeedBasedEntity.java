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
package org.hawkular.inventory.api.model;

import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
public abstract class FeedBasedEntity<B extends Entity.Blueprint, U extends AbstractElement.Update>
        extends EnvironmentBasedEntity<B, U> {

    FeedBasedEntity() {
    }

    protected FeedBasedEntity(CanonicalPath path) {
        this(path, null);
    }

    public FeedBasedEntity(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
        if (path.getDepth() < 2) {
            throw new IllegalArgumentException("A feed-based entity must be contained either in an environment or a" +
                    " feed. The supplied path is too short for that.");
        }
    }

    /**
     * The id of the feed that this entity belongs to or null, if this entity does not belong to any particular feed.
     *
     * (It is allowed for an entity that can exist under a feed to also exist directly under an environment).
     *
     * @return the id of the feed this entity lives under or null if this entity lives directly under an environment
     */
    public String getFeedId() {
        CanonicalPath.Segment seg = getPath().getRoot().down().down().getSegment();
        return Feed.class.equals(seg.getElementType()) ? seg.getElementId() : null;
    }
}
