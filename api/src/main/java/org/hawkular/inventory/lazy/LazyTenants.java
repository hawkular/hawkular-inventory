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
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class LazyTenants {

    private LazyTenants() {

    }

    public static final class ReadWrite<BE> extends Traversal<BE, Tenant> implements Tenants.ReadWrite {

        public ReadWrite(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        public Tenants.Multiple getAll(Filter... filters) {
            return new Multiple<>(context.proceedByPath().where(filters).get());
        }

        @Override
        public Tenants.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceedByPath().where(With.id(id)).get());
        }

        @Override
        public Tenants.Single create(Tenant.Blueprint blueprint) throws EntityAlreadyExistsException {
            //TODO implement
            return null;
        }

        @Override
        public void update(String id, Tenant.Update update) throws EntityNotFoundException {
            //TODO implement

        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            //TODO implement

        }
    }

    public static final class Multiple<BE> extends Traversal<BE, Tenant> implements Tenants.Multiple {

        public Multiple(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        public ResourceTypes.Read resourceTypes() {
            //TODO implement
            return null;
        }

        @Override
        public MetricTypes.Read metricTypes() {
            //TODO implement
            return null;
        }

        @Override
        public Environments.Read environments() {
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

        @Override
        public Page<Tenant> entities(Pager pager) {
            //TODO implement
            return null;
        }
    }

    public static final class Single<BE> extends Fetcher<BE, Tenant> implements Tenants.Single {

        public Single(TraversalContext<BE, Tenant> context) {
            super(context);
        }

        @Override
        public ResourceTypes.ReadWrite resourceTypes() {
            //TODO implement
            return null;
        }

        @Override
        public MetricTypes.ReadWrite metricTypes() {
            //TODO implement
            return null;
        }

        @Override
        public Environments.ReadWrite environments() {
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
}
