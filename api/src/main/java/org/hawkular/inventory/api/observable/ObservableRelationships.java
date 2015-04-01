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
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;

import java.util.Set;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class ObservableRelationships {

    public static final class ReadWrite extends ObservableBase<Relationships.ReadWrite>
            implements Relationships.ReadWrite {

        ReadWrite(Relationships.ReadWrite wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public Relationships.Multiple named(String name) {
            return wrap(Multiple::new, wrapped.named(name));
        }

        @Override
        public Relationships.Multiple named(Relationships.WellKnown name) {
            return wrap(Multiple::new, wrapped.named(name));
        }

        @Override
        public Relationships.Single get(String id) throws EntityNotFoundException, RelationNotFoundException {
            return wrap(Single::new, wrapped.get(id));
        }

        @Override
        public Relationships.Multiple getAll(RelationFilter... filters) {
            return wrap(Multiple::new, wrapped.getAll(filters));
        }

        @Override
        public Relationships.Single linkWith(String name, Entity targetOrSource) throws IllegalArgumentException {
            return wrapAndNotify(Single::new, wrapped.linkWith(name, targetOrSource), Interest.Action.CREATE);
        }

        @Override
        public Relationships.Single linkWith(Relationships.WellKnown name, Entity targetOrSource) throws IllegalArgumentException {
            return linkWith(name.name(), targetOrSource);
        }

        @Override
        public void update(Relationship relationship) throws RelationNotFoundException {
            wrapped.update(relationship);
            notify(relationship, Interest.Action.UPDATE);
        }

        @Override
        public void delete(String id) throws RelationNotFoundException {
            Relationship r = get(id).entity();
            wrapped.delete(id);
            notify(r, Interest.Action.DELETE);
        }
    }

    public static final class Single extends ObservableBase<Relationships.Single> implements Relationships.Single {

        Single(Relationships.Single wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public Relationship entity() {
            return wrapped.entity();
        }
    }

    public static final class Multiple extends ObservableBase<Relationships.Multiple>
            implements Relationships.Multiple {

        Multiple(Relationships.Multiple wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        @Override
        public Tenants.Read tenants() {
            //TODO implement
            return null;
        }

        @Override
        public Environments.Read environments() {
            //TODO implement
            return null;
        }

        @Override
        public Feeds.Read feeds() {
            //TODO implement
            return null;
        }

        @Override
        public MetricTypes.Read metricTypes() {
            //TODO implement
            return null;
        }

        @Override
        public Metrics.Read metrics() {
            //TODO implement
            return null;
        }

        @Override
        public Resources.Read resources() {
            //TODO implement
            return null;
        }

        @Override
        public ResourceTypes.Read resourceTypes() {
            //TODO implement
            return null;
        }

        @Override
        public Set<Relationship> entities() {
            return wrapped.entities();
        }
    }
}
