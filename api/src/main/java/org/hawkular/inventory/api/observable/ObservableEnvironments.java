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
package org.hawkular.inventory.api.observable;

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.model.Environment;

import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class ObservableEnvironments {
    private ObservableEnvironments() {

    }

    public static final class Read
            extends ObservableBase.Read<Environments.Single, Environments.Multiple, Environments.Read>
            implements Environments.Read {

        Read(Environments.Read wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Environments.Single, ObservableContext, ? extends Environments.Single> singleCtor() {
            return ObservableEnvironments.Single::new;
        }

        @Override
        protected BiFunction<Environments.Multiple, ObservableContext, ? extends Environments.Multiple> multipleCtor() {
            return ObservableEnvironments.Multiple::new;
        }
    }

    public static final class ReadWrite
            extends ObservableBase.ReadWrite<Environment, Environment.Blueprint, Environments.Single,
            Environments.Multiple, Environments.ReadWrite> implements Environments.ReadWrite {

        ReadWrite(Environments.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Environments.Single, ObservableContext, ? extends Environments.Single> singleCtor() {
            return ObservableEnvironments.Single::new;
        }

        @Override
        protected BiFunction<Environments.Multiple, ObservableContext, ? extends Environments.Multiple> multipleCtor() {
            return ObservableEnvironments.Multiple::new;
        }

        @Override
        public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
            wrapped.copy(sourceEnvironmentId, targetEnvironmentId);
            Environment s = get(sourceEnvironmentId).entity();
            Environment t = get(targetEnvironmentId).entity();
            notify(t, Action.created());
            notify(s, new Action.EnvironmentCopy(s, t), Action.copied());
        }
    }

    public static final class Single extends ObservableBase.RelatableSingle<Environment, Environments.Single>
            implements Environments.Single {

        Single(Environments.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableFeeds.ReadAndRegister feeds() {
            return wrap(ObservableFeeds.ReadAndRegister::new, wrapped.feeds());
        }

        @Override
        public ObservableResources.ReadWrite resources() {
            return wrap(ObservableResources.ReadWrite::new, wrapped.resources());
        }

        @Override
        public ObservableMetrics.ReadWrite metrics() {
            return wrap(ObservableMetrics.ReadWrite::new, wrapped.metrics());
        }
    }

    public static final class Multiple extends ObservableBase.RelatableMultiple<Environment, Environments.Multiple>
            implements Environments.Multiple {

        Multiple(Environments.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableFeeds.Read feeds() {
            return wrap(ObservableFeeds.Read::new, wrapped.feeds());
        }

        @Override
        public ObservableResources.Read resources() {
            return wrap(ObservableResources.Read::new, wrapped.resources());
        }

        @Override
        public ObservableMetrics.Read metrics() {
            return wrap(ObservableMetrics.Read::new, wrapped.metrics());
        }
    }
}
