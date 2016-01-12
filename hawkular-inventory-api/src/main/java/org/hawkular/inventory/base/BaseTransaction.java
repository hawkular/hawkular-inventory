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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.hawkular.inventory.base.spi.CommitFailureException;
import org.hawkular.inventory.base.spi.Transaction;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
class BaseTransaction<BE> implements Transaction<BE> {

    private final boolean mutating;
    private List<Consumer<Transaction<BE>>> preCommitActions;
    private final PreCommit<BE> preCommit;
    private final HashMap<Object, Object> attachments = new HashMap<>(1);

    BaseTransaction(boolean mutating) {
        this(mutating, new PreCommit.Simple<>());
    }

    BaseTransaction(boolean mutating, PreCommit<BE> preCommit) {
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
