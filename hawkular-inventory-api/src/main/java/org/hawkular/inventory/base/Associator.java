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

import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;

/**
 * A base class for implementations of {@code *ReadAssociate} implementations.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
class Associator<BE, E extends Entity<?, ?>> extends Traversal<BE, E> {

    protected Associator(TraversalContext<BE, E> context) {
        super(context);
    }

    protected Relationship createAssociation(Class<? extends Entity<?, ?>> sourceType,
            Relationships.WellKnown relationship, BE target) {

        Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceType)).get();

        EntityAndPendingNotifications<Relationship> rel = Util.createAssociation(context, sourceQuery,
                sourceType, relationship.name(), target);

        context.notifyAll(rel);

        return rel.getEntity();
    }

    protected Relationship deleteAssociation(Class<? extends Entity<?, ?>> sourceType,
            Relationships.WellKnown relationship, BE target) {

        Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceType)).get();
        EntityAndPendingNotifications<Relationship> rel = Util.deleteAssociation(context, sourceQuery,
                sourceType, relationship.name(), target);

        context.notifyAll(rel);

        return rel.getEntity();
    }

    protected Relationship getAssociation(Class<? extends Entity<?, ?>> sourceType, Path targetPath,
            Relationships.WellKnown rel) {
        Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceType)).get();
        Query targetQuery = Util.queryTo(context, targetPath);

        @SuppressWarnings("unchecked")
        Class<? extends Entity<?, ?>> targetType = (Class<? extends Entity<?, ?>>) targetPath.getSegment()
                .getElementType();

        return Util.getAssociation(context, sourceQuery, sourceType, targetQuery, targetType, rel.name());
    }

    /**
     * @return the type of the source entity of the association on this position in the inventory traversal
     */
    @SuppressWarnings("unchecked")
    protected Class<? extends Entity<?, ?>> getSourceType() {
        return (Class<? extends Entity<?, ?>>) context.previous.entityClass;
    }

    public Relationship associate(Path id) throws EntityNotFoundException,
            RelationAlreadyExistsException {
        Query getTarget = Util.queryTo(context, id);

        BE target = getSingle(getTarget, context.entityClass);

        return createAssociation(getSourceType(), incorporates, target);
    }

    public Relationship disassociate(Path id) throws EntityNotFoundException {
        Query getTarget = Util.queryTo(context, id);

        BE target = getSingle(getTarget, context.entityClass);

        return deleteAssociation(getSourceType(), incorporates, target);
    }

    public Relationship associationWith(Path path) throws RelationNotFoundException {
        return getAssociation(getSourceType(), path, incorporates);
    }
}
