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
package org.hawkular.inventory.api.filters;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class Related<T extends Entity> extends Filter {

    private final T entity;
    private final String relationshipName;
    private final Direction direction;

    public static Related<Environment> with(Environment environment) {
        return new Related<>(environment, Relationships.WellKnown.contains.name(), Owned.Direction.ANY);
    }

    public static Related<Tenant> with(Tenant tenant) {
        return new Related<>(tenant, Relationships.WellKnown.contains.name(), Owned.Direction.ANY);
    }

    public static Related<ResourceType> definedBy(ResourceType resourceType) {
        return new Related<>(resourceType, Relationships.WellKnown.defines.name(), Owned.Direction.ANY);
    }

    public static <U extends Entity> Related<U> with(U entity, String relationship) {
        return new Related<>(entity, relationship, Direction.OUT);
    }

    public static <U extends Entity> Related<U> with(U entity, Relationships.WellKnown relationship) {
        return new Related<>(entity, relationship.name(), Direction.OUT);
    }

    public static Related<?> by(String relationshipName) {
        return new Related<>(null, relationshipName, Direction.OUT);
    }

    public static Related<?> by(Relationships.WellKnown relationship) {
        return new Related<>(null, relationship.name(), Direction.OUT);
    }

    public static <U extends Entity> Related<U> asTargetWith(U entity, String relationship) {
        return new Related<>(entity, relationship, Direction.IN);
    }

    public static <U extends Entity> Related<U> asTargetWith(U entity, Relationships.WellKnown relationship) {
        return new Related<>(entity, relationship.name(), Direction.IN);
    }

    public static Related<?> asTargetBy(String relationshipName) {
        return new Related<>(null, relationshipName, Direction.IN);
    }

    public static Related<?> asTargetBy(Relationships.WellKnown relationship) {
        return new Related<>(null, relationship.name(), Direction.IN);
    }

    Related(T entity, String relationshipName, Direction direction) {
        this.entity = entity;
        this.relationshipName = relationshipName;
        this.direction = direction;
    }

    public T getEntity() {
        return entity;
    }

    public String getRelationshipName() {
        return relationshipName;
    }

    public Direction getDirection() {
        return direction;
    }

    public static enum Direction {
        IN, OUT, ANY
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (entity != null ? "entity=" + String.valueOf(entity) : "")
                + ", rel='" + relationshipName + "', dir=" + direction.name() + "]";
    }
}
