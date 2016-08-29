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

import java.time.Instant;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.function.BiConsumer;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Parents;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.SwitchElementType;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.base.spi.Discriminator;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.Path;

import rx.subjects.Subject;

/**
 * Holds the data needed throughout the construction of inventory traversal.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class TraversalContext<BE, E extends AbstractElement<?, ?>> {
    /**
     * The inventory instance we're operating in.
     */
    protected final BaseInventory<BE> inventory;

    /**
     * The query to the "point" right before the entities of interest.
     */
    protected final Query sourcePath;

    /**
     * A query that will select the entities of interest from the {@link #sourcePath}.
     */
    protected final Query selectCandidates;

    /**
     * The inventory backend to be used for querying and persistence.
     */
    private final InventoryBackend<BE> backend;

    /**
     * The type of the entity currently being sought after.
     */
    protected final Class<E> entityClass;

    /**
     * The user provided configuration.
     */
    protected final Configuration configuration;

    /**
     * The previous context, from which this one was created. Can be null.
     */
    protected final TraversalContext<BE, ?> previous;

    private final ObservableContext observableContext;

    private final int transactionRetries;

    private final Instant now;

    /**
     * Optimization for quickly retrieving the entity that has been just created. We have all the data ready at the
     * creation time so it seems silly to load it from backend as soon as the caller requires to see the results of the
     * creation.
     */
    private E createdEntity;

    private final TransactionConstructor<BE> transactionConstructor;

    TraversalContext(BaseInventory<BE> inventory, Instant now, Query sourcePath, Query selectCandidates,
                     InventoryBackend<BE> backend, Class<E> entityClass, Configuration configuration,
                     ObservableContext observableContext, TransactionConstructor<BE> transactionConstructor) {
        this(inventory, now, sourcePath, selectCandidates, backend, entityClass, configuration, observableContext,
                getTransactionRetries(configuration), null, null, transactionConstructor);
    }

    private TraversalContext(BaseInventory<BE> inventory, Instant now, Query sourcePath, Query selectCandidates,
                             InventoryBackend<BE> backend, Class<E> entityClass, Configuration configuration,
                             ObservableContext observableContext, int transactionRetries,
                             TraversalContext<BE, ?> previous, E createdEntity,
                             TransactionConstructor<BE> transactionConstructor) {

        this.inventory = inventory;
        this.now = now;
        this.sourcePath = sourcePath;
        this.selectCandidates = selectCandidates;
        this.backend = backend;
        this.entityClass = entityClass;
        this.configuration = configuration;
        this.observableContext = observableContext;
        this.transactionRetries = transactionRetries;
        this.previous = previous;
        this.createdEntity = createdEntity;
        this.transactionConstructor = transactionConstructor == null
                ? TransactionConstructor.startInBackend() : transactionConstructor;
    }

    private static int getTransactionRetries(Configuration configuration) {
        String retries = configuration.getProperty(BaseInventory.TRANSACTION_RETRIES, "10");
        return Integer.parseInt(retries);
    }

    /**
     * @return the entity previously created on this traversal position or null if no such thing happened.
     */
    public E getCreatedEntity() {
        return createdEntity;
    }

    public void setCreatedEntity(E entity) {
        createdEntity = entity;
    }

    /**
     * If the current position in the traversal defines any select candidates, the new context will have its source path
     * composed by appending the select candidates to the current source path.
     *
     * @return a context builder with the modified source path
     */
    Builder<BE, E> proceed() {
        return new Builder<>(this, hop(), Query.filter(), entityClass);
    }

    /**
     * The new context will have the source path composed by appending current select candidates to the current source
     * path and its select candidates will filter for entities related by the provided relationship to the new sources
     * and will have the provided type.
     *
     * @param over       the relationship with which the select candidates will be related to the entities on the source
     *                   path
     * @param entityType the type of the entities related to the entities on the source path
     * @param <T>        the type of the "target" entities
     * @return a context builder with the modified source path, select candidates and type
     */
    <T extends Entity<?, ?>> Builder<BE, T> proceedTo(Relationships.WellKnown over, Class<T> entityType) {
        return new Builder<>(this, hop(), Query.filter(), entityType).hop(Related.by(over), type(entityType));
    }

    /**
     * Virtually identical to {@link #proceedTo(Relationships.WellKnown, Class)} only follows the relationship in the
     * opposite direction.
     *
     * @param over       the relationship to retreat over (i.e. if the current position is the target of the
     *                   relationship, the sought after entity type needs to be the source of the relationship).
     * @param entityType the type of the entities to retreat to
     * @param <T>        the type of the "target" entities
     * @return a context builder with the modified source path, select candidates and type
     */
    <T extends Entity<?, ?>> Builder<BE, T> retreatTo(Relationships.WellKnown over, Class<T> entityType) {
        return new Builder<>(this, hop(), Query.filter(), entityType).hop(Related.asTargetBy(over), type(entityType));
    }

    /**
     * Proceeds the traversal to the next entities taking into account what parents the next entities should have.
     *
     * @param nextEntityType the type of the entities the new context will target
     * @param parentsType    the type of the enum containing the possible parent types
     * @param currentParent  the parent type representing the current position in the traversal
     * @param parents        the parents to proceed to target entities over
     * @param hopBuilder the producer function that will convert the parent into a filter path to apply to the
     *                       traversal
     * @param <T>            the type of the next entity
     * @param <P>            the type of the enum of the possible parents of the target entity
     * @return a new traversal context
     */
    <T extends Entity<?, ?>, P extends Enum<P> & Parents>
    TraversalContext<BE, T> proceedWithParents(Class<T> nextEntityType, Class<P> parentsType, P currentParent,
                                               P[] parents,
                                               BiConsumer<P, Query.SymmetricExtender> hopBuilder) {
        EnumSet<P> ps = ParentsUtil.convert(parentsType, currentParent, parents);
        TraversalContext.Builder<BE, E> bld = proceed();

        for (P p : ps) {
            Query.Builder qb = bld.rawQueryBuilder();
            qb = qb.branch();
            Query.SymmetricExtender extender = qb.symmetricExtender();
            hopBuilder.accept(p, extender);
            qb.done();
        }

        return bld.getting(nextEntityType);
    }

    /**
     * The new context will have the source path composed by appending current select candidates to the current source
     * path. The new context will have select candidates such that it will select the relationships in given direction
     * stemming from the entities on the new source path.
     *
     * @param direction the direction of the relationships to look for
     * @return a context builder with the modified source path, select candidates and type
     */
    Builder<BE, Relationship> proceedToRelationships(Relationships.Direction direction) {
        return new Builder<>(this, hop(), Query.filter(), Relationship.class)
                .hop(new SwitchElementType(direction, false));
    }

    /**
     * An opposite of {@link #proceedToRelationships(Relationships.Direction)}.
     *
     * @param direction  the direction in which to "leave" the relationships
     * @param entityType the type of entities to "hop to"
     * @param <T>        the type of entities to "hop to"
     * @return a context builder with the modified source path, select candidates and type
     */
    <T extends Entity<?, ?>> Builder<BE, T> proceedFromRelationshipsTo(Relationships.Direction direction,
                                                                       Class<T> entityType) {
        return new Builder<>(this, hop(), Query.filter(), entityType)
                .hop(new SwitchElementType(direction, true)).where(type(entityType));
    }

    /**
     * @return a new query selecting the select candidates from the source path. The resulting extender
     * is set up to append filter fragments.
     */
    Query.SymmetricExtender select() {
        return sourcePath.extend().filter().withExact(selectCandidates);
    }

    /**
     * @return appends the select candidates to the source path. The only difference between this and {@link #select()}
     * is that this method returns the extender set up to append path fragments.
     */
    Query.SymmetricExtender hop() {
        return sourcePath.extend().path().withExact(selectCandidates).path();
    }

    /**
     * Constructs a new traversal context with given time as its "now".
     * @param time the time of now
     * @return a new traversal context
     */
    TraversalContext<BE, E> at(Instant time) {
        return new TraversalContext<>(inventory, time, sourcePath, Query.empty(), backend, entityClass, configuration,
                observableContext, transactionRetries, this, null, transactionConstructor);
    }

    Discriminator discriminator() {
        return Discriminator.time(now);
    }

    /**
     * Constructs a new traversal context by replacing the source path with the provided query and clearing out the
     * selected candidates.
     *
     * @param path the source path of the new context
     * @return a new traversal context with the provided source path and empty select candidates, but otherwise
     * identical to this one.
     */
    TraversalContext<BE, E> replacePath(Query path) {
        return new TraversalContext<>(inventory, now, path, Query.empty(), backend, entityClass, configuration,
                observableContext, transactionRetries, this, null, transactionConstructor);
    }

    TraversalContext<BE, E> toCreatedEntity(E entity, boolean cache) {
        return new TraversalContext<>(inventory, now, Query.to(entity.getPath()), Query.empty(), backend, entityClass,
                configuration, observableContext, transactionRetries, this, cache ? entity : null, null);
    }

    TraversalContext<BE, E> proceedTo(Path path) {
        if (!AbstractElement.segmentTypeFromType(entityClass).equals(path.getSegment().getElementType())) {
            throw new IllegalArgumentException("Path doesn't point to the type of element currently being accessed.");
        }
        return replacePath(Util.extendTo(this, path));
    }

    /**
     * Sends out the notification to the subscribers.
     *
     * @param entity the entity on which the action took place
     * @param action the action (for which the entity and context resolve to the same type)
     * @param <V>    the type of the entity and at the same time the type of the action context
     * @see #notify(Object, Object, Action)
     */
    <V> void notify(V entity, Action<V, V> action) {
        notify(entity, entity, action);
    }

    /**
     * Sends out the notification to the subscribers.
     *
     * @param entity        the entity on which the action occured
     * @param actionContext the description of the action
     * @param action        the actual action
     * @param <C>           the type of the action description (aka context)
     * @param <V>           the type of the entity on which the action occurred
     */
    <C, V> void notify(V entity, C actionContext, Action<C, V> action) {
        Iterator<Subject<C, C>> subjects = observableContext.matchingSubjects(action, entity);
        while (subjects.hasNext()) {
            Subject<C, C> s = subjects.next();
            s.onNext(actionContext);
        }
    }

    public int getTransactionRetriesCount() {
        return transactionRetries;
    }

    public TransactionConstructor<BE> getTransactionConstructor() {
        return transactionConstructor;
    }

    /**
     * Sends out all the pending notifications in the supplied object.
     *
     * @param entityAndNotifications the list of pending notifications
     */
    void notifyAll(EntityAndPendingNotifications<BE, ?> entityAndNotifications) {
        entityAndNotifications.getNotifications().forEach(this::notify);
    }

    /**
     * Another way of sending out a notification.
     *
     * @param notification the notification to send out
     * @param <C>          the type of the action description (aka context)
     * @param <V>          the type of the entity on which the action occurred
     */
    <C, V> void notify(Notification<C, V> notification) {
        notify(notification.getValue(), notification.getActionContext(), notification.getAction());
    }

    Transaction<BE> startTransaction() {
        return startTransaction(new BasePreCommit<>());
    }

    Transaction<BE> startTransaction(Transaction.PreCommit<BE> preCommit) {
        Transaction<BE> tx = transactionConstructor.construct(backend, preCommit);
        tx.getPreCommit().initialize(inventory.keepTransaction(tx), tx);
        return tx;
    }

    /**
     * Builds a new traversal context.
     *
     * @param <BE> the type of the backend elements
     * @param <E>  the type of the inventory element the new context will represent
     */
    public static final class Builder<BE, E extends AbstractElement<?, ?>> {
        private final TraversalContext<BE, ?> sourceContext;
        private final Query.SymmetricExtender pathExtender;
        private final Query.SymmetricExtender selectExtender;
        private final Class<E> entityClass;

        public Builder(TraversalContext<BE, ?> sourceContext, Query.SymmetricExtender pathExtender,
                       Query.SymmetricExtender selectExtender, Class<E> entityClass) {
            this.sourceContext = sourceContext;
            this.pathExtender = pathExtender;
            this.selectExtender = selectExtender;
            this.entityClass = entityClass;
        }

        /**
         * Appends the sets of filters in succession to the select candidates.
         *
         * @param filters the sets of filters to apply
         * @return this builder
         * @see #where(Filter[][])
         * @see #where(Filter...)
         */
        public Builder<BE, E> whereAll(Filter[][] filters) {
            if (filters.length == 1) {
                return where(filters[0]);
            } else {
                for (Filter[] fs : filters) {
                    hop().where(fs);
                }
                return this;
            }
        }

        /**
         * Create query branches in the select candidates with each of the provided sets of filters.
         *
         * @param filters the sets of filters, each representing a new branch in the query
         * @return this builder
         */
        public Builder<BE, E> where(Filter[][] filters) {
            selectExtender.filter().with(filters);
            return this;
        }

        /**
         * Appends the provided set of filters to the current select candidates.
         *
         * @param filters the set of filters to append
         * @return this builder
         */
        public Builder<BE, E> where(Filter... filters) {
            selectExtender.filter().with(filters);
            return this;
        }

        /**
         * Create query branches in the select candidates with each of the provided sets of filters.
         * The filters are applied as path fragments.
         *
         * @param filters the sets of the filters to append as path fragments
         * @return this builder
         */
        public Builder<BE, E> hop(Filter[][] filters) {
            selectExtender.path().with(filters);
            return this;
        }

        /**
         * Appends the provided set of filters to the current select candidates.
         * The filters are applied as path fragments.
         *
         * @param filters the set of filters to append as path fragments
         * @return this builder
         */
        public Builder<BE, E> hop(Filter... filters) {
            selectExtender.path().with(filters);
            return this;
        }

        public Query.Builder rawQueryBuilder() {
            return selectExtender.rawQueryBuilder();
        }

        /**
         * @return a new traversal context set up using this builder
         */
        TraversalContext<BE, E> get() {
            return new TraversalContext<>(sourceContext.inventory, sourceContext.now, pathExtender.get(),
                    selectExtender.get(), sourceContext.backend, entityClass, sourceContext.configuration,
                    sourceContext.observableContext, sourceContext.transactionRetries, sourceContext, null,
                    sourceContext.transactionConstructor);
        }

        /**
         * Changes the entity type of the to-be-returned traversal context.
         *
         * @param entityType the type of entities to be returned by traversals using the new context
         * @param <T>        the type
         * @return a new traversal context set up using this builder and querying for entities of the provided type
         */
        <T extends AbstractElement<?, ?>> TraversalContext<BE, T> getting(Class<T> entityType) {
            return new TraversalContext<>(sourceContext.inventory, sourceContext.now, pathExtender.get(),
                    selectExtender.get(), sourceContext.backend, entityType, sourceContext.configuration,
                    sourceContext.observableContext, sourceContext.transactionRetries, sourceContext, null, null);
        }
    }
}
