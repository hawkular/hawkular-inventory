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
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.Relationships.Direction.both;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.InventoryException;
import org.hawkular.inventory.api.Log;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Marker;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.Discriminator;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InconsistenStateException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
final class Util {

    private static final Random rand = new Random();

    private Util() {

    }

    public static <R, BE> R inTx(TraversalContext<BE, ?> context, TransactionPayload<R, BE> payload) {
        Transaction<BE> transaction = context.startTransaction();
        Log.LOGGER.trace("Starting transaction: " + transaction);
        int maxFailures = context.getTransactionRetriesCount();
        return onFailureRetry(context, transaction, payload, payload, maxFailures);
    }

    public static <R, BE> R inCommittableTx(TraversalContext<BE, ?> context,
                                            TransactionPayload.Committing<R, BE> payload) {

        Transaction.Committable<BE> tx = Transaction.Committable.from(context.startTransaction());
        Log.LOGGER.trace("Starting self-committing transaction: " + tx);
        int maxFailures = context.getTransactionRetriesCount();
        return onFailureRetry(context::startTransaction, tx, payload, payload, maxFailures);
    }

    public static <R, BE> R onFailureRetry(TraversalContext<BE, ?> ctx, Transaction<BE> tx,
                                           TransactionPayload<R, BE> firstPayload,
                                           TransactionPayload<R, BE> succeedingPayload, int maxFailures) {

        return onFailureRetry(ctx::startTransaction, Transaction.Committable.from(tx),
                TransactionPayload.Committing.committing(firstPayload),
                TransactionPayload.Committing.committing(succeedingPayload), maxFailures);
    }

    public static <R, BE> R onFailureRetry(Function<Transaction.PreCommit<BE>, Transaction<BE>> txCtor,
                                           Transaction.Committable<BE> tx,
                                           TransactionPayload.Committing<R, BE> firstPayload,
                                           TransactionPayload.Committing<R, BE> succeedingPayload,
                                           int maxFailures) {
        int failures = 0;
        Exception lastException;

        //this could be configurable, but let's just start with the hardcoded 300ms + a random bit
        //as the first retry wait time.
        int waitTime = 300 + rand.nextInt(150);

        do {
            try {
                try {
                    R ret;
                    if (failures == 0) {
                        ret = firstPayload.run(tx);
                        tx.registerCommittedPayload(firstPayload);
                    } else {
                        tx.getPreCommit().reset();
                        tx = Transaction.Committable.from(txCtor.apply(tx.getPreCommit()));

                        ret = succeedingPayload.run(tx);
                        tx.registerCommittedPayload(succeedingPayload);
                    }

                    return ret;
                } catch (Throwable t) {
                    Log.LOGGER.dTransactionFailed(t.getMessage());
                    if (t instanceof InconsistenStateException || tx.requiresRollbackAfterFailure(t)) {
                        tx.rollback();
                    }
                    throw t;
                }
            } catch (CommitFailureException | InconsistenStateException e) {
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
                    waitTime = waitTime * 2 + rand.nextInt(waitTime / 2);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                //an exception in the payload itself, not caused by a failed commit. We don't retry those...
                throw new InventoryException("Transaction payload failed.", e);
            }
        } while (failures < maxFailures);

        throw new TransactionFailureException(lastException, failures);
    }

    public static <BE> BE getSingle(Discriminator discriminator, Transaction<BE> backend, Query query,
                                    SegmentType entityType) {
        BE result = backend.querySingle(discriminator, query);
        if (result == null) {
            throw new EntityNotFoundException(entityType, Query.filters(query));
        }

        return result;
    }

    public static <BE> EntityAndPendingNotifications<BE, Relationship>
    createAssociation(Discriminator discriminator, Transaction<BE> tx, BE source, String relationship, BE target, Map<String, Object> properties) {

        if (tx.hasRelationship(discriminator, source, target, relationship)) {
            throw new RelationAlreadyExistsException(relationship, Query.filters(Query.to(tx.extractCanonicalPath
                    (source))));
        }

        RelationshipRules.checkCreate(discriminator, tx, source, Relationships.Direction.outgoing, relationship,
                target);
        BE relationshipObject = tx.relate(discriminator, source, target, relationship, properties);

        Relationship ret = tx.convert(discriminator, relationshipObject, Relationship.class);

        return new EntityAndPendingNotifications<>(relationshipObject, ret,
                new Notification<>(ret, ret, created()));
    }

    public static <BE> EntityAndPendingNotifications<BE, Relationship>
    deleteAssociation(Discriminator discriminator, Transaction<BE> tx, Query sourceQuery, SegmentType sourceType,
                      String relationship, BE target) {

        BE source = getSingle(discriminator, tx, sourceQuery, sourceType);

        BE relationshipObject;

        try {
            relationshipObject = tx.getRelationship(discriminator, source, target, relationship);
        } catch (ElementNotFoundException e) {
            throw new RelationNotFoundException(sourceType, relationship, Query.filters(sourceQuery),
                    null, e);
        }

        RelationshipRules.checkDelete(discriminator, tx, source, Relationships.Direction.outgoing, relationship,
                target);

        Relationship ret = tx.convert(discriminator, relationshipObject, Relationship.class);

        tx.markDeleted(discriminator, relationshipObject);

        return new EntityAndPendingNotifications<>(relationshipObject, ret,
                new Notification<>(ret, ret, deleted()));
    }

    public static <BE> Relationship getAssociation(Discriminator discriminator, Transaction<BE> tx, Query sourceQuery,
                                                   SegmentType sourceType, Query targetQuery,
                                                   SegmentType targetType,
                                                   String rel) {

        BE source = getSingle(discriminator, tx, sourceQuery, sourceType);
        BE target = getSingle(discriminator, tx, targetQuery, targetType);

        BE relationship;
        try {
            relationship = tx.getRelationship(discriminator, source, target, rel);
        } catch (ElementNotFoundException e) {
            throw new RelationNotFoundException(sourceType, rel, Query.filters(sourceQuery),
                    null, null);
        }

        return tx.convert(discriminator, relationship, Relationship.class);
    }

    @SuppressWarnings("unchecked")
    public static Query queryTo(Query sourcePath, Path path) {
        if (path instanceof CanonicalPath) {
            return Query.to((CanonicalPath) path);
        } else {
            Query.SymmetricExtender extender = sourcePath.extend().path();

            extender.with(With.relativePath(null, (RelativePath) path));

            return extender.get();
        }
    }

    /**
     * Tries to find an element at given path.
     *
     * @param tx current transaction in the traversal (if path is canonical, this is not used)
     * @param path    either canonical or relative path of the element to find
     * @return the element
     * @throws ElementNotFoundException if the element is not found
     */
    @SuppressWarnings("unchecked")
    public static <BE> BE find(Discriminator discriminator, Transaction<BE> tx, Query sourcePath, Path path) throws
            EntityNotFoundException {
        BE element;
        if (path.isCanonical()) {
            try {
                element = tx.find(discriminator, path.toCanonicalPath());
            } catch (ElementNotFoundException e) {
                throw new EntityNotFoundException("Entity not found on path: " + path);
            }
        } else {
            Query query = queryTo(sourcePath, path);
            element = getSingle(discriminator, tx, query, null);
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
            Discriminator discriminator, Class<E> entityClass,
            Transaction<BE> tx, Query entityQuery, U update,
            TransactionParticipant<BE, U> preUpdateCheck,
            BiConsumer<BE, Transaction<BE>> postUpdateCheck) {

        BE entity = tx.querySingle(discriminator, entityQuery);
        if (entity == null) {
            if (update instanceof Relationship.Update) {
                throw new RelationNotFoundException((String) null, Query.filters(entityQuery));
            } else {
                throw new EntityNotFoundException(entityClass, Query.filters(entityQuery));
            }
        }

        if (preUpdateCheck != null) {
            preUpdateCheck.execute(entity, update, tx);
        }

        E orig = tx.convert(discriminator, entity, entityClass);

        tx.update(discriminator, entity, update);

        if (postUpdateCheck != null) {
            postUpdateCheck.accept(entity, tx);
        }

        E updated = tx.convert(discriminator, entity, entityClass);

        tx.getPreCommit().addNotifications(new EntityAndPendingNotifications<>(entity, updated,
                new Action.Update<>(orig, update), Action.updated()));
    }

    @SuppressWarnings("unchecked")
    public static <BE, E extends AbstractElement<?, ?>>
    void delete(Discriminator discriminator, Class<E> entityClass, Transaction<BE> tx, Query entityQuery,
                BiConsumer<BE, Transaction<BE>> cleanupFunction,
                BiConsumer<BE, Transaction<BE>> postDelete, boolean eradicate) {

        BE entity = tx.querySingle(discriminator, entityQuery);
        if (entity == null) {
            if (entityClass.equals(Relationship.class)) {
                throw new RelationNotFoundException((String) null, Query.filters(entityQuery));
            } else {
                throw new EntityNotFoundException(entityClass, Query.filters(entityQuery));
            }
        }

        if (cleanupFunction != null) {
            cleanupFunction.accept(entity, tx);
        }

        Set<BE> verticesToDeleteThatDefineSomething = new HashSet<>();
        Set<BE> dataToBeDeleted = new HashSet<>();

        Set<BE> deleted = new HashSet<>();
        Set<BE> deletedRels = new HashSet<>();

        Consumer<BE> categorizer = (e) -> {
            if (tx.hasRelationship(discriminator, e, outgoing, defines.name())) {
                verticesToDeleteThatDefineSomething.add(e);
            } else {
                deleted.add(e);
            }
            //not only the entity, but also its relationships are going to disappear
            deletedRels.addAll(tx.getRelationships(discriminator, e, both));

            tx.getRelationships(discriminator, e, outgoing, hasData.name()).forEach(rel -> {
                dataToBeDeleted.add(tx.getRelationshipTarget(discriminator, rel));
            });
        };

        categorizer.accept(entity);
        tx.getTransitiveClosureOver(discriminator, entity, outgoing, contains.name())
                .forEachRemaining(categorizer::accept);

        //we've gathered all entities to be deleted. Now record the notifications to be sent out when the transaction
        //commits.
        Consumer<BE> addNotification = be -> {
            AbstractElement<?, ?> e = tx.convert(discriminator, be, (Class<AbstractElement<?, ?>>) tx.extractType(be));
            tx.getPreCommit().addNotifications(new EntityAndPendingNotifications<>(be, e, deleted()));
        };

        deleted.stream().filter(o -> isRepresentableInAPI(tx, o)).forEach(addNotification);
        verticesToDeleteThatDefineSomething.stream().filter(o -> isRepresentableInAPI(tx, o)).forEach(addNotification);

        deletedRels.stream().filter(o -> isRepresentableInAPI(tx, o)).forEach(addNotification);

        //k, now we can delete them all... the order is not important anymore
        for (BE e : deleted) {
            if (eradicate) {
                tx.eradicate(e);
            } else {
                tx.markDeleted(discriminator, e);
            }
        }

        for (BE e : verticesToDeleteThatDefineSomething) {
            if (tx.hasRelationship(discriminator, e, outgoing, defines.name())) {
                //we avoid the convert() function here because it assumes the containing entities of the passed in
                //entity exist. This might not be true during the delete because the transitive closure "walks" the
                //entities from the "top" down the containment chain and the entities are immediately deleted.
                CanonicalPath rootPath = tx.extractCanonicalPath(entity);
                CanonicalPath definingPath = tx.extractCanonicalPath(e);

                throw new IllegalArgumentException("Could not delete entity '" + rootPath + "'. The entity '" +
                        definingPath + "', which it (indirectly) contains, acts as a definition for some " +
                        "entities that are not deleted along with it, which would leave them without a " +
                        "definition. This is illegal.");
            } else {
                if (eradicate) {
                    tx.eradicate(e);
                } else {
                    tx.markDeleted(discriminator, e);
                }
            }
        }

        if (eradicate) {
            dataToBeDeleted.forEach(tx::deleteStructuredData);
        }

        if (postDelete != null) {
            postDelete.accept(entity, tx);
        }
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
     * @see Path#fromPartiallyUntypedString(String, CanonicalPath, CanonicalPath, SegmentType)
     */
    public static CanonicalPath canonicalize(String path, CanonicalPath canonicalPrefix, CanonicalPath relativeOrigin,
                                             SegmentType intendedFinalType) {

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
     * @param tx the context using which to access backend
     * @param entity  the entity to decide on
     * @param <BE>    the type of the backend entity
     * @return true if the entity can be represented in API results, false otherwise
     */
    public static <BE> boolean isRepresentableInAPI(Transaction<BE> tx, BE entity) {
        if (tx.isBackendInternal(entity)) {
            return false;
        }

        if (Relationship.class.equals(tx.extractType(entity))) {
            if (Relationships.WellKnown.hasData.name().equals(tx.extractRelationshipName(entity))) {
                return false;
            }
        }

        return true;
    }

    public interface TransactionParticipant<BE, E> {
        void execute(BE entityRepresentation, E entity, Transaction<BE> transaction);
    }
}
