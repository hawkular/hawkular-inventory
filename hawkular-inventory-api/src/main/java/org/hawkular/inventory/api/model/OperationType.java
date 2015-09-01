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

import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
public final class OperationType extends Entity<OperationType.Blueprint, OperationType.Update> {

    private OperationType() {
    }

    public OperationType(CanonicalPath path) {
        super(path);
    }

    public OperationType(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitOperationType(this, parameter);
    }

    @Override
    public Updater<Update, OperationType> update() {
        return new Updater<>((u) -> new OperationType(getPath(), u.getProperties()));
    }

    public static final class Blueprint extends Entity.Blueprint {

        public static Builder builder() {
            return new Builder();
        }

        public Blueprint(String id, Map<String, Object> properties) {
            super(id, properties);
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitOperationType(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            @Override
            public Blueprint build() {
                return new Blueprint(id, properties);
            }
        }
    }

    public static final class Update extends Entity.Update {

        public static Builder builder() {
            return new Builder();
        }

        public Update(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitOperationType(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(properties);
            }
        }
    }
}
