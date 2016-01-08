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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;

import org.hawkular.inventory.paths.CanonicalPath;

/**
 * Type of a resource. A resource type is versioned and currently just defines the types of metrics that should be
 * present in the resources of this type.
 *
 * @author Lukas Krejci
 */
@XmlRootElement
public final class ResourceType extends Entity<ResourceType.Blueprint, ResourceType.Update> {

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private ResourceType() {
    }

    public ResourceType(CanonicalPath path) {
        this(path, null);
    }

    public ResourceType(String name, CanonicalPath path) {
        super(name, path);
    }

    public ResourceType(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
    }

    public ResourceType(String name, CanonicalPath path, Map<String, Object> properties) {
        super(name, path, properties);
    }

    @Override
    public Updater<Update, ResourceType> update() {
        return new Updater<>((u) -> new ResourceType(u.getName(), getPath(), u.getProperties()));
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitResourceType(this, parameter);
    }

    /**
     * Data required to create a resource type.
     *
     * <p>Note that tenantId, etc., are not needed here because they are provided by the context in which the
     * {@link org.hawkular.inventory.api.WriteInterface#create(org.hawkular.inventory.api.model.Blueprint)} method is
     * called.
     */
    @XmlRootElement
    public static final class Blueprint extends Entity.Blueprint {

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Blueprint() {
        }

        public Blueprint(String id) {
            this(id, Collections.emptyMap());
        }

        public Blueprint(String id, Map<String, Object> properties) {
            super(id, properties);
        }

        public Blueprint(String id, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, properties, outgoing, incoming);
        }

        public Blueprint(String id, String name, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, name, properties, outgoing, incoming);
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitResourceType(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {

            @Override
            public Blueprint build() {
                return new Blueprint(id, name, properties, outgoing, incoming);
            }
        }
    }

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
            return visitor.visitResourceType(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {

            @Override
            public Update build() {
                return new Update(name, properties);
            }
        }
    }
}
