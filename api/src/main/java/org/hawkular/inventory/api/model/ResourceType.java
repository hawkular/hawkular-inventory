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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Collections;
import java.util.Map;

/**
 * Type of a resource. A resource type is versioned and currently just defines the types of metrics that should be
 * present in the resources of this type.
 *
 * @author Lukas Krejci
 */
@XmlRootElement
public final class ResourceType extends OwnedEntity<ResourceType.Blueprint, ResourceType.Update> {

    @XmlAttribute
    @XmlJavaTypeAdapter(VersionAdapter.class)
    @Expose
    private final Version version;

    /** JAXB support */
    @SuppressWarnings("unused")
    private ResourceType() {
        this.version = null;
    }

    public ResourceType(String tenantId, String id, Version version) {
        super(tenantId, id);

        if (version == null) {
            throw new IllegalArgumentException("version == null");
        }

        this.version = version;
    }

    public ResourceType(String tenantId, String id, String version) {
        this(tenantId, id, new Version(version));
    }

    /** JSON serialization support */
    @JsonCreator
    public ResourceType(@JsonProperty("tenant") String tenantId, @JsonProperty("id") String id,
            @JsonProperty("version") String version, @JsonProperty("properties") Map<String, Object> properties) {
        this(tenantId, id, new Version(version), properties);
    }

    public ResourceType(String tenantId, String id, Version version, Map<String, Object> properties) {
        super(tenantId, id, properties);
        this.version = version;
    }

    @Override
    public Updater<Update, ResourceType> update() {
        return new Updater<>((u) -> new ResourceType(getTenantId(), getId(),
                u.version == null ? this.version : new Version(u.version), u.getProperties()));
    }

    public Version getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }

        ResourceType that = (ResourceType) o;

        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitResourceType(this, parameter);
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", version=").append(version);
    }

    /**
     * Data required to create a resource type.
     *
     * <p>Note that tenantId, etc., are not needed here because they are provided by the context in which the
     * {@link org.hawkular.inventory.api.WriteInterface#create(Entity.Blueprint)} method is called.
     */
    @XmlRootElement
    public static final class Blueprint extends Entity.Blueprint {
        @XmlAttribute
        private final String version;

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null, null);
        }

        public Blueprint(String id, String version) {
            this(id, version, Collections.emptyMap());
        }

        public Blueprint(String id, String version, Map<String, Object> properties) {
            super(id, properties);
            this.version = version;
        }

        public String getVersion() {
            return version;
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            private String version;

            public Builder withVersion(String version) {
                this.version = version;
                return this;
            }

            @Override
            public Blueprint build() {
                return new Blueprint(id, version, properties);
            }
        }
    }

    public static final class Update extends AbstractElement.Update {
        private final String version;

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Update() {
            this(null, null);
        }

        public Update(Map<String, Object> properties, String version) {
            super(properties);
            this.version = version;
        }

        public String getVersion() {
            return version;
        }

        public static final class Builder extends AbstractElement.Update.Builder<Update, Builder> {
            private String version;

            public Builder withVersion(String version) {
                this.version = version;
                return this;
            }

            @Override
            public Update build() {
                return new Update(properties, version);
            }
        }
    }

    private static final class VersionAdapter extends XmlAdapter<String, Version> {

        @Override
        public Version unmarshal(String v) throws Exception {
            return new Version(v);
        }

        @Override
        public String marshal(Version v) throws Exception {
            return v.toString();
        }
    }
}
