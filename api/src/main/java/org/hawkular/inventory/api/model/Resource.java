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
import com.google.gson.annotations.Expose;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.Map;

/**
 * A resource is a grouping of other data (currently just metrics). A resource can have a type, which prescribes how
 * the data in the resource should look like.
 *
 * @author Heiko Rupp
 * @author Lukas Krejci
 */
@XmlRootElement
public final class Resource extends FeedBasedEntity<Resource.Blueprint, Resource.Update> {

    @Expose
    private final ResourceType type;

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private Resource() {
        type = null;
    }

    public Resource(String tenantId, String environmentId, String feedId, String id, ResourceType type) {
        super(tenantId, environmentId, feedId, id);
        this.type = type;
    }

    @JsonCreator
    public Resource(@JsonProperty("tenant") String tenantId, @JsonProperty("environment") String environmentId,
            @JsonProperty("feed") String feedId, @JsonProperty("id") String id, @JsonProperty("type") ResourceType type,
            @JsonProperty("properties") Map<String, Object> properties) {

        super(tenantId, environmentId, feedId, id, properties);
        this.type = type;
    }

    @Override
    public Updater<Update, Resource> update() {
        return new Updater<>((u) -> new Resource(getTenantId(), getEnvironmentId(), getFeedId(), getId(), getType(),
                u.getProperties()));
    }

    public ResourceType getType() {
        return type;
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitResource(this, parameter);
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", type=").append(type);
    }

    /**
     * Data required to create a resource.
     *
     * <p>Note that tenantId, etc., are not needed here because they are provided by the context in which the
     * {@link org.hawkular.inventory.api.WriteInterface#create(Entity.Blueprint)} method is called.
     */
    @XmlRootElement
    public static final class Blueprint extends Entity.Blueprint {
        private final String resourceTypeId;

        public static Builder builder() {
            return new Builder();
        }

        /**
         * JAXB support
         */
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null, null);
        }

        public Blueprint(String id, String resourceTypeId) {
            this(id, resourceTypeId, Collections.emptyMap());
        }

        public Blueprint(String id, String resourceTypeId, Map<String, Object> properties) {
            super(id, properties);
            this.resourceTypeId = resourceTypeId;
        }

        public String getResourceTypeId() {
            return resourceTypeId;
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            private String resourceTypeId;

            public Builder withResourceType(String resourceTypeId) {
                this.resourceTypeId = resourceTypeId;
                return this;
            }

            @Override
            public Blueprint build() {
                return new Blueprint(id, resourceTypeId, properties);
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
