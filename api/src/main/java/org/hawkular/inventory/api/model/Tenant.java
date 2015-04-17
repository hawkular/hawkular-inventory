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
import java.util.Collections;
import java.util.Map;

/**
 * A tenant is a top level entity that owns everything else. Multiple tenants are not supposed to share anything between
 * each other.
 *
 * <p>Note that the tenant does not have a dedicated blueprint type (i.e. data required to create a new tenant
 * in some context), because the only data needed to create a new tenant is its ID, which can easily be modelled
 * by a {@code String}.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
@XmlRootElement
public final class Tenant extends Entity<Tenant.Blueprint, Tenant.Update> {

    public Tenant(CanonicalPath path) {
        this(path, null);
    }

    @JsonCreator
    public Tenant(@JsonProperty("path") CanonicalPath path,
            @JsonProperty("properties") Map<String, Object> properties) {
        super(path, properties);
    }

    @Override
    public Updater<Update, Tenant> update() {
        return new Updater<>((u) -> new Tenant(getPath(), u.getProperties()));
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitTenant(this, parameter);
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

        public Blueprint(String id) {
            super(id, Collections.emptyMap());
        }

        public Blueprint(String id, Map<String, Object> properties) {
            super(id, properties);
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitTenant(this, parameter);
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

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitTenant(this, parameter);
        }

        public static final class Builder extends AbstractElement.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(properties);
            }
        }
    }
}
