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
 * An environment is supposed to contain resources that belong to one infrastructure. Examples being "development",
 * "testing", "staging", "production", etc.
 *
 * <p>Note that the environment does not have a dedicated blueprint type (i.e. data required to create a new environment
 * in some context), because the only data needed to create a new environment is its ID, which can easily be modelled
 * by a {@code String}.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
@ApiModel(description = "Environment can incorporate feeds and can contain resources and metrics.",
        parent = Entity.class)
public final class Environment extends ContentHashedEntity<Environment.Blueprint, Environment.Update> {

    public static final SegmentType SEGMENT_TYPE = SegmentType.e;

    @SuppressWarnings("unused")
    private Environment() {
    }

    public Environment(CanonicalPath path, String contentHash) {
        super(path, contentHash);
    }

    public Environment(String name, CanonicalPath path, String contentHash) {
        super(name, path, contentHash);
    }

    public Environment(CanonicalPath path, String contentHash, Map<String, Object> properties) {
        this(null, path, contentHash, properties);
    }

    public Environment(String name, CanonicalPath path, String contentHash, Map<String, Object> properties) {
        super(name, path, contentHash, properties);
    }

    @Override
    public Updater<Update, Environment> update() {
        return new Updater<>((u) -> new Environment(u.getName(), getPath(), getContentHash(), u.getProperties()));
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitEnvironment(this, parameter);
    }

    @ApiModel("EnvironmentBlueprint")
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
            return visitor.visitEnvironment(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {

            @Override
            public Blueprint build() {
                return new Blueprint(id, name, properties, outgoing, incoming);
            }
        }
    }

    @ApiModel("EnvironmentUpdate")
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
            this(null, properties);
        }

        public Update(String name, Map<String, Object> properties) {
            super(name, properties);
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitEnvironment(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(name, properties);
            }
        }
    }
}
