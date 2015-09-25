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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.configuration.MapConfiguration;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.configuration.Configuration;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;

import com.thinkaurelius.titan.core.Cardinality;
import com.thinkaurelius.titan.core.Multiplicity;
import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.SchemaViolationException;
import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.schema.PropertyKeyMaker;
import com.thinkaurelius.titan.core.schema.TitanManagement;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class TitanProvider implements GraphProvider<TitanGraph> {

    private static class ExceptionMapper {
        private Class<? extends RuntimeException> targetException;
        private Predicate<RuntimeException> predicate;

        public ExceptionMapper(Class<? extends RuntimeException> targetException,
                               Predicate<RuntimeException> predicate) {
            this.targetException = targetException;
            this.predicate = predicate;
        }

        public Class<? extends RuntimeException> getTargetException() {
            return targetException;
        }

        public Predicate<RuntimeException> getPredicate() {
            return predicate;
        }
    }

    private static final Map<Class<? extends RuntimeException>, List<ExceptionMapper>> exceptionMapping =
            new HashMap<>();

    private static void mapException(Class<? extends RuntimeException> source, Class<? extends RuntimeException> target,
                                     Predicate<RuntimeException> p) {
        if (exceptionMapping.get(source) == null) {
            exceptionMapping.put(source, new ArrayList<>());
        }
        exceptionMapping.get(source).add(new ExceptionMapper(target, p));
    }

    static {
        try {
            mapException(SchemaViolationException.class, EntityAlreadyExistsException.class,
                    e -> e.getMessage().contains("violates a uniqueness constraint [by___cp]"));
        } catch (Throwable t) {
            // never fail during the class loading
        }
    }


    @Override
    public TitanGraph instantiateGraph(Configuration configuration) {
        return TitanFactory.open(new MapConfiguration(configuration.prefixedWith(ALLOWED_PREFIXES)
                .getImplementationConfiguration(EnumSet.allOf(PropertyKeys.class))));
    }

    @Override
    public void ensureIndices(TitanGraph graph, IndexSpec... indexSpecs) {
        Set<IndexSpec.Property> undefinedPropertyKeys = new HashSet<>();
        Map<String, PropertyKey> definedPropertyKeys = new HashMap<>();
        Map<String, IndexSpec> undefinedIndices = new HashMap<>();

        TitanManagement mgmt = graph.getManagementSystem();

        for (IndexSpec spec : indexSpecs) {
            String indexName = getIndexName(spec.getProperties());
            if (mgmt.getGraphIndex(indexName) == null) {
                undefinedIndices.put(indexName, spec);
            }

            //the indices might share keys, so we need to check for the keys even if the index doesn't exist
            for (IndexSpec.Property p : spec.getProperties()) {
                PropertyKey key = mgmt.getPropertyKey(p.getName());
                if (key == null) {
                    undefinedPropertyKeys.add(p);
                } else {
                    if (!key.getDataType().equals(p.getType())) {
                        throw new IllegalStateException("There already is a key '" + key.getName() +
                                "' that would be needed for index " + spec + ". The key has a different data type" +
                                " than expected, though. Expected: '" + p.getType() + "', actual: '" +
                                key.getDataType() + "'.");
                    }
                    definedPropertyKeys.put(p.getName(), key);
                }
            }
        }

        //first define all the undefined property keys
        for (IndexSpec.Property p : undefinedPropertyKeys) {
            PropertyKeyMaker propertyKeyMaker = mgmt.makePropertyKey(p.getName()).dataType(p.getType());
            if (null != p.getLabelIndex()) {
                propertyKeyMaker.signature(mgmt.makeEdgeLabel(p.getLabelIndex())
                        // index on directed edges doesn't work
                        .unidirected()
                        .multiplicity(Multiplicity.SIMPLE)
                        .make());
            }
            if (p.isUnique()) {
                propertyKeyMaker.cardinality(Cardinality.SINGLE);
            }
            PropertyKey key = propertyKeyMaker.make();
            definedPropertyKeys.put(p.getName(), key);
        }

        for (Map.Entry<String, IndexSpec> e : undefinedIndices.entrySet()) {
            TitanManagement.IndexBuilder bld = mgmt.buildIndex(e.getKey(), e.getValue().getElementType());
            if (e.getValue().isUnique()) {
                bld.unique();
            }
            for (IndexSpec.Property k : e.getValue().getProperties()) {
//                bld.addKey(definedPropertyKeys.get(k.getName()), Parameter.of("mapped-name", k.getName()));
                bld.addKey(definedPropertyKeys.get(k.getName()));
            }

            bld.buildCompositeIndex();
        }

        mgmt.commit();
    }

    private String getIndexName(Set<IndexSpec.Property> properties) {
        StringBuilder bld = new StringBuilder("by");

        for (IndexSpec.Property property : properties) {
            bld.append("_").append(property.getName());
        }

        return bld.toString();
    }

    public static final String[] ALLOWED_PREFIXES = {"attributes", "cache", "cluster", "graph", "ids", "index", "log" +
            "metrics", "query", "schema", "storage", "tx"};

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

    @Override
    public RuntimeException translateException(RuntimeException inputException) {
        List<ExceptionMapper> exceptionMappers = exceptionMapping.get(inputException.getClass());
        if (exceptionMappers != null) {
            Optional<RuntimeException> firstMatch =
                    exceptionMappers.stream()
                            .filter(mapper -> mapper.getPredicate().test(inputException))
                            .findFirst()
                            .map(mapper -> {
                                try {
                                    // todo: find the proper ctor based on parameter match
//                                    Arrays.stream(mapper.getTargetException().getConstructors()).forEach(
//                                            constructor ->  {
//
//                                                Class<?>[] params = constructor.getParameterTypes();
//                                            }
//                                    );
                                    return mapper.getTargetException().getConstructor(String.class).newInstance
                                            (inputException.getMessage());
                                } catch (Exception e) {
                                    return inputException;
                                }
                            });
            if (firstMatch.isPresent()) {
                return firstMatch.orElseGet(() -> inputException);
            }
        }
        return inputException;
    }
}
