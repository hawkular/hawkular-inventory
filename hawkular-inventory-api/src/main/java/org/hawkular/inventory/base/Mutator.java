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

import static java.util.stream.Collectors.toSet;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.Relationships.Direction.both;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.filters.With.id;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.model.AbstractElement;
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
abstract class Mutator<BE, E extends Entity<Blueprint, Update>, Blueprint extends Entity.Blueprint,
        Update extends AbstractElement.Update> extends Traversal<BE, E> {

    protected Mutator(TraversalContext<BE, E> context) {
        super(context);
    }

    /**
     * Extracts the proposed ID from the blueprint or identifies the ID through some other means.
     *
     * @param blueprint the blueprint of the entity to be created
     * @return the ID to be used for the new entity
     */
    protected abstract String getProposedId(Blueprint blueprint);

    /**
     * A helper method to be used in the implementation of the
     * {@link org.hawkular.inventory.api.WriteInterface#create(Entity.Blueprint)} method.
     *
     * <p>The callers may merely use the returned query and construct a new {@code *Single} instance using it.
     *
     * @param blueprint the blueprint of the new entity
     * @return the query to the newly created entity.
     */
    protected final Query doCreate(Blueprint blueprint) {
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

    public final void update(String id, Update update) throws EntityNotFoundException {
        Util.update(context, context.select().with(id(id)).get(), update);
    }

    public final void delete(String id) throws EntityNotFoundException {
        mutating((transaction) -> {
            BE toDelete = checkExists(id);

            Set<BE> verticesToDeleteThatDefineSomething = new HashSet<>();

            Set<BE> deleted = new HashSet<>();
            Set<BE> deletedRels = new HashSet<>();
            Set<AbstractElement<?, ?>> deletedEntities;
            Set<Relationship> deletedRelationships;

            context.backend.getTransitiveClosureOver(toDelete, contains.name(), outgoing).forEachRemaining((e) -> {
                if (context.backend.hasRelationship(e, outgoing, defines.name())) {
                    verticesToDeleteThatDefineSomething.add(e);
                } else {
                    deleted.add(e);
                }
                //not only the entity, but also its relationships are going to disappear
                deletedRels.addAll(context.backend.getRelationships(e, both));
            });

            if (context.backend.hasRelationship(toDelete, outgoing, defines.name())) {
                verticesToDeleteThatDefineSomething.add(toDelete);
            } else {
                deleted.add(toDelete);
            }
            deletedRels.addAll(context.backend.getRelationships(toDelete, both));

            //we've gathered all entities to be deleted. Now convert them all to entities for reporting purposes.
            //We have to do it prior to actually deleting the objects in the backend so that all information and
            //relationships is still available.
            deletedEntities = deleted.stream().map((o) -> context.backend.convert(o, context.backend.extractType(o)))
                    .collect(Collectors.<AbstractElement<?, ?>>toSet());

            deletedRelationships = deletedRels.stream().map((o) -> context.backend.convert(o, Relationship.class))
                    .collect(toSet());

            //k, now we can delete them all... the order is not important anymore
            for (BE e : deleted) {
                context.backend.delete(e);
            }

            for (BE e : verticesToDeleteThatDefineSomething) {
                if (context.backend.hasRelationship(e, outgoing, defines.name())) {
                    //we avoid the convert() function here because it assumes the containing entities of the passed in
                    //entity exist. This might not be true during the delete because the transitive closure "walks" the
                    //entities from the "top" down the containment chain and the entities are immediately deleted.
                    String rootId = context.backend.extractId(toDelete);
                    String definingId = context.backend.extractId(e);
                    String rootType = context.entityClass.getSimpleName();
                    String definingType = context.backend.extractType(e).getSimpleName();

                    String rootEntity = "Entity[id=" + rootId + ", type=" + rootType + "]";
                    String definingEntity = "Entity[id=" + definingId + ", type=" + definingType + "]";

                    throw new IllegalArgumentException("Could not delete entity " + rootEntity + ". The entity " +
                            definingEntity + ", which it (indirectly) contains, acts as a definition for some " +
                            "entities that are not deleted along with it, which would leave them without a " +
                            "definition. This is illegal.");
                } else {
                    context.backend.delete(e);
                }
            }

            context.backend.commit(transaction);

            //report the relationship deletions first - it would be strange to report deletion of a relationship after
            //reporting that an entity on one end of the relationship has been deleted
            for (Relationship r : deletedRelationships) {
                context.notify(r, deleted());
            }

            for (AbstractElement<?, ?> e : deletedEntities) {
                context.notify(e, deleted());
            }

            return null;
        });
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
    protected abstract EntityAndPendingNotifications<E> wireUpNewEntity(BE entity, Blueprint blueprint,
            CanonicalPath parentPath, BE parent);

    private BE checkExists(String id) {
        //sourcePath is "path to the parent"
        //selectCandidates - is the elements possibly matched by this mutator
        //we're given the id to select from these
        Query query = context.select().with(id(id)).get();

        Page<BE> result = context.backend.query(query, Pager.single());

        if (result.isEmpty()) {
            throw new EntityNotFoundException(context.entityClass, Query.filters(query));
        }

        return result.get(0);
    }
}
