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
package org.hawkular.inventory.api.test;

import static org.hawkular.inventory.api.Action.created;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.TransactionFrame;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.base.EntityAndPendingNotifications;
import org.hawkular.inventory.base.Transaction;
import org.hawkular.inventory.base.TransactionConstructor;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
public class PreCommitActionsTest {

    @Test
    public void testPreCommitActionsInSingleActionTransaction() throws Exception {
        @SuppressWarnings("unchecked")
        InventoryBackend<String> backend = Mockito.mock(InventoryBackend.class);

        //holders for counters that we are going to be modifying in inner classes
        int[] payloadsExecuted = new int[1];
        PrecommitTracker pct = new PrecommitTracker();

        when(backend.startTransaction()).then(a -> {
            payloadsExecuted[0]++;
            Assert.assertEquals(0, pct.actionsObtained);
            return backend;
        });
        when(backend.persist(any(), any())).thenAnswer((args) -> args.getArguments()[0].toString());

        commonBackendMocks(backend);

        doAnswer((args) -> {
            Assert.assertEquals(1, payloadsExecuted[0]);
            Assert.assertEquals(1, pct.actionsObtained);
            Assert.assertEquals(0, pct.finalNotificationsObtained);
            return null;
        }).when(backend).commit();

        TestInventory inv = new TestInventory(backend, pct);

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        inv.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());

        Assert.assertEquals(1, payloadsExecuted[0]);
        Assert.assertEquals(1, pct.actionsObtained);
        Assert.assertEquals(1, pct.finalNotificationsObtained);
    }

    @Test
    public void testPreCommitActionsInTransactionFrame() throws Exception {
        @SuppressWarnings("unchecked")
        InventoryBackend<String> backend = Mockito.mock(InventoryBackend.class);

        when(backend.startTransaction()).thenReturn(backend);

        //holders for counters that we are going to be modifying in inner classes
        int[] dataPersisted = new int[1];
        int[] notifsSent = new int[1];
        PrecommitTracker pct = new PrecommitTracker();

        TestInventory inv = new TestInventory(backend, pct);

        inv.observable(Interest.in(Tenant.class).being(created())).subscribe(t -> notifsSent[0]++);

        //this way we check that the backend is actually contacted twice to persist the tenants, which would normally
        //cause 2 transactions to be committed.
        when(backend.persist(any(), any())).thenAnswer((args) -> {
            dataPersisted[0]++;
            return args.getArguments()[0].toString();
        });

        commonBackendMocks(backend);

        //simulate the commit and check for correct behavior
        //noinspection Duplicates
        doAnswer((args) -> {
            Assert.assertEquals(2, dataPersisted[0]);
            //even though we had 2 calls to inventory (persisted 2 pieces of data)
            //the actions should be obtained only once - at the actual commit of the frame.
            Assert.assertEquals(1, pct.actionsObtained);
            Assert.assertEquals(0, notifsSent[0]);
            return null;
        }).when(backend).commit();

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        TransactionFrame frame = inv.newTransactionFrame();
        Inventory inv2 = frame.boundInventory();

        //these two invocations do not really commit, because they're executed through an inventory that is bound to
        //a transaction frame.
        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());
        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf2").build());

        //now commit the frame
        frame.commit();

        Assert.assertEquals(2, dataPersisted[0]);
        Assert.assertEquals(1, pct.actionsObtained);
        Assert.assertEquals(2, notifsSent[0]);
    }

    @Test
    public void testPreCommitActionsInRetriedTransaction() throws Exception {
        @SuppressWarnings("unchecked")
        InventoryBackend<String> backend = Mockito.mock(InventoryBackend.class);

        when(backend.startTransaction()).thenReturn(backend);

        //holders for counters that we are going to be modifying in inner classes
        int[] dataPersisted = new int[1];
        int[] notifsSent = new int[1];
        PrecommitTracker pct = new PrecommitTracker();

        TestInventory inv = new TestInventory(backend, pct);

        inv.observable(Interest.in(Tenant.class).being(created())).subscribe(t -> notifsSent[0]++);

        //noinspection Duplicates
        doAnswer((args) -> {
            Assert.assertEquals(1, dataPersisted[0]);
            Assert.assertEquals(1, pct.actionsObtained);
            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(2, dataPersisted[0]);
            Assert.assertEquals(2, pct.actionsObtained);
            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(3, dataPersisted[0]);
            Assert.assertEquals(3, pct.actionsObtained);
            Assert.assertEquals(0, notifsSent[0]);
            return null;
        }).when(backend).commit();

        when(backend.persist(any(), any())).thenAnswer((args) -> {
            dataPersisted[0]++;
            return args.getArguments()[0].toString();
        });

        commonBackendMocks(backend);

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        inv.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());

        Assert.assertEquals(3, dataPersisted[0]);
        Assert.assertEquals(3, pct.actionsObtained);
        Assert.assertEquals(1, notifsSent[0]);
    }

    @Test
    public void testPreCommitInRetriedTransactionFrame() throws Exception {
        @SuppressWarnings("unchecked")
        InventoryBackend<String> backend = Mockito.mock(InventoryBackend.class);

        when(backend.startTransaction()).thenReturn(backend);

        //holders for counters that we are going to be modifying in inner classes
        int[] dataPersisted = new int[1];
        int[] notifsSent = new int[1];
        PrecommitTracker pct = new PrecommitTracker();

        TestInventory inv = new TestInventory(backend, pct);

        inv.observable(Interest.in(Tenant.class).being(created())).subscribe(t -> notifsSent[0]++);

        //noinspection Duplicates
        doAnswer((args) -> {
            Assert.assertEquals(2, dataPersisted[0]);
            Assert.assertEquals(1, pct.actionsObtained);
            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(4, dataPersisted[0]);
            Assert.assertEquals(2, pct.actionsObtained);
            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(6, dataPersisted[0]);
            Assert.assertEquals(3, pct.actionsObtained);
            Assert.assertEquals(0, notifsSent[0]);
            return null;
        }).when(backend).commit();

        when(backend.persist(any(), any())).thenAnswer((args) -> {
            dataPersisted[0]++;
            return args.getArguments()[0].toString();
        });

        commonBackendMocks(backend);

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        TransactionFrame frame = inv.newTransactionFrame();

        Inventory inv2 = frame.boundInventory();

        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());
        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf2").build());

        frame.commit();

        Assert.assertEquals(6, dataPersisted[0]);
        Assert.assertEquals(3, pct.actionsObtained);
        Assert.assertEquals(2, notifsSent[0]);

        //check that we had exactly 3 commit attemps
        verify(backend, times(3)).commit();
    }

    private void commonBackendMocks(InventoryBackend<String> backend) throws Exception {
        when(backend.isUniqueIndexSupported()).thenReturn(true);
        when(backend.isPreferringBigTransactions()).thenReturn(true);

        when(backend.extractId(any())).thenAnswer((args) -> CanonicalPath.fromString(args.getArguments()[0]
                .toString()).getSegment().getElementId());

        when(backend.extractType(any())).thenAnswer(args -> {
            SegmentType t = CanonicalPath.fromString(args.getArgumentAt(0, String.class)).getSegment().getElementType();

            return Inventory.types().bySegment(t).getElementType();
        });

        when(backend.find(any(), any())).thenAnswer(args -> args.getArgumentAt(1, CanonicalPath.class).toString());

        when(backend.convert(any(), any(), any())).thenAnswer(args -> {
            Class<?> type = args.getArgumentAt(2, Class.class);
            String path = args.getArgumentAt(1, String.class);

            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object entity = ctor.newInstance();
            Field pathF = null;
            while (true) {
                try {
                    pathF = type.getDeclaredField("path");
                    break;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }

            pathF.setAccessible(true);
            pathF.set(entity, CanonicalPath.fromString(path));

            return entity;
        });
    }

    private static final class PrecommitTracker implements Function<Transaction.PreCommit<String>,
            Transaction.PreCommit<String>> {
        int actionsObtained;
        int finalNotificationsObtained;
        int initialized;
        int reset;

        @Override
        public Transaction.PreCommit<String> apply(Transaction.PreCommit<String> pc) {
            return new Transaction.PreCommit<String>() {
                @Override public List<EntityAndPendingNotifications<String, ?>> getFinalNotifications() {
                    finalNotificationsObtained++;
                    return pc.getFinalNotifications();
                }

                @Override public void initialize(Inventory inventory, Transaction<String> tx) {
                    if (this.getClass() != pc.getClass()) {
                        initialized++;
                    }
                    pc.initialize(inventory, tx);
                }

                @Override public void reset() {
                    if (this.getClass() != pc.getClass()) {
                        reset++;
                    }
                    pc.reset();
                }

                @Override public void addAction(Consumer<Transaction<String>> action) {
                    pc.addAction(action);
                }

                @Override public List<Consumer<Transaction<String>>> getActions() {
                    if (this.getClass() != pc.getClass()) {
                        actionsObtained++;
                    }
                    return pc.getActions();
                }

                @Override public void addNotifications(EntityAndPendingNotifications<String, ?> element) {
                    pc.addNotifications(element);
                }

                @Override public void addProcessedNotifications(EntityAndPendingNotifications<String, ?> element) {
                    pc.addProcessedNotifications(element);
                }
            };
        }
    }

    private static final class TestInventory extends BaseInventory<String> {

        private final InventoryBackend<String> backend;
        private final PrecommitTracker tracker;

        public TestInventory(InventoryBackend<String> backend, PrecommitTracker tracker) {
            super((b, p) -> {
                Transaction.PreCommit<String> realPrecommit = tracker.apply(p);
                return TransactionConstructor.<String>startInBackend().construct(b, realPrecommit);
            });
            this.backend = backend;
            this.tracker = tracker;
        }

        private TestInventory(BaseInventory<String> orig, InventoryBackend<String> backend,
                              TransactionConstructor<String> transactionConstructor,
                              PrecommitTracker tracker) {
            super(orig, backend, transactionConstructor);
            this.backend = backend;
            this.tracker = tracker;
        }

        @Override protected BaseInventory<String> cloneWith(TransactionConstructor<String> transactionCtor) {
            return new TestInventory(this, backend, transactionCtor, tracker);
        }

        @Override
        protected TransactionConstructor<String> adaptTransactionConstructor(TransactionConstructor<String> txCtor) {
            return (b, p) -> {
                Transaction.PreCommit<String> realPrecommit = tracker.apply(p);
                return txCtor.construct(b, realPrecommit);
            };
        }

        @Override protected InventoryBackend<String> doInitialize(Configuration configuration) {
            return backend;
        }
    }
}
