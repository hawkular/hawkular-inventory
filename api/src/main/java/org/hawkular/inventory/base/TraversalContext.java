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
package org.hawkular.inventory.base;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.base.spi.SwitchElementType;
import rx.subjects.Subject;

import java.util.Iterator;

import static org.hawkular.inventory.api.filters.With.type;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public final class TraversalContext<BE, E extends AbstractElement<?, ?>> {
    protected final BaseInventory<BE> inventory;
    protected final Query sourcePath;
    protected final Query selectCandidates;
    protected final InventoryBackend<BE> backend;
    protected final Class<E> entityClass;
    protected final Configuration configuration;
    private final ObservableContext observableContext;

    TraversalContext(BaseInventory<BE> inventory, Query sourcePath, Query selectCandidates,
            InventoryBackend<BE> backend,
            Class<E> entityClass, Configuration configuration, ObservableContext observableContext) {
        this.inventory = inventory;
        this.sourcePath = sourcePath;
        this.selectCandidates = selectCandidates;
        this.backend = backend;
        this.entityClass = entityClass;
        this.configuration = configuration;
        this.observableContext = observableContext;
    }

    Builder<BE, E> proceed() {
        return new Builder<>(inventory, hop(), Query.filter(), backend, entityClass, configuration,
                observableContext);
    }

    <T extends Entity<?, ?>> Builder<BE, T> proceedTo(Relationships.WellKnown over, Class<T> entityType) {
        return new Builder<>(inventory, hop(), Query.filter(), backend, entityType, configuration,
                observableContext)
                .where(Related.by(over), type(entityType));
    }

    Builder<BE, Relationship> proceedToRelationships(Relationships.Direction direction) {
        return new Builder<>(inventory, hop(), Query.filter()
                .with(new SwitchElementType(direction, false)), backend, Relationship.class, configuration,
                observableContext);
    }

    <T extends Entity<?, ?>> Builder<BE, T> proceedFromRelationshipsTo(Relationships.Direction direction,
            Class<T> entityType) {
        return new Builder<>(inventory, hop().with(new SwitchElementType(direction, true)), Query.filter(),
                backend, entityType, configuration, observableContext).where(type(entityType));
    }

    Query.SymmetricExtender select() {
        return sourcePath.extend().filter().with(selectCandidates);
    }

    Query.SymmetricExtender hop() {
        return sourcePath.extend().path().with(selectCandidates);
    }

    TraversalContext<BE, E> replacePath(Query path) {
        return new TraversalContext<>(inventory, path, Query.empty(), backend, entityClass, configuration,
                observableContext);
    }

    protected <V> void notify(V entity, Action<V, V> action) {
        notify(entity, entity, action);
    }

    protected <C, V> void notify(V entity, C actionContext, Action<C, V> action) {
        Iterator<Subject<C, C>> subjects = observableContext.matchingSubjects(action, entity);
        while (subjects.hasNext()) {
            Subject<C, C> s = subjects.next();
            s.onNext(actionContext);
        }
    }

    public static final class Builder<BE, E extends AbstractElement<?, ?>> {
        private final BaseInventory<BE> inventory;
        private final Query.SymmetricExtender pathExtender;
        private final Query.SymmetricExtender selectExtender;
        private final InventoryBackend<BE> backend;
        private final Class<E> entityClass;
        private final Configuration configuration;
        private final ObservableContext observableContext;

        public Builder(BaseInventory<BE> inventory, Query.SymmetricExtender pathExtender,
                Query.SymmetricExtender selectExtender, InventoryBackend<BE> backend,
                Class<E> entityClass, Configuration configuration, ObservableContext observableContext) {
            this.inventory = inventory;
            this.pathExtender = pathExtender;
            this.selectExtender = selectExtender;
            this.backend = backend;
            this.entityClass = entityClass;
            this.configuration = configuration;
            this.observableContext = observableContext;
        }

        public Builder<BE, E> where(Filter[][] filters) {
            selectExtender.filter().with(filters);
            return this;
        }

        public Builder<BE, E> where(Filter... filters) {
            selectExtender.filter().with(filters);
            return this;
        }

        TraversalContext<BE, E> get() {
            return new TraversalContext<>(inventory, pathExtender.get(), selectExtender.get(), backend, entityClass,
                    configuration, observableContext);
        }

        <T extends AbstractElement<?, ?>> TraversalContext<BE, T> getting(Class<T> entityType) {
            return new TraversalContext<>(inventory, pathExtender.get(), selectExtender.get(), backend, entityType,
                    configuration, observableContext);
        }
    }
}
