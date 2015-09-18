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
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.EntityNotFoundException;
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
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
final class Util {

    private static final Random rand = new Random();

    private Util() {

    }

    public static <R> R commitOrRetry(InventoryBackend.Transaction transaction, InventoryBackend<?> backend,
                                      R firstCommitSuccessReturnValue,
                                      PotentiallyCommittingPayload<R> payload, int maxRetries) {

        return commitOrRetry(transaction, backend, (t) -> firstCommitSuccessReturnValue, payload, maxRetries, false);
    }

    public static <R> R runInTransaction(TraversalContext<?, ?> context, boolean readOnly,
                                         PotentiallyCommittingPayload<R> payload) {
        InventoryBackend.Transaction transaction = context.backend.startTransaction(!readOnly);
        int maxFailures = context.getTransactionRetriesCount();
        return commitOrRetry(transaction, context.backend, payload, payload, maxFailures, true);
    }

    private static <R> R commitOrRetry(InventoryBackend.Transaction transaction, InventoryBackend<?> backend,
                                       PotentiallyCommittingPayload<R> firstPayload,
                                       PotentiallyCommittingPayload<R> succeedingPayload, int maxFailures,
                                       boolean commitOnlyReadonly) {
        int failures = 0;
        Exception lastException;

        //this could be configurable, but let's just start with the hardcoded 100ms as the first retry wait time.
        int waitTime = 100;

        do {
            try {
                try {
                    R ret;
                    if (failures == 0) {
                        ret = transaction.execute(firstPayload);
                    } else {
                        transaction = backend.startTransaction(transaction.isMutating());
                        ret = transaction.execute(succeedingPayload);
                    }

                    if (!(commitOnlyReadonly && transaction.isMutating())) {
                        backend.commit(transaction);
                    }

                    return ret;
                } catch (Throwable t) {
                    Log.LOGGER.dTransactionFailed(t.getMessage());
                    backend.rollback(transaction);
                    throw t;
                }
            } catch (CommitFailureException e) {
                failures++;

                //if the backend fails the commit, we can retry
                Log.LOGGER.debugf(e, "Commit attempt %d/%d failed. Will wait for %d ms before retrying." +
                        " The failure message was: %s", failures, maxFailures, waitTime, e.getMessage());

                lastException = e;

                if (failures < maxFailures) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Log.LOGGER.wInterruptedWhileWaitingForTransactionRetry();
                        //reset the interruption flag
                        Thread.currentThread().interrupt();
                        //and jump out of the loop to throw the transaction failure exception
                        break;
                    }

                    //double the wait time for the next attempt - the assumption is that if the competing transaction
                    //takes a long time to complete, it probably is going to be really long.
                    //We randomize the value a little bit so that competing transactions started at roughly same time
                    //don't knock each other out easily.
                    waitTime = waitTime * 2 + rand.nextInt(waitTime / 10);
                }
            }
        } while (failures < maxFailures);

        throw new TransactionFailureException(lastException, failures);
    }

    public static <BE> BE getSingle(InventoryBackend<BE> backend, Query query,
                                    Class<? extends Entity<?, ?>> entityType) {
        BE result = backend.querySingle(query);
        if (result == null) {
            throw new EntityNotFoundException(entityType, Query.filters(query));
        }

        return result;
    }

    public static <BE> EntityAndPendingNotifications<Relationship>
    createAssociation(TraversalContext<BE, ?> context, Query sourceQuery, Class<? extends Entity<?, ?>> sourceType,
                      String relationship, BE target) {

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

    public static <BE> EntityAndPendingNotifications<Relationship>
    createAssociationNoTransaction(TraversalContext<BE, ?> context, BE source, String relationship, BE target) {
        if (context.backend.hasRelationship(source, target, relationship)) {
            throw new RelationAlreadyExistsException(relationship, Query.filters(Query.to(context.backend
                    .extractCanonicalPath(source))));
        }

        RelationshipRules.checkCreate(context.backend, source, Relationships.Direction.outgoing, relationship,
                target);
        BE relationshipObject = context.backend.relate(source, target, relationship, null);

        Relationship ret = context.backend.convert(relationshipObject, Relationship.class);

        return new EntityAndPendingNotifications<>(ret, new Notification<>(ret, ret, created()));
    }

    public static <BE> EntityAndPendingNotifications<Relationship>
    deleteAssociation(TraversalContext<BE, ?> context, Query sourceQuery, Class<? extends Entity<?, ?>> sourceType,
                      String relationship, BE target) {

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
                                                   Class<? extends Entity<?, ?>> sourceType, Query targetQuery,
                                                   Class<? extends Entity<?, ?>> targetType,
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

    /**
     * Tries to find an element at given path.
     *
     * @param context current context in the traversal (if path is canonical, this is not used)
     * @param path    either canonical or relative path of the element to find
     * @return the element
     * @throws ElementNotFoundException if the element is not found
     */
    @SuppressWarnings("unchecked")
    public static <BE> BE find(TraversalContext<BE, ?> context, Path path) throws EntityNotFoundException {
        BE element;
        if (path.isCanonical()) {
            try {
                element = context.backend.find(path.toCanonicalPath());
            } catch (ElementNotFoundException e) {
                throw new EntityNotFoundException("Entity not found on path: " + path);
            }
        } else {
            Query query = queryTo(context, path);
            element = getSingle(context.backend, query, null);
        }
        return element;
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
            BE entity = context.backend.querySingle(entityQuery);
            if (entity == null) {
                if (update instanceof Relationship.Update) {
                    throw new RelationNotFoundException((String) null, Query.filters(entityQuery));
                } else {
                    throw new EntityNotFoundException((Class<? extends Entity<?, ?>>) context.entityClass,
                            Query.filters(entityQuery));
                }
            }

            if (preUpdateCheck != null) {
                preUpdateCheck.accept(entity, update);
            }

            context.backend.update(entity, update);
            context.backend.commit(t);
            return entity;
        });

        E entity = context.backend.convert(updated, context.entityClass);
        context.notify(entity, new Action.Update<>(entity, update), Action.updated());
    }

    @SuppressWarnings("unchecked")
    public static <BE, E extends AbstractElement<?, ?>> void delete(TraversalContext<BE, E> context,
                                                                    Query entityQuery, Consumer<BE> cleanupFunction) {

        runInTransaction(context, false, (transaction) -> {
            BE entity = context.backend.querySingle(entityQuery);
            if (entity == null) {
                if (context.entityClass.equals(Relationship.class)) {
                    throw new RelationNotFoundException((String) null, Query.filters(entityQuery));
                } else {
                    throw new EntityNotFoundException((Class<? extends Entity<?, ?>>) context.entityClass,
                            Query.filters(entityQuery));
                }
            }

            if (cleanupFunction != null) {
                cleanupFunction.accept(entity);
            }

            Set<BE> verticesToDeleteThatDefineSomething = new HashSet<>();

            Set<BE> deleted = new HashSet<>();
            Set<BE> deletedRels = new HashSet<>();
            Set<?> deletedEntities;
            Set<Relationship> deletedRelationships;

            context.backend.getTransitiveClosureOver(entity, outgoing, contains.name()).forEachRemaining((e) -> {
                if (context.backend.hasRelationship(e, outgoing, defines.name())) {
                    verticesToDeleteThatDefineSomething.add(e);
                } else {
                    deleted.add(e);
                }
                //not only the entity, but also its relationships are going to disappear
                deletedRels.addAll(context.backend.getRelationships(e, both));
            });

            if (context.backend.hasRelationship(entity, outgoing, defines.name())) {
                verticesToDeleteThatDefineSomething.add(entity);
            } else {
                deleted.add(entity);
            }
            deletedRels.addAll(context.backend.getRelationships(entity, both));

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
                    String rootId = context.backend.extractId(entity);
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
     * <p>
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
