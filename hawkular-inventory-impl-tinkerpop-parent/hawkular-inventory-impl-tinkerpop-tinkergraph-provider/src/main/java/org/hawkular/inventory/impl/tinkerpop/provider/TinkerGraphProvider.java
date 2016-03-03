/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.impl.tinkerpop.provider;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.configuration.MapConfiguration;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

import com.tinkerpop.blueprints.ThreadedTransactionalGraph;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.wrapped.WrappedGraph;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class TinkerGraphProvider implements GraphProvider {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override public boolean isUniqueIndexSupported() {
        return false;
    }

    @Override public boolean needsDraining() {
        return false;
    }

    @Override public boolean isPreferringBigTransactions() {
        //just for testing purposes... Otherwise tinkergraph doesn't actually care about this because it doesn't
        //support transactions anyway.
        return true;
    }

    @Override
    public WrappedTinkerGraph instantiateGraph(Configuration configuration) {
        return new WrappedTinkerGraph(new MapConfiguration(
                configuration.getImplementationConfiguration(
                        Collections.singleton(PropertyKey.DIRECTORY_NAME))));
    }

    @Override
    public void ensureIndices(TransactionalGraph graph, IndexSpec... indexSpecs) {
        //don't bother with this for a demo graph
    }

    @Override
    public TransactionalGraph startTransaction(TransactionalGraph graph) {
        lock.writeLock().lock();
        return graph;
    }

    @Override
    public void commit(TransactionalGraph graph) {
        lock.writeLock().unlock();
    }

    @Override
    public void rollback(TransactionalGraph graph) {
        lock.writeLock().unlock();
    }

    public static final class WrappedTinkerGraph extends WrappedGraph<TinkerGraph> implements
            ThreadedTransactionalGraph {

        public WrappedTinkerGraph(org.apache.commons.configuration.Configuration configuration) {
            super(new TinkerGraph(configuration));
        }

        @Override public TransactionalGraph newTransaction() {
            return this;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void stopTransaction(Conclusion conclusion) {
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }

    private enum PropertyKey implements Configuration.Property {
        DIRECTORY_NAME("blueprints.tg.directory", "blueprints.tg.directory", null);

        private final String propertyName;
        private final List<String> sysPropName;
        private final List<String> envVarName;

        PropertyKey(String propertyName, String sysPropName, String envVarName) {
            this.envVarName = envVarName == null ? Collections.emptyList() : Collections.singletonList(envVarName);
            this.propertyName = propertyName;
            this.sysPropName = sysPropName == null ? Collections.emptyList() : Collections.singletonList(sysPropName);
        }


        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public List<String> getSystemPropertyNames() {
            return sysPropName;
        }

        @Override
        public List<String> getEnvironmentVariableNames() {
            return envVarName;
        }
    }
}
