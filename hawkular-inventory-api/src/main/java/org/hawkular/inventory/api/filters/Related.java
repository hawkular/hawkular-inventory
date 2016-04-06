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
package org.hawkular.inventory.api.filters;

import java.util.Objects;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.paths.CanonicalPath;

/**
 * Defines a filter on entities having specified relationship.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class Related extends Filter {

    private final CanonicalPath entityPath;
    private final String relationshipName;
    private final String relationshipId;
    private final EntityRole entityRole;

    /**
     * Specifies a filter for entities that are sources of a relationship with the specified entity.
     *
     * @param entityPath the entity that is the target of the relationship
     * @param relationship the name of the relationship
     * @return a new "related" filter instance
     */
    public static Related with(CanonicalPath entityPath, String relationship) {
        return new Related(entityPath, relationship, EntityRole.SOURCE);
    }

    /**
     * An overloaded version of {@link #with(org.hawkular.inventory.paths.CanonicalPath, String)} that uses one of
     * the {@link org.hawkular.inventory.api.Relationships.WellKnown} as the name of the relationship.
     *
     * @param entityPath the entity that is the target of the relationship
     * @param relationship the type of the relationship
     * @return a new "related" filter instance
     */
    public static Related with(CanonicalPath entityPath, Relationships.WellKnown relationship) {
        return new Related(entityPath, relationship.name(), EntityRole.SOURCE);
    }

    /**
     * Creates a filter for entities that are sources of at least one relationship with the specified name. The target
     * entity is not specified and can be anything.
     *
     * @param relationshipName the name of the relationship
     * @return a new "related" filter instance
     */
    public static Related by(String relationshipName) {
        return new Related(null, relationshipName, EntityRole.SOURCE);
    }

    /**
     * Overloaded version of {@link #by(String)} that uses the
     * {@link org.hawkular.inventory.api.Relationships.WellKnown} as the name of the relationship.
     *
     * @param relationship the type of the relationship
     * @return a new "related" filter instance
     */
    public static Related by(Relationships.WellKnown relationship) {
        return new Related(null, relationship.name(), EntityRole.SOURCE);
    }

    /**
     * Creates a filter for entities that are sources of at least one relationship with the specified id. The target
     * entity is not specified and can be anything.
     *
     * @param relationshipId the id of the relationship
     * @return a new "related" filter instance
     */
    public static Related byRelationshipWithId(String relationshipId) {
        return new Related(null, relationshipId, EntityRole.SOURCE);
    }

    /**
     * Specifies a filter for entities that are targets of a relationship with the specified entity.
     *
     * @param entityPath the entity that is the source of the relationship
     * @param relationship the name of the relationship
     * @return a new "related" filter instance
     */
    public static Related asTargetWith(CanonicalPath entityPath, String relationship) {
        return new Related(entityPath, relationship, EntityRole.TARGET);
    }

    /**
     * An overloaded version of {@link #asTargetWith(org.hawkular.inventory.paths.CanonicalPath, String)} that uses
     * one of the {@link org.hawkular.inventory.api.Relationships.WellKnown} as the name of the relationship.
     *
     * @param entityPath the entity that is the source of the relationship
     * @param relationship the type of the relationship
     * @return a new "related" filter instance
     */
    public static Related asTargetWith(CanonicalPath entityPath, Relationships.WellKnown relationship) {
        return new Related(entityPath, relationship.name(), EntityRole.TARGET);
    }

    /**
     * Creates a filter for entities that are targets of at least one relationship with the specified name. The source
     * entity is not specified and can be anything.
     *
     * @param relationshipName the name of the relationship
     * @return a new "related" filter instance
     */
    public static Related asTargetBy(String relationshipName) {
        return new Related(null, relationshipName, EntityRole.TARGET);
    }

    /**
     * Overloaded version of {@link #asTargetBy(String)} that uses the
     * {@link org.hawkular.inventory.api.Relationships.WellKnown} as the name of the relationship.
     *
     * @param relationship the type of the relationship
     * @return a new "related" filter instance
     */
    public static Related asTargetBy(Relationships.WellKnown relationship) {
        return new Related(null, relationship.name(), EntityRole.TARGET);
    }

    protected Related(CanonicalPath entityPath, String relationshipName, String relationshipId, EntityRole entityRole) {
        this.entityPath = entityPath;
        this.relationshipName = relationshipName;
        this.relationshipId = relationshipId;
        this.entityRole = entityRole;
    }

    protected Related(CanonicalPath entityPath, String relationshipName, EntityRole entityRole) {
        this(entityPath, relationshipName, null, entityRole);
    }

    /**
     * @return the entity used for creating this filter.
     */
    public CanonicalPath getEntityPath() {
        return entityPath;
    }

    /**
     * @return the name of the relationship
     */
    public String getRelationshipName() {
        return relationshipName;
    }

    /**
     * @return the id of the relationship
     */
    public String getRelationshipId() {
        return relationshipId;
    }

    /**
     * @return the role of the entity in the filter
     */
    public EntityRole getEntityRole() {
        return entityRole;
    }

    public enum EntityRole {
        TARGET, SOURCE, ANY
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (entityPath != null ? "entity=" + String.valueOf(entityPath) : "")
                + ", rel='" + relationshipName + "', role=" + entityRole.name() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Related)) return false;

        Related related = (Related) o;

        if (!Objects.equals(entityPath, related.entityPath)) {
            return false;
        }

        if (!Objects.equals(relationshipName, related.relationshipName)) {
            return false;
        }

        if (!Objects.equals(relationshipId, related.relationshipId)) {
            return false;
        }

        return entityRole == related.entityRole;

    }

    @Override
    public int hashCode() {
        int result = entityPath != null ? entityPath.hashCode() : 0;
        result = 31 * result + (relationshipName != null ? relationshipName.hashCode() : 0);
        result = 31 * result + (relationshipId != null ? relationshipId.hashCode() : 0);
        result = 31 * result + (entityRole != null ? entityRole.hashCode() : 0);
        return result;
    }
}
