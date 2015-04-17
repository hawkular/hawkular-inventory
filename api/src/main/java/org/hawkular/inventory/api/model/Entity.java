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

import javax.xml.bind.annotation.XmlAttribute;
import java.util.Map;

/**
 * Base class for all Hawkular entities.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public abstract class Entity<B extends AbstractElement.Blueprint, U extends AbstractElement.Update>
        extends AbstractElement<B, U> {

    /**
     * JAXB support
     */
    Entity() {
    }

    Entity(CanonicalPath path) {
        this(path, null);
    }

    Entity(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
        if (!this.getClass().equals(path.getSegment().getElementType().getType())) {
            throw new IllegalArgumentException("Invalid path specified. Trying to create " +
                    this.getClass().getSimpleName() + " but the path points to " +
                    path.getSegment().getElementType().getType().getSimpleName());
        }
    }

    /**
     * Use this to append additional information to the string representation of this instance
     * returned from the (final) {@link #toString()}.
     *
     * <p>Generally, one should call the super method first and then only add additional information
     * to the builder.
     *
     * @param toStringBuilder the builder to append stuff to.
     */
    protected void appendToString(StringBuilder toStringBuilder) {

    }

    @Override
    public final String toString() {
        StringBuilder bld = new StringBuilder(getClass().getSimpleName());
        bld.append("[path='").append(getPath()).append('\'');
        appendToString(bld);
        bld.append(']');

        return bld.toString();
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
