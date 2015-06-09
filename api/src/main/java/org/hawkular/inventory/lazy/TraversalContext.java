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

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.lazy.spi.LazyInventoryBackend;

import java.util.function.Function;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
final class TraversalContext<BE, E extends AbstractElement<?, ?>> {
    protected final QueryFragmentTree sourcePath;
    protected final QueryFragmentTree selectCandidates;
    protected final LazyInventoryBackend<BE> backend;
    protected final Class<E> entityClass;
    protected final Configuration configuration;

    TraversalContext(QueryFragmentTree sourcePath, QueryFragmentTree selectCandidates, LazyInventoryBackend<BE> backend,
            Class<E> entityClass, Configuration configuration) {
        this.sourcePath = sourcePath;
        this.selectCandidates = selectCandidates;
        this.backend = backend;
        this.entityClass = entityClass;
        this.configuration = configuration;
    }

    <T extends Entity<?, ?>> Builder<BE, T> proceedBySelect(Class<T> entityClass) {
        return new Builder<>(sourcePath.extend(), QueryFragmentTree.empty().extend(), backend, entityClass,
                configuration, false).where(With.type(entityClass));
    }

    Builder<BE, E> proceedByPath() {
        return new Builder<>(sourcePath.extend().with(selectCandidates), QueryFragmentTree.empty().extend(), backend,
                entityClass, configuration, true);
    }

    TraversalContext<BE, E> replacePath(QueryFragmentTree path) {
        return new TraversalContext<>(path, QueryFragmentTree.empty(), backend, entityClass, configuration);
    }

    public static final class Builder<BE, E extends AbstractElement<?, ?>> {
        private final QueryFragmentTree.SymmetricExtender pathExtender;
        private final QueryFragmentTree.SymmetricExtender selectExtender;
        private final QueryFragmentTree.SymmetricExtender workingExtender;
        private final LazyInventoryBackend<BE> backend;
        private final Class<E> entityClass;
        private final Configuration configuration;
        private final Function<Filter[], QueryFragment[]> queryFragmentSupplier;

        public Builder(QueryFragmentTree.SymmetricExtender pathExtender,
                QueryFragmentTree.SymmetricExtender selectExtender, LazyInventoryBackend<BE> backend,
                Class<E> entityClass, Configuration configuration, boolean extendPath) {
            this.pathExtender = pathExtender;
            this.selectExtender = selectExtender;
            this.backend = backend;
            this.entityClass = entityClass;
            this.configuration = configuration;
            if (extendPath) {
                workingExtender = pathExtender;
                queryFragmentSupplier = PathFragment::from;
            } else {
                workingExtender = selectExtender;
                queryFragmentSupplier = FilterFragment::from;
            }
        }

        public Builder<BE, E> where(Filter[][] filters) {
            workingExtender.with(filters, queryFragmentSupplier);
            return this;
        }

        public Builder<BE, E> where(Filter... filters) {
            workingExtender.with(filters, queryFragmentSupplier);
            return this;
        }

        TraversalContext<BE, E> get() {
            return new TraversalContext<>(pathExtender.get(), selectExtender.get(), backend, entityClass,
                    configuration);
        }
    }
}
