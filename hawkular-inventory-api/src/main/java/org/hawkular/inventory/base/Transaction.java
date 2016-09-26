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

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Hashes;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.Discriminator;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.EntityStateChange;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;

/**
 * A transaction is essentially a "window" into the backend for transactional payloads executed using the
 * {@link TransactionPayload}s.
 * <p>
 * It is almost the same as the {@link InventoryBackend} interface, but doesn't contain the methods for direct
 * transaction manipulation. This is because by default, transactional payloads don't commit - the commit is handled
 * automatically by the base impl. Also, nested transactions are not supported so the
 * {@link InventoryBackend#startTransaction()} is not exposed through this interface.
 *
 * @author Lukas Krejci
 * @since 0.13.0
 */
public interface Transaction<E> {
    /**
     * This is very dangerous, do NOT obtain the raw inventory backend unless you have utterly serious reasons to do so.
     * All the backend methods are delegated to from this transaction, unless they are transaction-handling related.
     * <p>
     * You should not try to do your own transaction handling. If you do, the code should change to accomodate for your
     * usecase in a non-exceptional way.
     *
     * @return the inventory backend that this transaction delegates to
     */
    InventoryBackend<E> directAccess();

    /**
     * By default this does nothing, but is used during execution of a transactio frame to keep track of the
     * individual payloads executed as part of the transaction frame.
     * <p>
     * These are then used in case of commit failure for a re-play.
     *
     * @param committedPayload a payload that has just been committed.
     */
    void registerCommittedPayload(TransactionPayload.Committing<?, E> committedPayload);

    PreCommit<E> getPreCommit();

    <T> T convert(Discriminator discriminator, E entityRepresentation, Class<T> entityType);

    void markDeleted(Discriminator discriminator, E entity);

    void deleteStructuredData(E dataRepresentation);

    E descendToData(Discriminator discriminator, E dataEntityRepresentation, RelativePath dataPath);

    void eradicate(E entityRepresentation);

    CanonicalPath extractCanonicalPath(E entityRepresentation);

    String extractId(E entityRepresentation);

    String extractIdentityHash(Discriminator discriminator, E entityRepresentation);

    String extractContentHash(Discriminator discriminator, E entityRepresentation);

    String extractSyncHash(Discriminator discriminator, E entityRepresentation);

    String extractRelationshipName(E relationship);

    Class<?> extractType(E entityRepresentation);

    E find(Discriminator discriminator, CanonicalPath element) throws ElementNotFoundException;

    InputStream getGraphSON(Discriminator discriminator, String tenantId);

    E getRelationship(Discriminator discriminator, E source, E target, String relationshipName) throws ElementNotFoundException;

    Set<E> getRelationships(Discriminator discriminator, E entity, Relationships.Direction direction,
                            String... names);

    E getRelationshipSource(Discriminator discriminator, E relationship);

    E getRelationshipTarget(Discriminator discriminator, E relationship);

    <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(
            Discriminator discriminator, CanonicalPath startingPoint,
            Relationships.Direction direction, Class<T> clazz,
            String... relationshipNames);

    Iterator<E> getTransitiveClosureOver(Discriminator discriminator, E startingPoint,
                                         Relationships.Direction direction,
                                         String... relationshipNames);

    boolean hasRelationship(Discriminator discriminator, E entity, Relationships.Direction direction,
                            String relationshipName);

    boolean hasRelationship(Discriminator discriminator, E source, E target, String relationshipName);

    boolean isBackendInternal(E element);

    boolean isUniqueIndexSupported();

    E persist(Discriminator discriminator, CanonicalPath path,
              Blueprint blueprint);

    E persist(StructuredData structuredData);

    Page<E> query(Discriminator discriminator, Query query,
                  Pager pager);

    <T> Page<T> query(Discriminator discriminator, Query query,
                      Pager pager,
                      Function<E, T> conversion,
                      Function<T, Boolean> filter);

    E querySingle(Discriminator discriminator, Query query);

    E relate(Discriminator discriminator, E sourceEntity, E targetEntity, String name,
             Map<String, Object> properties);

    Page<E> traverse(Discriminator discriminator, E startingPoint, Query query,
                     Pager pager);

    E traverseToSingle(Discriminator discriminator, E startingPoint, Query query);

    void update(Discriminator discriminator, E entity, AbstractElement.Update update);

    void updateHashes(Discriminator discriminator, E entity, Hashes hashes);

    /**
     * Checks the exception thrown during the commit and returns true if the backend requires explicit rollback after
     * such failure occured or false if the failure caused the transaction to close itself automatically.
     *
     * @param t the exception thrown during commit
     * @return true to explictly roll back or false if the exception already caused the transaction to close
     */
    default boolean requiresRollbackAfterFailure(Throwable t) {
        return true;
    }

    <T extends Entity<?, U>, U extends Entity.Update>
    List<EntityStateChange<T>> getHistory(E entity, Class<T> entityType, Instant from, Instant to);

    interface PreCommit<E> {
        /**
         * This is always to be called AFTER a transaction is committed and therefore after {@link #getActions()} is
         * called.
         * @return the notifications to send out - these can be different from what was originally requested by the base
         * code
         */
        List<EntityAndPendingNotifications<E, ?>> getFinalNotifications();

        /**
         * Initializes this pre-commit using the inventory and/or the backend.
         *
         * @param inventory the inventory to use, it is bound to the provided transaction
         * @param tx the transaction for which this pre-commit is defined
         * @param txStart the time the transaction has started
         */
        void initialize(Inventory inventory, Transaction<E> tx, Instant txStart);

        /**
         * Resets all the internal structures as to the initialized state.
         */
        void reset();

        /**
         * Adds an explicit action to be run prior to commit.
         *
         * @param action the action
         */
        void addAction(Consumer<Transaction<E>> action);

        /**
         * @return the list of actions to run prior to commit. This is a super set of the actions explicitly added by
         * the {@link #addAction(Consumer)} method.
         */
        List<Consumer<Transaction<E>>> getActions();

        /**
         * Adds a notification to be sent out after the commit is made. This is analyzed and can result in new
         * actions being run prior to commit.
         *
         * @param element the changed element and its notifications.
         */
        void addNotifications(EntityAndPendingNotifications<E, ?> element);

        /**
         * Similar to {@link #addNotifications(EntityAndPendingNotifications)} but the provided element and its
         * notifications are not subject to further analysis.
         * <p>
         * This is used only in special circumstances, like passing notifications from one pre-commit to another
         * while handling "silent" transactions that are used in {@link org.hawkular.inventory.api.TransactionFrame}s
         * or when inventory is told to {@link BaseInventory#keepTransaction(Transaction)}.
         *
         * @param element the changed element and its notifications
         */
        void addProcessedNotifications(EntityAndPendingNotifications<E, ?> element);

        class Simple<E> implements PreCommit<E> {
            private List<EntityAndPendingNotifications<E, ?>> notifs = new ArrayList<>();
            private List<Consumer<Transaction<E>>> actions = new ArrayList<>();
            protected Transaction<E> transaction;
            protected Inventory inventory;
            protected Instant txStart;

            @Override public void initialize(Inventory inventory, Transaction<E> tx, Instant txStart) {
                this.inventory = inventory;
                this.transaction = tx;
                this.txStart = txStart;
            }

            @Override public void reset() {
                notifs.clear();
                actions.clear();
            }

            @Override public void addNotifications(EntityAndPendingNotifications<E, ?> element) {
                notifs.add(element);
            }

            @Override public void addProcessedNotifications(EntityAndPendingNotifications<E, ?> element) {
                notifs.add(element);
            }

            @Override public void addAction(Consumer<Transaction<E>> action) {
                actions.add(action);
            }

            @Override public List<Consumer<Transaction<E>>> getActions() {
                return actions;
            }

            @Override public List<EntityAndPendingNotifications<E, ?>> getFinalNotifications() {
                return notifs;
            }
        }
    }

    /**
     * This is to be used only in those extreme situations when one needs a manual control over when a payload is
     * committed.
     *
     * @param <E>
     */
    class Committable<E> extends DelegatingTransaction<E> implements Transaction<E> {

        /**
         * Converts the provided transaction to a committable one.
         * <p>
         * <b>WARNING:</b> DO NOT USE THE PROVIDED TRANSACTION FOR ANYTHING AFTER THIS CALL.
         *
         * @param tx the transaction
         * @param <E> the type of the elements that the backend deals with
         * @return a commitable transaction to be used instead of the provided one from this point on.
         */
        public static <E> Committable<E> from(Transaction<E> tx) {
            return new Committable<>(tx);
        }

        protected Committable(Transaction<E> tx) {
            super(tx);
        }

        public void commit() throws CommitFailureException {
            getPreCommit().getActions().forEach(a -> a.accept(this));
            directAccess().commit();
        }

        public void rollback() {
            directAccess().rollback();
        }
    }
}
