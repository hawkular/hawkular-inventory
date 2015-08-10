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

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.With.id;

import java.util.Collections;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.PartiallyApplied;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.ElementTypeVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
abstract class Mutator<BE, E extends Entity<?, U>, B extends Blueprint, U extends AbstractElement.Update, Id>
        extends Traversal<BE, E> {

    protected Mutator(TraversalContext<BE, E> context) {
        super(context);
    }

    /**
     * Extracts the proposed ID from the blueprint or identifies the ID through some other means.
     *
     * @param blueprint the blueprint of the entity to be created
     * @return the ID to be used for the new entity
     */
    protected abstract String getProposedId(B blueprint);

    /**
     * A helper method to be used in the implementation of the
     * {@link org.hawkular.inventory.api.WriteInterface#create(Blueprint)} method.
     *
     * <p>The callers may merely use the returned query and construct a new {@code *Single} instance using it.
     *
     * @param blueprint the blueprint of the new entity
     * @return the query to the newly created entity.
     */
    protected final Query doCreate(B blueprint) {
        return mutating((transaction) -> {
            String id = getProposedId(blueprint);

            Query existenceCheck = context.hop().filter().with(id(id)).get();

            Page<BE> results = context.backend.query(existenceCheck, Pager.single());

            if (!results.isEmpty()) {
                throw new EntityAlreadyExistsException(id, Query.filters(existenceCheck));
            }

            BE parent = getParent();
            CanonicalPath parentCanonicalPath = parent == null ? null : context.backend.extractCanonicalPath(parent);

            EntityAndPendingNotifications<E> newEntity;
            BE containsRel = null;

            CanonicalPath entityPath;
            if (parent == null) {
                if (context.entityClass == Tenant.class) {
                    entityPath = CanonicalPath.of().tenant(id).get();
                } else {
                    throw new IllegalStateException("Could not find the parent of the entity to be created," +
                            "yet the entity is not a tenant: " + blueprint);
                }
            } else {
                entityPath = parentCanonicalPath.extend(context.entityClass, id).get();
            }

            BE entityObject = context.backend.persist(entityPath, blueprint);

            if (parentCanonicalPath != null) {
                //no need to check for contains rules - we're connecting a newly created entity
                containsRel = context.backend.relate(parent, entityObject, contains.name(), Collections.emptyMap());
            }

            newEntity = wireUpNewEntity(entityObject, blueprint, parentCanonicalPath, parent);

            context.backend.commit(transaction);

            context.notify(newEntity.getEntity(), created());
            if (containsRel != null) {
                context.notify(context.backend.convert(containsRel, Relationship.class), created());
            }
            context.notifyAll(newEntity);

            return Query.to(entityPath);
        });
    }

    public final void update(Id id, U update) throws EntityNotFoundException {
        Query q = id == null ? context.select().get() : context.select().with(id(id.toString())).get();
        Util.update(context, q, update);
    }

    public final void delete(Id id) throws EntityNotFoundException {
        Query q = id == null ? context.select().get() : context.select().with(id(id.toString())).get();
        Util.delete(context, q, PartiallyApplied.procedure(this::cleanup).first(id).asConsumer());
    }

    /**
     * A hook that can run additional clean up logic inside the delete transaction.
     *
     * <p>This hook is called prior to anything being deleted.
     *
     * <p>By default this does nothing.
     *
     * @param id                   the id of the entity being deleted
     * @param entityRepresentation the backend specific representation of the entity
     */
    protected void cleanup(Id id, BE entityRepresentation) {

    }

    private BE getParent() {
        return ElementTypeVisitor.accept(context.entityClass, new ElementTypeVisitor.Simple<BE, Void>() {
            @SuppressWarnings("unchecked")
            @Override
            protected BE defaultAction() {
                Page<BE> res = context.backend.query(context.sourcePath, Pager.single());

                if (res.isEmpty()) {
                    Class<? extends Entity<?, ?>> parentEntityClass = null;
                    if (context.previous != null && Entity.class.isAssignableFrom(context.previous.entityClass)) {
                        parentEntityClass = (Class<? extends Entity<?, ?>>) context.previous.entityClass;
                    }

                    throw new EntityNotFoundException(parentEntityClass, Query.filters(context.sourcePath));
                }

                return res.get(0);
            }

            @Override
            public BE visitTenant(Void parameter) {
                return null;
            }
        }, null);
    }

    protected BE relate(BE source, BE target, String relationshipName) {
        RelationshipRules.checkCreate(context.backend, source, outgoing, relationshipName, target);
        return context.backend.relate(source, target, relationshipName, null);
    }

    /**
     * Wires up the freshly created entity in the appropriate places in inventory. The "contains" relationship between
     * the parent and the new entity will already have been created so the implementations don't need to do that again.
     *
     * <p>The wiring up might result in new relationships being created or other "notifiable" actions - the returned
     * object needs to reflect that so that the notification can correctly be emitted.
     *
     * @param entity     the freshly created, uninitialized entity
     * @param blueprint  the blueprint that it prescribes how the entity should be initialized
     * @param parentPath the path to the parent entity
     * @param parent     the actual parent entity
     * @return an object with the initialized and converted entity together with any pending notifications to be sent
     * out
     */
    protected abstract EntityAndPendingNotifications<E> wireUpNewEntity(BE entity, B blueprint,
            CanonicalPath parentPath, BE parent);
}
