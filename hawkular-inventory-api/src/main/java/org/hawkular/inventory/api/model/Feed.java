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
package org.hawkular.inventory.api.model;

import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;

/**
 * Feed is a source of data. It reports about resources and metrics it knows about (and can send the actual data to
 * other Hawkular components like metrics).
 *
 * <p>Note that the feed does not have a dedicated blueprint type (i.e. data required to create a new feed
 * in some context), because the only data needed to create a new feed is its ID, which can easily be modelled
 * by a {@code String}.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
@ApiModel(description = "A feed represents a remote \"agent\" that is reporting its data to Hawkular.",
        parent = SyncedEntity.class)
public final class Feed extends SyncedEntity<Feed.Blueprint, Feed.Update> {

    public static final SegmentType SEGMENT_TYPE = SegmentType.f;

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private Feed() {
    }

    public Feed(CanonicalPath path, String identityHash, String contentHash, String syncHash) {
        this(path, identityHash, contentHash, syncHash, null);
    }

    public Feed(String name, CanonicalPath path, String identityHash, String contentHash, String syncHash) {
        super(name, path, identityHash, contentHash, syncHash);
    }

    public Feed(CanonicalPath path, String identityHash, String contentHash, String syncHash,
                Map<String, Object> properties) {
        super(path, identityHash, contentHash, syncHash, properties);
    }

    public Feed(String name, CanonicalPath path, String identityHash, String contentHash, String syncHash,
                Map<String, Object> properties) {
        super(name, path, identityHash, contentHash, syncHash, properties);
    }

    @Override
    public Updater<Update, Feed> update() {
        return new Updater<>((u) -> new Feed(u.getName(), getPath(), getIdentityHash(), getContentHash(), getSyncHash(),
                u.getProperties()));
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitFeed(this, parameter);
    }

    @ApiModel("FeedBlueprint")
    public static final class Blueprint extends Entity.Blueprint {

        public static Builder builder() {
            return new Builder();
        }
        private static final String AUTO_ID_FLAG = "__auto-generate-id";

        //JAXB support
        @SuppressWarnings("unused")
        private Blueprint() {
        }

        public Blueprint(String id, Map<String, Object> properties) {
            super(id == null ? AUTO_ID_FLAG : id, properties);
        }

        public Blueprint(String id, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, properties, outgoing, incoming);
        }

        public Blueprint(String id, String name, Map<String, Object> properties) {
            super(id, name, properties);
        }

        public Blueprint(String id, String name, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, name, properties, outgoing, incoming);
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitFeed(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {

            @Override
            public Blueprint build() {
                return new Blueprint(id, name, properties, outgoing, incoming);
            }
        }

        public static boolean shouldAutogenerateId(Feed proposedFeed) {
            return AUTO_ID_FLAG.equals(proposedFeed.getId());
        }
    }

    @ApiModel("FeedUpdate")
    public static final class Update extends Entity.Update {

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Update() {
            this(null);
        }

        public Update(Map<String, Object> properties) {
            super(null, properties);
        }

        public Update(String name, Map<String, Object> properties) {
            super(name, properties);
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitFeed(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(name, properties);
            }
        }
    }
}
