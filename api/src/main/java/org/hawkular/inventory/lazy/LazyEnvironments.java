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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.lazy.spi.CanonicalPath;

import static org.hawkular.inventory.api.filters.With.id;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class LazyEnvironments {

    private LazyEnvironments() {

    }

    public static final class ReadWrite<BE> extends Mutator<BE, Environment, Environment.Blueprint, Environment.Update>
            implements Environments.ReadWrite {

        public ReadWrite(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Environment.Blueprint entity) {
            return entity.getId();
        }

        @Override
        protected void wireUpNewEntity(BE entity, Environment.Blueprint blueprint, CanonicalPath parentPath,
                BE parent) {
            //contains is already wired up by the superclass, we don't need anything else here.
        }

        @Override
        public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
            //TODO implement
            throw new UnsupportedOperationException();
        }

        @Override
        public Environments.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceed().where(filters).get());
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Environments.Single create(Environment.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static final class Single<BE> extends Fetcher<BE, Environment> implements Environments.Single {

        public Single(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.ReadWrite feeds() {
            //TODO implement
            return null;
        }

        @Override
        public Resources.ReadWrite feedlessResources() {
            //TODO implement
            return null;
        }

        @Override
        public Metrics.ReadWrite feedlessMetrics() {
            //TODO implement
            return null;
        }

        @Override
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            //TODO implement
            return null;
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            //TODO implement
            return null;
        }

        @Override
        public Relationships.ReadWrite relationships() {
            //TODO implement
            return null;
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            //TODO implement
            return null;
        }
    }

    public static final class Multiple<BE> extends Fetcher<BE, Environment> implements Environments.Multiple {

        public Multiple(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.Read feeds() {
            //TODO implement
            return null;
        }

        @Override
        public Resources.Read feedlessResources() {
            //TODO implement
            return null;
        }

        @Override
        public Metrics.Read feedlessMetrics() {
            //TODO implement
            return null;
        }

        @Override
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            //TODO implement
            return null;
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            //TODO implement
            return null;
        }

        @Override
        public Relationships.Read relationships() {
            //TODO implement
            return null;
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            //TODO implement
            return null;
        }
    }
}
