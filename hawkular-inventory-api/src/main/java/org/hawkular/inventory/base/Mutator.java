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

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.With.id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.ElementTypeVisitor;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
abstract class Mutator<BE, E extends Entity<?, U>, B extends Blueprint, U extends Entity.Update, Id>
        extends Traversal<BE, E> {

    protected Mutator(TraversalContext<BE, E> context) {
        super(context);
    }

    /**
     * Extracts the proposed ID from the blueprint or identifies the ID through some other means.
     *
     *
     * @param tx the transaction this method is called within
     * @param blueprint the blueprint of the entity to be created
     * @return the ID to be used for the new entity
     */
    protected abstract String getProposedId(Transaction<BE> tx, B blueprint);

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
        return inTx(tx -> {
            String id = getProposedId(tx, blueprint);

            if (!tx.isUniqueIndexSupported()) {
                //poor man's way of ensuring uniqueness of CPs
                Query existenceCheck = context.hop().filter().with(id(id)).get();

                Page<BE> results = tx.query(existenceCheck, Pager.single());

                if (results.hasNext()) {
                    throw new EntityAlreadyExistsException(id, Query.filters(existenceCheck));
                }
            }

            preCreate(blueprint, tx);

            BE parent = getParent(tx);
            CanonicalPath parentCanonicalPath = parent == null ? null : tx.extractCanonicalPath(parent);

            EntityAndPendingNotifications<BE, E> newEntity;
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
                entityPath = parentCanonicalPath.extend(AbstractElement.segmentTypeFromType(context.entityClass), id)
                        .get();
            }

            BE entityObject = tx.persist(entityPath, blueprint);

            if (parentCanonicalPath != null) {
                //no need to check for contains rules - we're connecting a newly created entity
                containsRel = tx.relate(parent, entityObject, contains.name(), Collections.emptyMap());
                Relationship rel = tx.convert(containsRel, Relationship.class);
                tx.getPreCommit().addNotifications(
                        new EntityAndPendingNotifications<>(containsRel, rel, new Notification<>(rel, rel, created())));
            }

            newEntity = wireUpNewEntity(entityObject, blueprint, parentCanonicalPath, parent, tx);

            if (blueprint instanceof Entity.Blueprint) {
                Entity.Blueprint b = (Entity.Blueprint) blueprint;
                createCustomRelationships(entityObject, outgoing, b.getOutgoingRelationships(), tx);
                createCustomRelationships(entityObject, incoming, b.getIncomingRelationships(), tx);
            }

            postCreate(entityObject, newEntity.getEntity(), tx);

            List<Notification<?, ?>> notifs = new ArrayList<>(newEntity.getNotifications());
            notifs.add(new Notification<>(newEntity.getEntity(), newEntity.getEntity(), Action.created()));

            EntityAndPendingNotifications<BE, E> pending =
                    new EntityAndPendingNotifications<>(newEntity.getEntityRepresentation(), newEntity.getEntity(),
                            notifs);

            tx.getPreCommit().addNotifications(pending);

            return Query.to(entityPath);
        });
    }

    public final void update(Id id, U update) throws EntityNotFoundException {
        inTx(tx -> {
            Query q = id == null ? context.select().get() : context.select().with(id(id.toString())).get();
            Util.update(context.entityClass, tx, q, update, (e, u, t) -> preUpdate(id, e, u, t), this::postUpdate);
            return null;
        });
    }

    public final void delete(Id id) throws EntityNotFoundException {
        inTx(tx -> {
            Query q = id == null ? context.select().get() : context.select().with(id(id.toString())).get();
            Util.delete(context.entityClass, tx, q, (e, t) -> preDelete(id, e, t), this::postDelete);
            return null;
        });
    }

    protected void preCreate(B blueprint, Transaction<BE> transaction) {

    }

    protected void postCreate(BE entityObject, E entity, Transaction<BE> transaction) {

    }

    /**
     * A hook that can run additional clean up logic inside the delete transaction.
     *
     * <p>This hook is called prior to anything being deleted.
     *
     * <p>By default this does nothing.
     *  @param id                   the id of the entity being deleted
     * @param entityRepresentation the backend specific representation of the entity
     * @param transaction          the transaction in which the delete is executing
     */
    protected void preDelete(Id id, BE entityRepresentation, Transaction<BE> transaction) {

    }

    protected void postDelete(BE entityRepresentation, Transaction<BE> transaction) {

    }

    /**
     * A hook that can run additional logic inside the update transaction before anything has been persisted to the
     * backend database.
     *
     * <p>By default, this does nothing
     *  @param id                   the id of the entity being updated
     * @param entityRepresentation the backend representation of the updated entity
     * @param update               the update object
     * @param transaction          the transaction in which the update is executing
     */
    protected void preUpdate(Id id, BE entityRepresentation, U update, Transaction<BE> transaction) {

    }

    protected void postUpdate(BE entityRepresentation, Transaction<BE> transaction) {

    }

    protected BE getParent(Transaction<BE> tx) {
        return ElementTypeVisitor.accept(AbstractElement.segmentTypeFromType(context.entityClass),
                new ElementTypeVisitor.Simple<BE, Void>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    protected BE defaultAction() {
                        BE res = tx.querySingle(context.sourcePath);

                        if (res == null) {
                            throw new EntityNotFoundException(context.previous.entityClass,
                                    Query.filters(context.sourcePath));
                        }

                        return res;
                    }

                    @Override
                    public BE visitTenant(Void parameter) {
                        return null;
                    }
                }, null);
    }

    protected BE relate(BE source, BE target, String relationshipName) {
        return inTx(tx -> {
            RelationshipRules.checkCreate(tx, source, outgoing, relationshipName, target);
            return tx.relate(source, target, relationshipName, null);
        });
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
     * @param transaction the transaction this is being executed in
     * @return an object with the initialized and converted entity together with any pending notifications to be sent
     * out
     */
    protected abstract EntityAndPendingNotifications<BE, E>
    wireUpNewEntity(BE entity, B blueprint, CanonicalPath parentPath, BE parent,
                    Transaction<BE> transaction);

    private void createCustomRelationships(BE entity, Relationships.Direction direction,
                                           Map<String, Set<CanonicalPath>> otherEnds,
                                           Transaction<BE> tx) {
        otherEnds.forEach((name, ends) -> ends.forEach((end) -> {
            try {
                BE endObject = tx.find(end);

                BE from = direction == outgoing ? entity : endObject;
                BE to = direction == outgoing ? endObject : entity;

                EntityAndPendingNotifications<BE, Relationship> res = Util.createAssociation(tx, from,
                        name, to, null);

                tx.getPreCommit().addNotifications(res);
            } catch (ElementNotFoundException e) {
                throw new EntityNotFoundException(Query.filters(Query.to(end)));
            }
        }));
    }
}
