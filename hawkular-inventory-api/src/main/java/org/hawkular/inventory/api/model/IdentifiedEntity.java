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

import javax.xml.bind.annotation.XmlAttribute;

/**
 * A base class for all entities that have a user-defined id that is used to distinguish the entity amongst its
 * siblings.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
public abstract class IdentifiedEntity<B extends IdentifiedEntity.Blueprint, U extends AbstractElement.Update>
        extends Entity<B, U> {

    IdentifiedEntity() {
    }

    IdentifiedEntity(CanonicalPath path) {
        this(path, null);
    }

    IdentifiedEntity(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
        if (!this.getClass().equals(path.getSegment().getElementType())) {
            throw new IllegalArgumentException("Invalid path specified. Trying to create " +
                    this.getClass().getSimpleName() + " but the path points to " +
                    path.getSegment().getElementType().getSimpleName());
        }
    }

    public abstract static class Blueprint extends AbstractElement.Blueprint {
        @XmlAttribute
        private final String id;

        protected Blueprint(String id, Map<String, Object> properties) {
            super(properties);
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public abstract static class Builder<Blueprint, This extends Builder<Blueprint, This>>
                extends AbstractElement.Blueprint.Builder<Blueprint, This> {

            protected String id;

            public This withId(String id) {
                this.id = id;
                return castThis();
            }
        }
    }
}
