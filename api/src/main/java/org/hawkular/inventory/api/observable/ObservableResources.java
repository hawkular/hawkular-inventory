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

import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.Resource;

import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class ObservableResources {
    private ObservableResources() {

    }

    public static final class ReadMultiple
            extends ObservableBase.ReadMultiple<Resources.Multiple, ResolvingToMultiple<Resources.Multiple>>
            implements ResolvingToMultiple<Resources.Multiple> {

        ReadMultiple(ResolvingToMultiple<Resources.Multiple> wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Resources.Multiple, ObservableContext, ? extends Resources.Multiple> multipleCtor() {
            return ObservableResources.Multiple::new;
        }
    }

    public static final class Read extends ObservableBase.Read<Resources.Single, Resources.Multiple, Resources.Read>
        implements Resources.Read {

        Read(Resources.Read wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Resources.Single, ObservableContext, ? extends Resources.Single> singleCtor() {
            return ObservableResources.Single::new;
        }

        @Override
        protected BiFunction<Resources.Multiple, ObservableContext, ? extends Resources.Multiple> multipleCtor() {
            return ObservableResources.Multiple::new;
        }
    }

    public static final class ReadWrite extends ObservableBase.ReadWrite<Resource, Resource.Blueprint, Resource.Update,
            Resources.Single, Resources.Multiple, Resources.ReadWrite> implements Resources.ReadWrite {

        ReadWrite(Resources.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        protected BiFunction<Resources.Single, ObservableContext, ? extends Resources.Single> singleCtor() {
            return ObservableResources.Single::new;
        }

        @Override
        protected BiFunction<Resources.Multiple, ObservableContext, ? extends Resources.Multiple> multipleCtor() {
            return ObservableResources.Multiple::new;
        }
    }

    public static final class Single extends ObservableBase.RelatableSingle<Resource, Resources.Single>
        implements Resources.Single {

        Single(Resources.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableMetrics.ReadAssociate metrics() {
            return wrap(ObservableMetrics.ReadAssociate::new, wrapped.metrics());
        }
    }

    public static final class Multiple extends ObservableBase.RelatableMultiple<Resource, Resources.Multiple>
        implements Resources.Multiple {

        Multiple(Resources.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public ObservableMetrics.Read metrics() {
            return wrap(ObservableMetrics.Read::new, wrapped.metrics());
        }
    }
}
