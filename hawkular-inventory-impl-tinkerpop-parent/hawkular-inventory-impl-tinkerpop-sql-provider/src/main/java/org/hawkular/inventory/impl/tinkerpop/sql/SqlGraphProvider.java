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
package org.hawkular.inventory.impl.tinkerpop.sql;

import static java.util.stream.Collectors.toMap;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.Relationships.WellKnown.isParentOf;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.InternalEdge.__containsIdentityHash;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.InternalEdge.__withIdentityHash;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__identityHash;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.impl.tinkerpop.spi.Constants;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;
import org.hawkular.inventory.paths.CanonicalPath;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgExceptions;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * This is a "toy" provider for Hawkular that uses the primitive Blueprints implementation for an RDBMS. It only
 * supports H2 and Postgres and should not be used for anything but playful experiments.
 * <p>
 * That said, its main use is for checking the correct transactional behavior of Hawkular, because especially H2
 * seems to be quite sensitive about accessing ResultSets of closed transactions etc, which is a great testbed for
 * Hawkular's manual transaction handling.
 *
 * @author Lukas Krejci
 * @since 0.13.0
 */
public class SqlGraphProvider implements GraphProvider {
    @Override public boolean isPreferringBigTransactions() {
        return false;
    }

    @Override public boolean isUniqueIndexSupported() {
        return true;
    }

    @Override public boolean needsDraining() {
        return true;
    }

    @Override public SqlgGraph instantiateGraph(Configuration configuration) {
        try {
            Map<String, String> conf = configuration.prefixedWith("sql.")
                    .getImplementationConfiguration(allProperties());

            //Sqlg doesn't use any common prefix to the configuration properties it expects. We want that though, so
            //let's just remove the "sql." prefix from all the props and pass it to sqlg.
            conf = conf.entrySet().stream().collect(
                    toMap(e -> e.getKey().startsWith("sql.") ? e.getKey().substring(4) : e.getKey(),
                            Map.Entry::getValue));

            return SqlgGraph.open(new MapConfiguration(conf));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate the SQL graph.", e);
        }
    }

    @Override public void ensureIndices(Graph graph, IndexSpec... indexSpecs) {
        SqlgGraph sqlg = (SqlgGraph) graph;

        ensureSchema(sqlg);

        ArrayList<IndexSpec> specs = new ArrayList<>(Arrays.asList(indexSpecs));

        String[] entityLabels =
                Stream.of(Constants.Type.values()).filter(t -> Entity.class.isAssignableFrom(t.getEntityType()))
                        .map(Enum::name).toArray(String[]::new);

        sqlg.tx().open();

        Iterator<IndexSpec> it = specs.iterator();
        while (it.hasNext()) {
            IndexSpec is = it.next();

            if (is.getProperties().stream().filter(IndexSpec.Property::isUnique).count() > 1) {
                throw new IllegalArgumentException("SQL Graph doesn't support unique indices over multiple " +
                        "properties");
            }

            it.remove();

            ArrayList<Object> keyValues = new ArrayList<>(is.getProperties().size() * 2);
            for (IndexSpec.Property p : is.getProperties()) {
                keyValues.add(p.getName());
                keyValues.add(sampleValue(p.getType()));
            }

            if (Vertex.class.equals(is.getElementType())) {
                for (String l : entityLabels) {
                    sqlg.createVertexLabeledIndex(l, keyValues.toArray());
                }

                is.getProperties().stream().filter(IndexSpec.Property::isUnique)
                        .findAny().ifPresent(p -> sqlg.createVertexUniqueConstraint(p.getName(), entityLabels));
            } else {
                //This is not working yet in Sqlg
                //sqlg.createEdgeLabeledIndex(Edge.DEFAULT_LABEL, keyValues.toArray());
            }
        }

        sqlg.tx().commit();
    }

    private static void ensureSchema(SqlgGraph graph) {
        String schema = graph.getSqlDialect().getPublicSchema();

        Function<String, Object> propertySampleValue = p ->
                sampleValue(Constants.Property.valueOf(p).getPropertyType());

        graph.tx().open();

        //tables for entity types
        for (Constants.Type t : Constants.Type.values()) {
            if (t == Constants.Type.relationship) {
                continue;
            }
            //get all the properties of this type and replace each with itself and a default value of it so that
            //we can properly define the tables
            Object[] props = Stream.of(t.getMappedProperties())
                    .flatMap(p -> Stream.of(p, propertySampleValue.apply(p))).toArray();

            graph.getSchemaManager().ensureVertexTableExist(schema, t.name(), props);
        }

        //table for the identity hash storage
        graph.getSchemaManager()
                .ensureVertexTableExist(schema, Constants.InternalType.__identityHash.name(), __type.name(), "",
                        __identityHash.name(), "");


        graph.tx().commit();

        SchemaTable tenant = SchemaTable.from(graph, Constants.Type.tenant.name(), schema);
        SchemaTable feed = SchemaTable.from(graph, Constants.Type.feed.name(), schema);
        SchemaTable environment = SchemaTable.from(graph, Constants.Type.environment.name(), schema);
        SchemaTable resourceType = SchemaTable.from(graph, Constants.Type.resourceType.name(), schema);
        SchemaTable metricType = SchemaTable.from(graph, Constants.Type.metricType.name(), schema);
        SchemaTable operationType = SchemaTable.from(graph, Constants.Type.operationType.name(), schema);
        SchemaTable metadataPack = SchemaTable.from(graph, Constants.Type.metadatapack.name(), schema);
        SchemaTable resource = SchemaTable.from(graph, Constants.Type.resource.name(), schema);
        SchemaTable metric = SchemaTable.from(graph, Constants.Type.metric.name(), schema);
        SchemaTable data = SchemaTable.from(graph, Constants.Type.dataEntity.name(), schema);
        SchemaTable structuredData = SchemaTable.from(graph, Constants.Type.structuredData.name(), schema);
        SchemaTable identityHash = SchemaTable.from(graph, Constants.InternalType.__identityHash.name(), schema);

        Object[] relProps =
                Stream.of(Constants.Type.relationship.getMappedProperties())
                        .flatMap(p -> Stream.of(p, propertySampleValue.apply(p))).toArray();

        AddEdgeHelper<SchemaTable, Enum<?>, SchemaTable> edges = (out, rel, in, props) -> {
            if (props == null) {
                props = new Object[0];
            } else if (props.length == 0) {
                props = relProps;
            }
            graph.getSchemaManager().ensureEdgeTableExist(schema, rel.name(), in, out, props);
        };

        graph.tx().open();

        //tenant relationships
        edges.add(tenant, contains, environment);
        edges.add(tenant, contains, metadataPack);
        edges.add(tenant, contains, feed);
        edges.add(tenant, contains, resourceType);
        edges.add(tenant, contains, metricType);
        edges.add(tenant, __containsIdentityHash, identityHash, Constants.Property.__targetIdentityHash.name(), "");

        //environment relationships
        edges.add(environment, contains, resource);
        edges.add(environment, contains, metric);
        edges.add(environment, incorporates, feed);

        //feed relationships
        edges.add(feed, contains, resourceType);
        edges.add(feed, contains, metricType);
        edges.add(feed, contains, resource);
        edges.add(feed, contains, metric);
        edges.add(feed, __withIdentityHash, identityHash, (Object[]) null);

        //resource type relationships
        edges.add(resourceType, contains, operationType);
        edges.add(resourceType, contains, data);
        edges.add(resourceType, defines, resource);
        edges.add(resourceType, incorporates, metricType);
        edges.add(resourceType, __withIdentityHash, identityHash, (Object[]) null);

        //metric type relationships
        edges.add(metricType, defines, metric);
        edges.add(metricType, __withIdentityHash, identityHash, (Object[]) null);

        //operation type relationships
        edges.add(operationType, contains, data);
        edges.add(operationType, __withIdentityHash, identityHash, (Object[]) null);

        //metadata pack relationships
        edges.add(metadataPack, incorporates, resourceType);
        edges.add(metadataPack, incorporates, metricType);

        //resource relationships
        edges.add(resource, contains, resource);
        edges.add(resource, contains, data);
        edges.add(resource, contains, metric);
        edges.add(resource, isParentOf, resource);
        edges.add(resource, incorporates, metric);
        edges.add(resource, __withIdentityHash, identityHash, (Object[]) null);

        //metric relationships
        edges.add(metric, __withIdentityHash, identityHash, (Object[]) null);

        //data entity relationships
        edges.add(data, hasData, structuredData);
        edges.add(data, __withIdentityHash, identityHash, (Object[]) null);

        //structured data relationships
        edges.add(structuredData, contains, structuredData);

        graph.tx().commit();
    }

    private static Set<Configuration.Property> allProperties() {
        Stream<Configuration.Property> predefined = Stream.of(PropertyKeys.values());

        Stream<Configuration.Property> sysProps = System.getProperties().entrySet().stream()
                .map(e -> new Configuration.Property() {
                    @Override public String getPropertyName() {
                        return (String) e.getKey();
                    }

                    @Override public List<String> getSystemPropertyNames() {
                        return Collections.singletonList((String) e.getKey());
                    }
                });

        return Stream.concat(predefined, sysProps).collect(Collectors.toSet());
    }

    private static Object sampleValue(Class<?> type) {
        if (type == String.class) {
            return "";
        } else if (type == Boolean.class || type == boolean.class) {
            return true;
        } else if (type == Character.class || type == char.class) {
            return ' ';
        } else if (type == Byte.class || type == byte.class) {
            return (byte) 0;
        } else if (type == Short.class || type == short.class) {
            return (short) 0;
        } else if (type == Integer.class || type == int.class) {
            return 0;
        } else if (type == Long.class || type == long.class) {
            return 0L;
        } else if (type == Float.class || type == float.class) {
            return 0F;
        } else if (type == Double.class || type == double.class) {
            return 0D;
        } else {
            throw new IllegalArgumentException("Unhandled type of property: " + type);
        }
    }

    @Override public RuntimeException translateException(RuntimeException inputException, CanonicalPath affectedPath) {
        if (inputException instanceof SqlgExceptions.UniqueConstraintViolationException) {
            return new EntityAlreadyExistsException(inputException, affectedPath);
        }
        return inputException;
    }

    @Override public boolean isTransactionRetryWarranted(Graph graph, Throwable t) {
        //this is always going to be hairy and hacky...
        //Basically for each supported Sqlg dialect we need to identify the exceptions caught by the database that
        //are recoverable by retrying a transaction.

        SqlgGraph sg = (SqlgGraph) graph;

        if (sg.getJdbcUrl().startsWith("jdbc:postgresql")) {
            //this happens when the schema changes in another transaction while a tx is in progress.
            //therefore restarting the tx should clear this out...
            if (t.getMessage().endsWith("ERROR: cached plan must not change result type")) {
                return true;
            }
        }

        return false;
    }

    @FunctionalInterface
    private interface AddEdgeHelper<T, U, V> {
        void add(T t, U u, V v, Object... props);
    }

    private enum PropertyKeys implements Configuration.Property {
        JDBC_URL("sql.jdbc.url", "hawkular.inventory.sql.jdbc.url", "HAWKULAR_INVENTORY_SQL_JDBC_URL"),
        JDBC_USERNAME("sql.jdbc.username", "hawkular.inventory.sql.jdbc.username",
                "HAWKULAR_INVENTORY_SQL_JDBC_USERNAME"),
        JDBC_PASSWORD("sql.jdbc.password", "hawkular.inventory.sql.jdbc.password",
                "HAWKULAR_INVENTORY_SQL_JDBC_PASSWORD");

        private final String propertyName;
        private final List<String> systemPropertyNames;
        private final List<String> envVars;

        PropertyKeys(String propertyName, String sysProp, String envVar) {
            this.propertyName = propertyName;
            systemPropertyNames = sysProp == null
                    ? Collections.emptyList()
                    : Collections.singletonList(sysProp);
            envVars = envVar == null
                    ? Collections.emptyList()
                    : Collections.singletonList(envVar);
        }

        @Override public String getPropertyName() {
            return propertyName;
        }

        @Override public List<String> getSystemPropertyNames() {
            return systemPropertyNames;
        }

        @Override public List<String> getEnvironmentVariableNames() {
            return envVars;
        }
    }
}
