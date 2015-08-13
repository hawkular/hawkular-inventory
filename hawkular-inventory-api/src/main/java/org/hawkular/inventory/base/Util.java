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

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.InventoryException;
import org.hawkular.inventory.api.Log;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Marker;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.EntityAndPendingNotifications.Notification;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
final class Util {

    private Util() {

    }

    public static <R> R runInTransaction(TraversalContext<?, ?> context, boolean readOnly,
            PotentiallyCommittingPayload<R> payload) {

        int failures = 0;
        Exception lastException = null;

        int maxFailures = context.getTransactionRetriesCount();

        while (failures++ < maxFailures) {
            try {
                InventoryBackend.Transaction t = context.backend.startTransaction(!readOnly);
                try {
                    R ret = payload.run(t);
                    if (readOnly) {
                        context.backend.commit(t);
                    }

                    return ret;
                } catch (Throwable e) {
                    context.backend.rollback(t);
                    throw e;
                }
            } catch (CommitFailureException e) {
                //if the backend fails the commit, we can retry
                Log.LOGGER.debugf(e, "Commit attempt %d/%d failed: %s", failures, maxFailures, e.getMessage());

                lastException = e;
            }
        }
        throw new TransactionFailureException(lastException, failures);
    }

    @FunctionalInterface
    public interface PotentiallyCommittingPayload<R> {
        R run(InventoryBackend.Transaction t) throws CommitFailureException;
    }

    public static <BE> BE getSingle(InventoryBackend<BE> backend, Query query,
            Class<? extends Entity<?, ?>> entityType) {
        Page<BE> results = backend.query(query, Pager.single());

        if (results.isEmpty()) {
            throw new EntityNotFoundException(entityType, Query.filters(query));
        }

        return results.get(0);
    }

    public static <BE> EntityAndPendingNotifications<Relationship> createAssociation(TraversalContext<BE, ?> context,
            Query sourceQuery, Class<? extends Entity<?, ?>> sourceType, String relationship, BE target) {

        return runInTransaction(context, false, (t) -> {
            BE source = getSingle(context.backend, sourceQuery, sourceType);

            if (context.backend.hasRelationship(source, target, relationship)) {
                throw new RelationAlreadyExistsException(relationship, Query.filters(sourceQuery));
            }

            RelationshipRules.checkCreate(context.backend, source, Relationships.Direction.outgoing, relationship,
                    target);
            BE relationshipObject = context.backend.relate(source, target, relationship, null);

            context.backend.commit(t);

            Relationship ret = context.backend.convert(relationshipObject, Relationship.class);

            return new EntityAndPendingNotifications<>(ret, new Notification<>(ret, ret, created()));
        });
    }

    public static <BE> EntityAndPendingNotifications<Relationship> deleteAssociation(TraversalContext<BE, ?> context,
            Query sourceQuery, Class<? extends Entity<?, ?>> sourceType, String relationship, BE target) {

        InventoryBackend<BE> backend = context.backend;
        return runInTransaction(context, false, (t) -> {
            BE source = getSingle(backend, sourceQuery, sourceType);

            BE relationshipObject;

            try {
                relationshipObject = backend.getRelationship(source, target, relationship);
            } catch (ElementNotFoundException e) {
                throw new RelationNotFoundException(sourceType, relationship, Query.filters(sourceQuery),
                        null, null);
            }

            RelationshipRules.checkDelete(backend, source, Relationships.Direction.outgoing, relationship,
                    target);

            Relationship ret = backend.convert(relationshipObject, Relationship.class);

            backend.delete(relationshipObject);

            backend.commit(t);

            return new EntityAndPendingNotifications<>(ret, new Notification<>(ret, ret, deleted()));
        });
    }

    public static <BE> Relationship getAssociation(TraversalContext<BE, ?> context, Query sourceQuery,
            Class<? extends Entity<?, ?>> sourceType, Query targetQuery, Class<? extends Entity<?, ?>> targetType,
            String rel) {

        InventoryBackend<BE> backend = context.backend;

        return runInTransaction(context, true, (t) -> {
            BE source = getSingle(backend, sourceQuery, sourceType);
            BE target = getSingle(backend, targetQuery, targetType);

            BE relationship;
            try {
                relationship = backend.getRelationship(source, target, rel);
            } catch (ElementNotFoundException e) {
                throw new RelationNotFoundException(sourceType, rel, Query.filters(sourceQuery),
                        null, null);
            }

            return backend.convert(relationship, Relationship.class);
        });
    }

    @SuppressWarnings("unchecked")
    public static Query queryTo(TraversalContext<?, ?> context, Path path) {
        if (path instanceof CanonicalPath) {
            return Query.to((CanonicalPath) path);
        } else {
            Query.SymmetricExtender extender = context.sourcePath.extend().path();

            extender.with(With.relativePath(null, (RelativePath) path));

            return extender.get();
        }
    }

    @SuppressWarnings("unchecked")
    public static Query extendTo(TraversalContext<?, ?> context, Path path) {
        if (path instanceof CanonicalPath) {
            return context.select().with(With.path((CanonicalPath) path)).get();
        } else {
            Marker marker = Marker.next();
            return context.sourcePath.extend().path().with(marker).with(context.selectCandidates)
                    .with(With.relativePath(marker.getLabel(), (RelativePath) path)).get();
        }
    }

    @SuppressWarnings("unchecked")
    public static <BE, E extends AbstractElement<?, U>, U extends AbstractElement.Update> void update(
            TraversalContext<BE, E> context, Query entityQuery, U update, BiConsumer<BE, U> preUpdateCheck) {

        BE updated = runInTransaction(context, false, (t) -> {
            Page<BE> entities = context.backend.query(entityQuery, Pager.single());
            if (entities.isEmpty()) {
                if (update instanceof Relationship.Update) {
                    throw new RelationNotFoundException((String) null, Query.filters(entityQuery));
                } else {
                    throw new EntityNotFoundException((Class<? extends Entity<?, ?>>) context.entityClass,
                            Query.filters(entityQuery));
                }
            }

            BE toUpdate = entities.get(0);

            if (preUpdateCheck != null) {
                preUpdateCheck.accept(toUpdate, update);
            }

            context.backend.update(toUpdate, update);
            context.backend.commit(t);
            return toUpdate;
        });

        E entity = context.backend.convert(updated, context.entityClass);
        context.notify(entity, new Action.Update<>(entity, update), Action.updated());
    }

    @SuppressWarnings("unchecked")
    public static <BE, E extends AbstractElement<?, ?>> void delete(TraversalContext<BE, E> context,
            Query entityQuery, Consumer<BE> cleanupFunction) {

        runInTransaction(context, false, (transaction) -> {
            Page<BE> entities = context.backend.query(entityQuery, Pager.single());
            if (entities.isEmpty()) {
                if (context.entityClass.equals(Relationship.class)) {
                    throw new RelationNotFoundException((String) null, Query.filters(entityQuery));
                } else {
                    throw new EntityNotFoundException((Class<? extends Entity<?, ?>>) context.entityClass,
                            Query.filters(entityQuery));
                }
            } else if (entities.size() > 1) {
                throw new InventoryException("Ambiguous delete query. More than 1 results found for query "
                        + entityQuery);
            }

            BE toDelete = entities.get(0);

            if (cleanupFunction != null) {
                cleanupFunction.accept(toDelete);
            }

            Set<BE> verticesToDeleteThatDefineSomething = new HashSet<>();

            Set<BE> deleted = new HashSet<>();
            Set<BE> deletedRels = new HashSet<>();
            Set<?> deletedEntities;
            Set<Relationship> deletedRelationships;

            context.backend.getTransitiveClosureOver(toDelete, outgoing, contains.name()).forEachRemaining((e) -> {
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
            deletedEntities = deleted.stream().filter((o) -> isRepresentableInAPI(context, o))
                    .map((o) -> context.backend.convert(o, context.backend.extractType(o)))
                    .collect(Collectors.<Object>toSet());

            deletedRelationships = deletedRels.stream().filter((o) -> isRepresentableInAPI(context, o))
                    .map((o) -> context.backend.convert(o, Relationship.class))
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

            for (Object e : deletedEntities) {
                context.notify(e, deleted());
            }

            return null;
        });
    }

    /**
     * If the provided path is canonical, it is prefixed with the {@code canonicalPrefix} and returned. If the provided
     * path is relative, it is resolved against the {@code relativeOrigin} and then converted to a canonical path.
     *
     * <p>The path can be partially untyped.
     *
     * @param path              the string representation of a path (either canonical or relative)
     * @param canonicalPrefix   the prefix to apply to a canonical path
     * @param relativeOrigin    origin to resolve a relative path against
     * @param intendedFinalType the intended type of the final segment of the path
     * @return the canonical path represented by the provided path string
     * @see Path#fromPartiallyUntypedString(String, CanonicalPath, CanonicalPath, Class)
     */
    public static CanonicalPath canonicalize(String path, CanonicalPath canonicalPrefix, CanonicalPath relativeOrigin,
            Class<?> intendedFinalType) {

        Path p = Path.fromPartiallyUntypedString(path, canonicalPrefix, relativeOrigin, intendedFinalType);

        if (p instanceof RelativePath) {
            return ((RelativePath) p).applyTo(relativeOrigin);
        } else {
            return p.toCanonicalPath();
        }
    }

    /**
     * Certain constructs in backend are not representable in API - such as the
     * {@link org.hawkular.inventory.api.Relationships.WellKnown#hasData} relationship.
     *
     * @param context the context using which to access backend
     * @param entity  the entity to decide on
     * @param <BE>    the type of the backend entity
     * @return true if the entity can be represented in API results, false otherwise
     */
    public static <BE> boolean isRepresentableInAPI(TraversalContext<BE, ?> context, BE entity) {
        if (Relationship.class.equals(context.backend.extractType(entity))) {
            if (Relationships.WellKnown.hasData.name().equals(context.backend.extractRelationshipName(entity))) {
                return false;
            }
        }

        return true;
    }
}
