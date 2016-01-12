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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.TransactionFrame;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.base.EntityAndPendingNotifications;
import org.hawkular.inventory.base.PotentiallyCommittingPayload;
import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.base.spi.Transaction;
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
        boolean[] actionsObtained = new boolean[1];
        int[] payloadsExecuted = new int[1];

        Transaction.PreCommit<String> manager = new Transaction.PreCommit.Simple<String>() {
            @Override public List<Consumer<Transaction<String>>> getActions() {
                actionsObtained[0] = true;
                return Collections.emptyList();
            }
        };

        Transaction<String> tx = new TestTransaction<String>(true, manager) {
            @Override public <R> R execute(PotentiallyCommittingPayload<R, String> payload)
                    throws CommitFailureException {
                payloadsExecuted[0]++;
                Assert.assertFalse(actionsObtained[0]);
                return super.execute(payload);
            }
        };

        doAnswer((args) -> {
            Assert.assertEquals(1, payloadsExecuted[0]);
            Assert.assertFalse(actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();
            return null;
        }).when(backend).commit(eq(tx));

        BaseInventory<String> inv = new BaseInventory<String>((ctx, mutating, preCommit) -> tx) {
            @Override protected InventoryBackend<String> doInitialize(Configuration configuration) {
                return backend;
            }
        };

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        inv.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());

        Assert.assertEquals(1, payloadsExecuted[0]);
        Assert.assertTrue(actionsObtained[0]);
    }

    @Test
    public void testPreCommitActionsInTransactionFrame() throws Exception {
        @SuppressWarnings("unchecked")
        InventoryBackend<String> backend = Mockito.mock(InventoryBackend.class);

        //holders for counters that we are going to be modifying in inner classes
        int[] actionsObtained = new int[1];
        int[] dataPersisted = new int[1];
        int[] notifsSent = new int[1];

        BaseInventory<String> inv = new BaseInventory<String>((ctx, mutating, preCommit) ->
                new TestTransaction<>(mutating, new TrackingPreCommit(preCommit, actionsObtained))) {
            @Override protected InventoryBackend<String> doInitialize(Configuration configuration) {
                return backend;
            }
        };

        inv.observable(Interest.in(Tenant.class).being(created())).subscribe(t -> notifsSent[0]++);

        //this way we check that the backend is actually contacted twice to persist the tenants, which would normally
        //cause 2 transactions to be committed.
        when(backend.persist(any(), any())).thenAnswer((args) -> {
            dataPersisted[0]++;
            return args.getArguments()[0].toString();
        });
        when(backend.extractId(any())).thenAnswer((args) -> CanonicalPath.fromString(args.getArguments()[0]
                .toString()).getSegment().getElementId());

        //simulate the commit and check for correct behavior
        //noinspection Duplicates
        doAnswer((args) -> {
            Assert.assertEquals(2, dataPersisted[0]);
            Assert.assertEquals(0, actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();

            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(4, dataPersisted[0]);
            Assert.assertEquals(1, actionsObtained[0]);

            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();

            Assert.assertEquals(0, notifsSent[0]);
            return null;
        }).when(backend).commit(any());

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        TransactionFrame frame = inv.newTransactionFrame();
        Inventory inv2 = frame.boundInventory();

        //these two invocations do not really commit, because they're executed through an inventory that is bound to
        //a transaction frame.
        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());
        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf2").build());

        //now commit the frame
        frame.commit();

        Assert.assertEquals(4, dataPersisted[0]);
        Assert.assertEquals(2, actionsObtained[0]);
        Assert.assertEquals(2, notifsSent[0]);

        //check that we had exactly 2 commit attemps
        verify(backend, times(2)).commit(any());
    }

    @Test
    public void testPreCommitActionsInRetriedTransaction() throws Exception {
        @SuppressWarnings("unchecked")
        InventoryBackend<String> backend = Mockito.mock(InventoryBackend.class);

        //holders for counters that we are going to be modifying in inner classes
        int[] actionsObtained = new int[1];
        int[] dataPersisted = new int[1];
        int[] notifsSent = new int[1];

        BaseInventory<String> inv = new BaseInventory<String>((ctx, mutating, preCommit) ->
                new TestTransaction<>(mutating, new TrackingPreCommit(preCommit, actionsObtained))) {
            @Override protected InventoryBackend<String> doInitialize(Configuration configuration) {
                return backend;
            }
        };

        inv.observable(Interest.in(Tenant.class).being(created())).subscribe(t -> notifsSent[0]++);

        //noinspection Duplicates
        doAnswer((args) -> {
            Assert.assertEquals(1, dataPersisted[0]);
            Assert.assertEquals(0, actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();

            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(2, dataPersisted[0]);
            Assert.assertEquals(1, actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();

            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(3, dataPersisted[0]);
            Assert.assertEquals(2, actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();

            Assert.assertEquals(0, notifsSent[0]);
            return null;
        }).when(backend).commit(any());

        when(backend.persist(any(), any())).thenAnswer((args) -> {
            dataPersisted[0]++;
            return args.getArguments()[0].toString();
        });
        when(backend.extractId(any())).thenAnswer((args) -> CanonicalPath.fromString(args.getArguments()[0]
                .toString()).getSegment().getElementId());

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        inv.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());

        Assert.assertEquals(3, dataPersisted[0]);
        Assert.assertEquals(3, actionsObtained[0]);
        Assert.assertEquals(1, notifsSent[0]);
    }

    @Test
    public void testPreCommitInRetriedTransactionFrame() throws Exception {
        @SuppressWarnings("unchecked")
        InventoryBackend<String> backend = Mockito.mock(InventoryBackend.class);

        //holders for counters that we are going to be modifying in inner classes
        int[] actionsObtained = new int[1];
        int[] dataPersisted = new int[1];
        int[] notifsSent = new int[1];

        BaseInventory<String> inv = new BaseInventory<String>((ctx, mutating, preCommit) ->
                new TestTransaction<>(mutating, new TrackingPreCommit(preCommit, actionsObtained))) {
            @Override protected InventoryBackend<String> doInitialize(Configuration configuration) {
                return backend;
            }
        };

        inv.observable(Interest.in(Tenant.class).being(created())).subscribe(t -> notifsSent[0]++);

        //noinspection Duplicates
        doAnswer((args) -> {
            Assert.assertEquals(2, dataPersisted[0]);
            Assert.assertEquals(0, actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();

            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(4, dataPersisted[0]);
            Assert.assertEquals(1, actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();

            Assert.assertEquals(0, notifsSent[0]);
            throw new CommitFailureException();
        }).doAnswer((args) -> {
            Assert.assertEquals(6, dataPersisted[0]);
            Assert.assertEquals(2, actionsObtained[0]);
            //this is what the backend should be doing, so let's do it... It should make the asserts at the end of the
            //test method succeed.
            ((Transaction<?>) args.getArguments()[0]).getPreCommit().getActions();
            Assert.assertEquals(0, notifsSent[0]);
            return null;
        }).when(backend).commit(any());

        when(backend.persist(any(), any())).thenAnswer((args) -> {
            dataPersisted[0]++;
            return args.getArguments()[0].toString();
        });
        when(backend.extractId(any())).thenAnswer((args) -> CanonicalPath.fromString(args.getArguments()[0]
                .toString()).getSegment().getElementId());

        inv.initialize(new Configuration(null, null, Collections.emptyMap()));

        TransactionFrame frame = inv.newTransactionFrame();

        Inventory inv2 = frame.boundInventory();

        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf").build());
        inv2.tenants().create(Tenant.Blueprint.builder().withId("asdf2").build());

        frame.commit();

        Assert.assertEquals(6, dataPersisted[0]);
        Assert.assertEquals(3, actionsObtained[0]);
        Assert.assertEquals(2, notifsSent[0]);
    }

    static class TrackingPreCommit extends Transaction.PreCommit.Simple<String> {
        private final Transaction.PreCommit<String> wrapped;
        private final int[] actionsObtained;

        TrackingPreCommit(Transaction.PreCommit<String> wrapped, int[] actionsObtained) {
            this.wrapped = wrapped;
            this.actionsObtained = actionsObtained;
        }

        @Override public void reset() {
            wrapped.reset();
        }

        @Override public void addNotifications(EntityAndPendingNotifications<String, ?> element) {
            wrapped.addNotifications(element);
        }

        @Override public List<Consumer<Transaction<String>>> getActions() {
            if (!(wrapped instanceof TrackingPreCommit)) {
                actionsObtained[0]++;
            }
            return wrapped.getActions();
        }

        @Override public List<EntityAndPendingNotifications<String, ?>> getFinalNotifications() {
            return wrapped.getFinalNotifications();
        }
    }

    private static class TestTransaction<BE> implements Transaction<BE> {
        private final boolean mutating;
        private List<Consumer<Transaction<BE>>> preCommitActions;
        private final PreCommit<BE> preCommit;
        private final HashMap<Object, Object> attachments = new HashMap<>(1);

        TestTransaction(boolean mutating, PreCommit<BE> preCommit) {
            this.mutating = mutating;
            this.preCommit = preCommit == null ? new PreCommit.Simple<>()
                    : preCommit;
        }

        public boolean isMutating() {
            return mutating;
        }

        public Map<Object, Object> getAttachments() {
            return attachments;
        }

        public void addPreCommitAction(Consumer<Transaction<BE>> action) {
            if (preCommitActions == null) {
                preCommitActions = new ArrayList<>();
            }

            preCommitActions.add(action);
        }

        /**
         * Use {@link #addPreCommitAction(Consumer)} to add an action to this list.
         *
         * @return the unmodifiable list of manually created pre-commit actions
         */
        public List<Consumer<Transaction<BE>>> getPreCommitActions() {
            return preCommitActions == null ? Collections.emptyList() : Collections.unmodifiableList(preCommitActions);
        }

        /**
         * In addition to "manual" ad-hoc pre-commit actions, it is possible to also define the actions using a
         * precommit action manager. This is meant to be used to handle stuff that is best done after a bulk of
         * work is done in a single transaction and needs to do this work based on what entities or relationships
         * have been created/updated/deleted.
         *
         * <p>The base implementation calls this manager to inform it of the creations/updates/deletions it does.
         *
         * @return the precommit action manager to use, never null
         */
        public PreCommit<BE> getPreCommit() {
            return preCommit;
        }

        public <R> R execute(PotentiallyCommittingPayload<R, BE> payload) throws CommitFailureException {
            return payload.run(this);
        }
    }
}
