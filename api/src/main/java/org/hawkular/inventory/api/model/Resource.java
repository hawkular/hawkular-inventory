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
public final class Resource extends EnvironmentalEntity {

    @Expose
    private final ResourceType type;

    /** JAXB support */
    @SuppressWarnings("unused")
    private Resource() {
        type = null;
    }

    public Resource(String tenantId, String environmentId, String id, ResourceType type) {
        super(tenantId, environmentId, id);
        this.type = type;
    }

    public ResourceType getType() {
        return type;
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
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

        /** JAXB support */
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
}
