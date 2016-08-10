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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;


/**
 * A resource is a grouping of other data (currently just metrics). A resource can have a type, which prescribes how
 * the data in the resource should look like.
 *
 * @author Heiko Rupp
 * @author Lukas Krejci
 */
@ApiModel(description = "A resource has a type, can have configuration and connection configuration and can" +
        " incorporate metrics.", parent = SyncedEntity.class)
public final class Resource extends SyncedEntity<Resource.Blueprint, Resource.Update> {

    public static final SegmentType SEGMENT_TYPE = SegmentType.r;

    private final ResourceType type;

    /**
     * Jackson support
     */
    @SuppressWarnings("unused")
    private Resource() {
        type = null;
    }

    public Resource(CanonicalPath path, String identityHash, String contentHash, String syncHash, ResourceType type) {
        this(path, identityHash, contentHash, syncHash, type, null);
    }

    public Resource(String name, CanonicalPath path, String identityHash, String contentHash, String syncHash,
                    ResourceType type) {
        this(name, path, identityHash, contentHash, syncHash, type, null);
    }

    public Resource(CanonicalPath path, String identityHash, String contentHash, String syncHash, ResourceType type,
                    Map<String, Object> properties) {
        super(path, identityHash, contentHash, syncHash, properties);
        this.type = type;
    }

    public Resource(String name, CanonicalPath path, String identityHash, String contentHash, String syncHash,
                    ResourceType type,
                    Map<String, Object> properties) {
        super(name, path, identityHash, contentHash, syncHash, properties);
        this.type = type;
    }

    @Override
    public Updater<Update, Resource> update() {
        return new Updater<>((u) -> new Resource(u.getName(), getPath(), getIdentityHash(), getContentHash(),
                getSyncHash(), getType(),
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
     * {@link org.hawkular.inventory.api.WriteInterface#create(org.hawkular.inventory.api.model.Blueprint)} method is
     * called.
     */
    @ApiModel("ResourceBlueprint")
    public static final class Blueprint extends Entity.Blueprint {
        private final String resourceTypePath;

        public static Builder builder() {
            return new Builder();
        }

        /**
         * JAXB support
         */
        @SuppressWarnings("unused")
        private Blueprint() {
            resourceTypePath = null;
        }

        public Blueprint(String id, String resourceTypePath) {
            this(id, resourceTypePath, Collections.emptyMap());
        }

        public Blueprint(String id, String resourceTypePath, Map<String, Object> properties) {
            super(id, properties);
            this.resourceTypePath = resourceTypePath;
        }

        public Blueprint(String id, String resourceTypePath, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, properties, outgoing, incoming);
            this.resourceTypePath = resourceTypePath;
        }

        public Blueprint(String id, String name, String resourceTypePath, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, name, properties, outgoing, incoming);
            this.resourceTypePath = resourceTypePath;
        }

        public String getResourceTypePath() {
            return resourceTypePath;
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitResource(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            private String resourceTypePath;

            public Builder withResourceTypePath(String resourceTypePath) {
                this.resourceTypePath = resourceTypePath;
                return this;
            }

            @Override
            public Blueprint build() {
                return new Blueprint(id, name, resourceTypePath, properties, outgoing, incoming);
            }
        }
    }

    @ApiModel("ResourceUpdate")
    public static final class Update extends Entity.Update {

        public static Builder builder() {
            return new Builder();
        }

        //Jackson support
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
            return visitor.visitResource(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(name, properties);
            }
        }
    }
}
