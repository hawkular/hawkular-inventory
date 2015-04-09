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

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Feed;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class ObservableFeeds {

    private ObservableFeeds() {

    }

    public static final class Read extends ObservableBase.Read<Feeds.Single, Feeds.Multiple, Feeds.Read>
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

    public static final class ReadAndRegister extends ObservableBase<Feeds.ReadAndRegister>
            implements Feeds.ReadAndRegister {

        ReadAndRegister(Feeds.ReadAndRegister wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public Feeds.Single register(String proposedId, Map<String, Object> properties) {
            return wrap(ObservableFeeds.Single::new, wrapped.register(proposedId, null));
        }

        @Override
        public Feeds.Single get(String id) throws EntityNotFoundException, RelationNotFoundException {
            return wrap(ObservableFeeds.Single::new, wrapped.get(id));
        }

        @Override
        public Feeds.Multiple getAll(Filter... filters) {
            return wrap(ObservableFeeds.Multiple::new, wrapped.getAll(filters));
        }
    }

    public static final class Single extends ObservableBase.RelatableSingle<Feed, Feeds.Single>
            implements Feeds.Single {

        Single(Feeds.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableResources.Read resources() {
            return wrap(ObservableResources.Read::new, wrapped.resources());
        }
    }

    public static final class Multiple extends ObservableBase.RelatableMultiple<Feed, Feeds.Multiple>
            implements Feeds.Multiple {

        Multiple(Feeds.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableResources.Read resources() {
            return wrap(ObservableResources.Read::new, wrapped.resources());
        }
    }
}
