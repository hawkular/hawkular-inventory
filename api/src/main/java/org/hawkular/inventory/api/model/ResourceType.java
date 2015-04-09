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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.HashMap;
import java.util.Map;

/**
 * Type of a resource. A resource type is versioned and currently just defines the types of metrics that should be
 * present in the resources of this type.
 *
 * @author Lukas Krejci
 */
@XmlRootElement
public final class ResourceType extends OwnedEntity {

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
     * {@link org.hawkular.inventory.api.WriteInterface#create(Object)} method is called.
     */
    @XmlRootElement
    public static final class Blueprint extends Entity.AbstractBlueprint{
        @XmlAttribute
        private final String id;

        @XmlAttribute
        @XmlJavaTypeAdapter(VersionAdapter.class)
        private final Version version;

        public Blueprint(String id, Version version, Map<String, Object> properties) {
            super(properties);
            this.id = id;
            this.version = version;
        }

        public Blueprint(String id, Version version) {
            this(id, version, new HashMap<>());
        }

        public String getId() {
            return id;
        }

        public Version getVersion() {
            return version;
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
