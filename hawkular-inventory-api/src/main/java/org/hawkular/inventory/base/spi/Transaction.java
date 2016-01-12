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
package org.hawkular.inventory.base.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.hawkular.inventory.base.EntityAndPendingNotifications;
import org.hawkular.inventory.base.PotentiallyCommittingPayload;

/**
 * Represents a transaction being performed. Implementations of the {@link InventoryBackend} interface are
 * encouraged to use the {@link #getAttachments()} method to add additional information to it.
 *
 * <p>The backends are required to execute all the pre-commit actions obtained from the
 * {@link PreCommit#getActions()} list.
 */
public interface Transaction<BE> {
    boolean isMutating();

    Map<Object, Object> getAttachments();

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
    PreCommit<BE> getPreCommit();

    <R> R execute(PotentiallyCommittingPayload<R, BE> payload) throws CommitFailureException;

    interface PreCommit<BE> {

        List<EntityAndPendingNotifications<BE, ?>> getFinalNotifications();

        void reset();

        void addAction(Consumer<Transaction<BE>> action);

        List<Consumer<Transaction<BE>>> getActions();

        void addNotifications(EntityAndPendingNotifications<BE, ?> element);

        class Simple<BE> implements PreCommit<BE> {
            private List<EntityAndPendingNotifications<BE, ?>> notifs = new ArrayList<>();
            private List<Consumer<Transaction<BE>>> actions = new ArrayList<>();

            @Override public void reset() {
                notifs.clear();
                actions.clear();
            }

            @Override public void addNotifications(EntityAndPendingNotifications<BE, ?> element) {
                notifs.add(element);
            }

            @Override public void addAction(Consumer<Transaction<BE>> action) {
                actions.add(action);
            }

            @Override public List<Consumer<Transaction<BE>>> getActions() {
                return actions;
            }

            @Override public List<EntityAndPendingNotifications<BE, ?>> getFinalNotifications() {
                return notifs;
            }
        }
    }
}
