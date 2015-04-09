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

import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
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
public final class Feed extends EnvironmentalEntity {

    /** JAXB support */
    @SuppressWarnings("unused")
    private Feed() {
    }

    public Feed(String tenantId, String environmentId, String id) {
        super(tenantId, environmentId, id);
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitFeed(this, parameter);
    }

    public static class Blueprint extends Entity.AbstractBlueprint {
        private final String id;

        public Blueprint(String id) {
            this(id, new HashMap<>());
        }

        public Blueprint(String id, Map<String, Object> properties) {
            super(properties);
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
