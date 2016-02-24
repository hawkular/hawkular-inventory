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
package org.hawkular.inventory.base;

import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Query;
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

    private final Relationships.WellKnown relationship;
    private final Class<? extends Entity<?, ?>> sourceEntityType;

    protected Associator(TraversalContext<BE, E> context, Relationships.WellKnown relationship,
                         Class<? extends Entity<?, ?>> sourceEntityType) {
        super(context);
        this.relationship = relationship;
        this.sourceEntityType = sourceEntityType;
    }

    public Relationship associate(Path id) throws EntityNotFoundException, RelationAlreadyExistsException {
        checkPathLegal(id);
        return associate(context, sourceEntityType, relationship, id);
    }

    static <BE> Relationship associate(TraversalContext<BE, ?> context, Class<? extends Entity<?, ?>> sourceEntityType,
                                       Relationships.WellKnown relationship, Path id) {
        return inTx(context, tx -> {
            BE target = Util.find(tx, context.sourcePath, id);

            Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceEntityType)).get();

            BE source = tx.querySingle(sourceQuery);

            EntityAndPendingNotifications<BE, Relationship> rel = Util.createAssociation(tx, source,
                    relationship.name(), target);

            tx.getPreCommit().addNotifications(rel);

            return rel.getEntity();
        });
    }

    public Relationship disassociate(Path id) throws EntityNotFoundException {
        checkPathLegal(id);
        return disassociate(context, sourceEntityType, relationship, id);
    }

    static <BE> Relationship disassociate(TraversalContext<BE, ?> context,
                                          Class<? extends Entity<?, ?>> sourceEntityType,
                                          Relationships.WellKnown relationship, Path id) {
        return inTx(context, tx -> {
            BE target = Util.find(tx, context.sourcePath, id);

            Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceEntityType)).get();
            EntityAndPendingNotifications<BE, Relationship> rel = Util.deleteAssociation(tx, sourceQuery,
                    sourceEntityType, relationship.name(), target);

            tx.getPreCommit().addNotifications(rel);

            return rel.getEntity();
        });
    }

    public Relationship associationWith(Path path) throws RelationNotFoundException {
        return associationWith(context, sourceEntityType, relationship, path);
    }

    static <BE> Relationship associationWith(TraversalContext<BE, ?> context,
                                             Class<? extends Entity<?, ?>> sourceEntityType,
                                             Relationships.WellKnown relationship, Path path)
            throws RelationNotFoundException {

        return inTx(context, tx -> {
            Query sourceQuery = context.sourcePath.extend().filter().with(type(sourceEntityType)).get();
            Query targetQuery = Util.queryTo(context.sourcePath, path);

            @SuppressWarnings("unchecked")
            Class<? extends Entity<?, ?>> targetType = (Class<? extends Entity<?, ?>>) path.getSegment()
                    .getElementType();

            return Util.getAssociation(tx, sourceQuery, sourceEntityType, targetQuery, targetType, relationship.name());
        });
    }

    protected void checkPathLegal(Path targetPath) {
        if (!context.entityClass.equals(targetPath.getSegment().getElementType())) {
            throw new IllegalArgumentException("Current position in the inventory traversal expects entities of type " +
                    context.entityClass.getSimpleName() + " which is incompatible with the provided path: " +
                    targetPath);
        }
    }

    /**
     * @return the type of the source entity of the association on this position in the inventory traversal
     */
    @SuppressWarnings("unchecked")
    protected Class<? extends Entity<?, ?>> getSourceType() {
        return (Class<? extends Entity<?, ?>>) context.previous.entityClass;
    }
}
