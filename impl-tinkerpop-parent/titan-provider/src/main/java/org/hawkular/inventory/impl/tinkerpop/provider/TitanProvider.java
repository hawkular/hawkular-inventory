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
package org.hawkular.inventory.impl.tinkerpop.provider;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.MapConfiguration;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.schema.TitanManagement;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class TitanProvider implements GraphProvider<TitanGraph> {
    @Override
    public TitanGraph instantiateGraph(Configuration configuration) {
        return TitanFactory.open(new MapConfiguration(configuration.getImplementationConfiguration(
                EnumSet.allOf(PropertyKeys.class))));
    }

    @Override
    public void ensureIndices(TitanGraph graph, IndexSpec... indexSpecs) {
        Map<String, Class<?>> undefinedPropertyKeys = new HashMap<>();
        Map<String, PropertyKey> definedPropertyKeys = new HashMap<>();
        Map<String, IndexSpec> undefinedIndices = new HashMap<>();

        TitanManagement mgmt = graph.getManagementSystem();

        for (IndexSpec spec : indexSpecs) {
            String indexName = getIndexName(spec.getProperties().keySet());
            if (mgmt.getGraphIndex(indexName) == null) {
                undefinedIndices.put(indexName, spec);
            }

            //the indices might share keys, so we need to check for the keys even if the index doesn't exist
            for (Map.Entry<String, Class<?>> p : spec.getProperties().entrySet()) {
                PropertyKey key = mgmt.getPropertyKey(p.getKey());
                if (key == null) {
                    undefinedPropertyKeys.put(p.getKey(), p.getValue());
                } else {
                    if (!key.getDataType().equals(p.getValue())) {
                        throw new IllegalStateException("There already is a key '" + key.getName() +
                                "' that would be needed for index " + spec + ". The key has a different data type" +
                                " than expected, though. Expected: '" + p.getValue() + "', actual: '" +
                                key.getDataType() + "'.");
                    }
                    definedPropertyKeys.put(p.getKey(), key);
                }
            }
        }

        //first define all the undefined property keys
        for (Map.Entry<String, Class<?>> pk : undefinedPropertyKeys.entrySet()) {
            PropertyKey key = mgmt.makePropertyKey(pk.getKey()).dataType(pk.getValue()).make();

            definedPropertyKeys.put(pk.getKey(), key);
        }

        for(Map.Entry<String, IndexSpec> e : undefinedIndices.entrySet()) {
            TitanManagement.IndexBuilder bld = mgmt.buildIndex(e.getKey(), e.getValue().getElementType());

            for (String k : e.getValue().getProperties().keySet()) {
                bld.addKey(definedPropertyKeys.get(k));
            }

            bld.buildCompositeIndex();
        }

        mgmt.commit();
    }

    private String getIndexName(Iterable<String> propertyNames) {
        StringBuilder bld = new StringBuilder("by");

        for (String propertyName : propertyNames) {
            bld.append("_").append(propertyName);
        }

        return bld.toString();
    }

    @SuppressWarnings("unused")
    private enum PropertyKeys implements Configuration.Property {
        STORAGE_HOSTNAME("storage.hostname", "hawkular.inventory.titan.storage.hostname",
                "HAWKULAR_INVENTORY_TITAN_STORAGE_HOSTNAME", "CASSANDRA_NODES"),
        STORAGE_PORT("storage.port", "hawkular.inventory.titan.storage.port", "HAWKULAR_INVENTORY_TITAN_STORAGE_PORT"),
        STORAGE_CASSANDRA_KEYSPACE("storage.cassandra.keyspace", "hawkular.inventory.titan.storage.cassandra.keyspace",
                "HAWKULAR_INVENTORY_TITAN_STORAGE_CASSANDRA_KEYSPACE");

        private final String propertyName;
        private final List<String> systemPropertyName;
        private final List<String> environmentVariableName;

        PropertyKeys(String propertyName, String systemPropertyName, String... environmentVariableName) {
            this.propertyName = propertyName;
            this.systemPropertyName = Collections.unmodifiableList(Collections.singletonList(systemPropertyName));
            this.environmentVariableName = Collections.unmodifiableList(Arrays.asList(environmentVariableName));
        }

        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public List<String> getSystemPropertyNames() {
            return systemPropertyName;
        }

        @Override
        public List<String> getEnvironmentVariableNames() {
            return environmentVariableName;
        }
    }
}
