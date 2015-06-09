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
package org.hawkular.inventory.base;

import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.filters.With.type;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
class Associator<BE, E extends Entity<?, ?>> extends Traversal<BE, E> {

    protected Associator(TraversalContext<BE, E> context) {
        super(context);
    }

    protected Relationship createAssociation(Class<? extends Entity<?, ?>> sourceType,
            Relationships.WellKnown relationship, BE target) {

        Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceType)).get();
        BE source = getSingle(sourceQuery, sourceType);

        if (context.backend.hasRelationship(source, target, relationship.name())) {
            throw new RelationAlreadyExistsException(relationship.name(), Query.filters(sourceQuery));
        }

        BE relationshipObject = context.backend.relate(source, target, relationship.name(), null);

        context.backend.commit();

        Relationship ret = context.backend.convert(relationshipObject, Relationship.class);

        context.notify(ret, created());

        return ret;
    }

    protected Relationship deleteAssociation(Class<? extends Entity<?, ?>> sourceType,
            Relationships.WellKnown relationship, Class<? extends Entity<?, ?>> targetType, BE target) {

        Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceType)).get();
        BE source = getSingle(sourceQuery, sourceType);

        BE relationshipObject = context.backend.getRelationship(source, target, relationship.name());

        if (relationshipObject == null) {
            throw new RelationNotFoundException(sourceType, relationship.name(), Query.filters(sourceQuery),
                    null, null);
        }

        Relationship ret = context.backend.convert(relationshipObject, Relationship.class);

        context.backend.delete(relationshipObject);

        context.backend.commit();

        context.notify(ret, deleted());

        return ret;
    }

    protected Relationship getAssociation(Class<? extends Entity<?, ?>> sourceType, String targetId,
            Class<? extends Entity<?, ?>> targetType, Relationships.WellKnown rel) {

        Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceType)).get();
        Query targetQuery = context.sourcePath.extend().path().with(type(sourceType), Related.by(rel))
                .filter().with(With.type(targetType), With.id(targetId)).get();

        BE source = getSingle(sourceQuery, sourceType);
        BE target = getSingle(targetQuery, targetType);

        BE relationship = context.backend.getRelationship(source, target, rel.name());

        return context.backend.convert(relationship, Relationship.class);
    }
}
