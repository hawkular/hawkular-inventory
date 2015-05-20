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

import org.hawkular.inventory.api.model.Feed;

import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class ObservableFeeds {

    private ObservableFeeds() {

    }

    static final class Read extends ObservableBase.Read<Feeds.Single, Feeds.Multiple, Feeds.Read>
            implements Feeds.Read {

        Read(Feeds.Read wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Feeds.Single, ObservableContext, ? extends Feeds.Single> singleCtor() {
            return ObservableFeeds.Single::new;
        }

        @Override
        protected BiFunction<Feeds.Multiple, ObservableContext, ? extends Feeds.Multiple> multipleCtor() {
            return ObservableFeeds.Multiple::new;
        }
    }

    static final class ReadWrite extends ObservableBase.ReadWrite<Feed, Feed.Blueprint, Feed.Update,
            Feeds.Single, Feeds.Multiple, Feeds.ReadWrite> implements Feeds.ReadWrite {

        ReadWrite(Feeds.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Feeds.Single, ObservableContext, ? extends Feeds.Single> singleCtor() {
            return ObservableFeeds.Single::new;
        }

        @Override
        protected BiFunction<Feeds.Multiple, ObservableContext, ? extends Feeds.Multiple> multipleCtor() {
            return ObservableFeeds.Multiple::new;
        }
    }

    static final class Single extends ObservableBase.RelatableSingle<Feed, Feeds.Single>
            implements Feeds.Single {

        Single(Feeds.Single wrapped, ObservableContext context) {
            super(wrapped, context);
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

    static final class Multiple extends ObservableBase.RelatableMultiple<Feed, Feeds.Multiple>
            implements Feeds.Multiple {

        Multiple(Feeds.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableResources.Read resources() {
            return wrap(ObservableResources.Read::new, wrapped.resources());
        }

        @Override
        public Metrics.Read metrics() {
            return wrap(ObservableMetrics.Read::new, wrapped.metrics());
        }
    }
}
