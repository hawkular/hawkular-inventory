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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all Hawkular entities.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public abstract class Entity {

    @XmlAttribute
    private final String id;
    private Map<String, Object> properties;

    //TODO has this the place in here?
    private Set<Relationship> relationships;

    /** JAXB support */
    Entity() {
        id = null;
    }

    Entity(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id == null");
        }

        this.id = id;
    }

    /**
     * The id of the entity.
     */
    public String getId() {
        return id;
    }

    /**
     * @return a map of arbitrary properties of this entity.
     */
    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new HashMap<>();
        }
        return properties;
    }

    /**
     * Accepts the provided visitor.
     *
     * @param visitor the visitor to visit this entity
     * @param parameter the parameter to pass on to the visitor
     * @param <R> the return type
     * @param <P> the type of the parameter
     * @return the return value provided by the visitor
     */
    public abstract <R, P> R accept(EntityVisitor<R, P> visitor, P parameter);

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Entity entity = (Entity) o;

        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public final String toString() {
        StringBuilder bld = new StringBuilder(getClass().getSimpleName());
        bld.append("[id='").append(id).append('\'');
        appendToString(bld);
        bld.append(']');

        return bld.toString();
    }
}
