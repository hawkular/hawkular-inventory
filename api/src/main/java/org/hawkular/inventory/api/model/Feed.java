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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Map;

/**
 * Feed is a source of data. It reports about resources and metrics it knows about (and can send the actual data to
 * other Hawkular components like metrics).
 *
 * <p>Note that the feed does not have a dedicated blueprint type (i.e. data required to create a new feed
 * in some context), because the only data needed to create a new feed is its ID, which can easily be modelled
 * by a {@code String}.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
@XmlRootElement
public final class Feed extends EnvironmentBasedEntity<Feed.Blueprint, Feed.Update> {

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private Feed() {
    }

    public Feed(String tenantId, String environmentId, String id) {
        super(tenantId, environmentId, id);
    }

    @JsonCreator
    public Feed(@JsonProperty("tenant") String tenantId, @JsonProperty("environment") String environmentId,
            @JsonProperty("id") String id, @JsonProperty("properties") Map<String, Object> properties) {
        super(tenantId, environmentId, id, properties);
    }

    @Override
    public Updater<Update, Feed> update() {
        return new Updater<>((u) -> new Feed(this.getTenantId(), this.getEnvironmentId(), this.getId(),
                u.getProperties()));
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitFeed(this, parameter);
    }

    public static final class Blueprint extends Entity.Blueprint {

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null);
        }

        public Blueprint(String id, Map<String, Object> properties) {
            super(id, properties);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {

            @Override
            public Blueprint build() {
                return new Blueprint(id, properties);
            }
        }
    }

    public static final class Update extends AbstractElement.Update {

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Update() {
            this(null);
        }

        public Update(Map<String, Object> properties) {
            super(properties);
        }

        public static final class Builder extends AbstractElement.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(properties);
            }
        }
    }
}
