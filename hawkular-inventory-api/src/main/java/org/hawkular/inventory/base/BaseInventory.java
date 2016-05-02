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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.TransactionFrame;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.CanonicalPath;

import rx.Observable;

/**
 * An implementation of the {@link Inventory} that converts the API traversals into trees of filters that it then passes
 * for evaluation to a {@link InventoryBackend backend}.
 *
 * <p>This class is meant to be inherited by the implementation that should provide the initialization and cleanup
 * logic.
 *
 * @param <E> the type of the backend-specific class representing entities and relationships.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public abstract class BaseInventory<E> implements Inventory {

    public static final Configuration.Property TRANSACTION_RETRIES = Configuration.Property.builder()
            .withPropertyNameAndSystemProperty("hawkular.inventory.transaction.retries")
            .withEnvironmentVariables("HAWKULAR_INVENTORY_TRANSACTION_RETRIES").build();

    private InventoryBackend<E> backend;
    private final ObservableContext observableContext;
    private Configuration configuration;
    private TraversalContext<E, Tenant> tenantContext;
    private TraversalContext<E, Relationship> relationshipContext;
    private final TransactionConstructor<E> transactionConstructor;

    /**
     * This is a sort of copy constructor.
     * Can be used by subclasses when implementing the {@link #cloneWith(TransactionConstructor)} method.
     *
     * @param orig the original instance to copy stuff over from
     * @param backend       if not null, then use this backend instead of the one used by {@code orig}
     * @param transactionConstructor if not null, then use this ctor instead of the one used by {@code orig}
     */
    protected BaseInventory(BaseInventory<E> orig, InventoryBackend<E> backend,
                            TransactionConstructor<E> transactionConstructor) {

        this.observableContext = orig.observableContext;
        this.configuration = orig.configuration;

        this.backend = backend == null ? orig.backend : backend;
        this.transactionConstructor = transactionConstructor == null
                ? orig.transactionConstructor : transactionConstructor;

        tenantContext = new TraversalContext<>(this, Query.empty(),
                Query.path().with(With.type(Tenant.class)).get(), this.backend, Tenant.class, configuration,
                observableContext, this.transactionConstructor);

        relationshipContext = new TraversalContext<>(this, Query.empty(), Query.path().get(), this.backend,
                Relationship.class, configuration, observableContext, this.transactionConstructor);
    }

    protected BaseInventory() {
        observableContext = new ObservableContext();
        transactionConstructor = TransactionConstructor.startInBackend();
    }

    /**
     * Mainly here for testing purposes
     * @param txCtor transaction constructor to use - useful to supply some test-enabled impl
     */
    protected BaseInventory(TransactionConstructor<E> txCtor) {
        observableContext = new ObservableContext();
        transactionConstructor = txCtor;
    }

    /**
     * Clones this inventory with the exception of the transaction constructor. The returned inventory will be the
     * exact copy of this one but its transaction constructor will be set to the provided one.
     *
     * @param transactionCtor the transaction constructor to use. It has already been adapted using
     * {@link #adaptTransactionConstructor(TransactionConstructor)}.
     *
     * @return the cloned inventory
     */
    protected abstract BaseInventory<E> cloneWith(TransactionConstructor<E> transactionCtor);

    /**
     * This is a hook used for unit testing. Using this we can keep track of how transaction constructors are used.
     * @param txCtor a potentially modified transaction constructor
     * @return a transaction constructor fit to use with this inventory impl.
     */
    protected TransactionConstructor<E> adaptTransactionConstructor(TransactionConstructor<E> txCtor) {
        return txCtor;
    }

    @Override
    public final void initialize(Configuration configuration) {
        this.backend = doInitialize(configuration);

        tenantContext = new TraversalContext<>(this, Query.empty(),
                Query.path().with(With.type(Tenant.class)).get(), backend, Tenant.class, configuration,
                observableContext, transactionConstructor);

        relationshipContext = new TraversalContext<>(this, Query.empty(), Query.path().get(), backend,
                Relationship.class, configuration, observableContext, transactionConstructor);
        this.configuration = configuration;
    }

    @Override
    public TransactionFrame newTransactionFrame() {
        if (backend.isPreferringBigTransactions()) {
            //a full-blown transaction frame... we don't commit/rollback anything and postpone all that work to the
            //frame's commit rollback
            return new OneTxTransactionFrame();
        } else {
            //the backend doesn't like big transactions... we just commit everything as it goes, essentially rendering
            //transaction frame useless..
            return new ManyTxTransactionFrame();
        }
    }

    BaseInventory<E> keepTransaction(Transaction<E> tx) {
        return cloneWith(adaptTransactionConstructor((b, p) -> {
            HidingPrecommit<E> precommit = new HidingPrecommit<>();
            Runnable transferActionsAndNotifs = () -> {
                //transfer the resulting notifications and actions - they will be emitted once the "real" transaction
                //really successfully commits.
                precommit.getHiddenActions().forEach(tx.getPreCommit()::addAction);
                precommit.getHiddenNotifications().forEach(tx.getPreCommit()::addNotifications);
            };

            return new BackendTransaction<>(new TransactionIgnoringBackend<>(tx.directAccess(),
                    transferActionsAndNotifs), precommit);
        }));
    }

    /**
     * This method is called during {@link #initialize(Configuration)} and provides the instance of the backend
     * initialized from the configuration.
     *
     * @param configuration the configuration provided by the user
     * @return a backend implementation that will be used to access the backend store of the inventory
     */
    protected abstract InventoryBackend<E> doInitialize(Configuration configuration);

    @Override
    public final void close() throws Exception {
        if (backend != null) {
            backend.close();
            backend = null;
        }
    }

    @Override
    public Tenants.ReadWrite tenants() {
        return new BaseTenants.ReadWrite<>(tenantContext);
    }

    @Override
    public Relationships.Read relationships() {
        return new BaseRelationships.Read<>(relationshipContext);
    }

    /**
     * <b>WARNING</b>: This is not meant for general consumption but primarily for testing purposes. You can render
     * the inventory inconsistent and/or unusable with unwise modifications done directly through the backend.
     *
     * @return the backend this inventory is using for persistence and querying.
     */
    public InventoryBackend<E> getBackend() {
        return backend;
    }

    @Override
    public boolean hasObservers(Interest<?, ?> interest) {
        return observableContext.isObserved(interest);
    }

    @Override
    public <C, V> Observable<C> observable(Interest<C, V> interest) {
        return observableContext.getObservableFor(interest);
    }

    @Override
    public InputStream getGraphSON(String tenantId) {
        return getBackend().getGraphSON(tenantId);
    }

    @Override
    public AbstractElement<?, ?> getElement(CanonicalPath path) {
        try {
            E element = getBackend().find(path);
            Class<?> type = getBackend().extractType(element);

            return (AbstractElement<?, ?>) getBackend().convert(element, type);
        } catch (ElementNotFoundException e) {
            throw new EntityNotFoundException("No element found on path: " + path.toString());
        }
    }

    @Override
    public <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(CanonicalPath startingPoint,
                                                                    Relationships.Direction direction, Class<T> clazz,
                                                                    String... relationshipNames) {

        return getBackend().getTransitiveClosureOver(startingPoint, direction, clazz, relationshipNames);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public <T extends AbstractElement> Page<T> execute(Query query, Class<T> requestedEntity, Pager pager) {

        Page<T> page = backend.query(query, pager, e -> backend.convert(e, requestedEntity), null);

        return page;
    }

    private static class TransactionIgnoringBackend<E> extends DelegatingInventoryBackend<E> {

        private final Runnable onCommit;


        public TransactionIgnoringBackend(InventoryBackend<E> backend, Runnable onCommit) {
            super(backend);
            this.onCommit = onCommit;
        }

        @Override public void commit() throws CommitFailureException {
            if (onCommit != null) {
                onCommit.run();
            }
        }

        @Override public void rollback() {
        }

        @Override public InventoryBackend<E> startTransaction() {
            return this;
        }
    }

    private static final class HidingPrecommit<E> extends Transaction.PreCommit.Simple<E> {
        private HidingPrecommit() {
        }

        @Override public List<Consumer<Transaction<E>>> getActions() {
            return Collections.emptyList();
        }

        @Override public List<EntityAndPendingNotifications<E, ?>> getFinalNotifications() {
            return Collections.emptyList();
        }

        public List<EntityAndPendingNotifications<E, ?>> getHiddenNotifications() {
            return super.getFinalNotifications();
        }

        public List<Consumer<Transaction<E>>> getHiddenActions() {
            return super.getActions();
        }
    }

    private class OneTxTransactionFrame implements TransactionFrame {
        private InventoryBackend<E> activeBackend;
        private Transaction.PreCommit<E> activePrecommit;
        private List<TransactionPayload.Committing<?, E>> committedPayloads = new ArrayList<>();

        private final TransactionConstructor<E> fakeTxCtor = (b, p) -> {
            InventoryBackend<E> realBackend;
            if (activeBackend == null) {
                activeBackend = b.startTransaction();
                activePrecommit = p;
            }
            realBackend = activeBackend;

            BaseInventory.HidingPrecommit<E> txPrecommit = new BaseInventory.HidingPrecommit<>();

            Runnable onCommit = () -> {
                //transfer the resulting notifications - they will be emitted once the "real" transaction
                //really successfully commits.
                txPrecommit.getHiddenActions().forEach(activePrecommit::addAction);
                txPrecommit.getHiddenNotifications().forEach(activePrecommit::addNotifications);
            };

            return new BackendTransaction<E>(new TransactionIgnoringBackend<>(realBackend, onCommit),
                    txPrecommit) {
                @Override public void registerCommittedPayload(TransactionPayload.Committing<?, E>
                                                                       committedPayload) {
                    committedPayloads.add(committedPayload);
                }
            };
        };

        @Override public void commit() throws CommitException {
            Util.onFailureRetry(p ->
                            new BackendTransaction<>(new TransactionIgnoringBackend<>(activeBackend, null), p),
                    Transaction.Committable.from(
                            adaptTransactionConstructor(fakeTxCtor)
                                    .construct(activeBackend, new BasePreCommit<>())),
                    (TransactionPayload.Committing<Void, E>) tx -> {
                        activePrecommit.initialize(boundInventory(), tx);
                        activePrecommit.getActions().forEach(a -> a.accept(tx));
                        activeBackend.commit();
                        activePrecommit.getFinalNotifications().forEach(tenantContext::notifyAll);

                        return null;
                    },
                    tx -> {
                        activePrecommit.reset();

                        for (TransactionPayload.Committing<?, E> p : committedPayloads) {
                            //the payloads "think" they each run in a transaction... prepare
                            //those for each of them
                            Transaction<E> fakeTx = fakeTxCtor.construct(activeBackend,
                                    new Transaction.PreCommit.Simple<>());
                            fakeTx.getPreCommit().initialize(boundInventory(), fakeTx);

                            p.run(fakeTx);
                        }

                        activePrecommit.initialize(boundInventory(), tx);
                        activePrecommit.getActions().forEach(a -> a.accept(tx));
                        activeBackend.commit();
                        activePrecommit.getFinalNotifications().forEach(tenantContext::notifyAll);

                        return null;
                    }, relationshipContext.getTransactionRetriesCount());
        }

        @Override public void rollback() {
            backend.rollback();
        }

        @Override public Inventory boundInventory() {
            return cloneWith(adaptTransactionConstructor(fakeTxCtor));
        }
    }

    private class ManyTxTransactionFrame implements TransactionFrame {
        private Transaction.PreCommit<E> activePrecommit;

        private final TransactionConstructor<E> notifsStashingTxCtor = (b, p) -> {
            if (activePrecommit == null) {
                activePrecommit = p;
            }

            BaseInventory.HidingPrecommit<E> hidingPrecommit = new BaseInventory.HidingPrecommit<>();

            return new BackendTransaction<>(new DelegatingInventoryBackend<E>(backend) {
                @Override public void commit() throws CommitFailureException {
                    hidingPrecommit.getHiddenActions().forEach(activePrecommit::addAction);
                    hidingPrecommit.getHiddenNotifications().forEach(activePrecommit::addNotifications);
                    super.commit();
                }
            }, hidingPrecommit);
        };

        @Override public void commit() throws CommitException {
            //we need to start a new transaction for the actions to run in... The actual payloads are already
            //committed.
            try {
                Transaction<E> tx = tenantContext.startTransaction();
                activePrecommit.initialize(BaseInventory.this, tx);

                activePrecommit.getActions().forEach(a -> a.accept(tx));

                backend.commit();
            } catch (Throwable t) {
                backend.rollback();
                throw new CommitException(t);
            }

            activePrecommit.getFinalNotifications().forEach(tenantContext::notifyAll);
        }

        @Override public void rollback() {
            //This is a poor mans rollback... because the individual actions in this frame actually commit
            //on their own and we only stash away the actions and notifications to be sent, in the case of
            //rollback, we need to "complete" what's already committed by emitting the notifications.
            commit();
        }

        @Override public Inventory boundInventory() {
            return cloneWith(adaptTransactionConstructor(notifsStashingTxCtor));
        }
    }
}
