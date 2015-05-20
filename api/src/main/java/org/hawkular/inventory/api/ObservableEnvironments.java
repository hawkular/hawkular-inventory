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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.Environment;

import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class ObservableEnvironments {
    private ObservableEnvironments() {

    }

    static final class Read
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

    static final class ReadWrite
            extends ObservableBase.ReadWrite<Environment, Environment.Blueprint, Environment.Update,
            Environments.Single, Environments.Multiple, Environments.ReadWrite> implements Environments.ReadWrite {

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

    static final class Single extends ObservableBase.RelatableSingle<Environment, Environments.Single>
            implements Environments.Single {

        Single(Environments.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableFeeds.ReadWrite feeds() {
            return wrap(ObservableFeeds.ReadWrite::new, wrapped.feeds());
        }

        @Override
        public ObservableResources.ReadWrite feedlessResources() {
            return wrap(ObservableResources.ReadWrite::new, wrapped.feedlessResources());
        }

        @Override
        public ObservableMetrics.ReadWrite feedlessMetrics() {
            return wrap(ObservableMetrics.ReadWrite::new, wrapped.feedlessMetrics());
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            return wrap(ObservableMetrics.ReadMultiple::new, wrapped.allMetrics());
        }

        @Override
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            return wrap(ObservableResources.ReadMultiple::new, wrapped.allResources());
        }
    }

    static final class Multiple extends ObservableBase.RelatableMultiple<Environment, Environments.Multiple>
            implements Environments.Multiple {

        Multiple(Environments.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableFeeds.Read feeds() {
            return wrap(ObservableFeeds.Read::new, wrapped.feeds());
        }

        @Override
        public ObservableResources.Read feedlessResources() {
            return wrap(ObservableResources.Read::new, wrapped.feedlessResources());
        }

        @Override
        public ObservableMetrics.Read feedlessMetrics() {
            return wrap(ObservableMetrics.Read::new, wrapped.feedlessMetrics());
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            return wrap(ObservableMetrics.ReadMultiple::new, wrapped.allMetrics());
        }

        @Override
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            return wrap(ObservableResources.ReadMultiple::new, wrapped.allResources());
        }
    }
}
