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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;
import org.hawkular.inventory.impl.tinkerpop.sql.impl.InsertException;
import org.hawkular.inventory.impl.tinkerpop.sql.impl.SqlGraph;
import org.hawkular.inventory.paths.CanonicalPath;
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
                    .getImplementationConfiguration(sysPropsAsProperties());

            String jndi = conf.get("sql.datasource.jndi");
            if (jndi == null || jndi.isEmpty()) {
                Log.LOG.iUsingJdbcUrl(conf.get("sql.datasource.url"));
                return SqlgGraph.open(new MapConfiguration(conf));
            } else {
                InitialContext ctx = new InitialContext();
                DataSource ds = (DataSource) ctx.lookup(jndi);
                Log.LOG.iUsingDatasource(jndi);
                return new SqlgGraph(ds, new MapConfiguration(conf));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate the SQL graph.", e);
        }
    }

    @Override public void ensureIndices(Graph graph, IndexSpec... indexSpecs) {
        try {
            SqlGraph sqlg = (SqlGraph) graph;

            sqlg.createSchemaIfNeeded();

            Set<String> vertexIndices = sqlg.getIndexedKeys(Vertex.class);
            Set<String> edgeIndices = sqlg.getIndexedKeys(Edge.class);
            ArrayList<IndexSpec> specs = new ArrayList<>(Arrays.asList(indexSpecs));

            BiConsumer<IndexSpec, Consumer<IndexSpec.Property>> indexChecker = (is, indexMutator) -> {
                IndexSpec.Property prop = is.getProperties().iterator().next();
                if (!prop.isUnique()) {
                    return;
                }

                Set<String> indices = is.getElementType().equals(Edge.class) ? edgeIndices : vertexIndices;

                if (indices.contains(prop.getName())) {
                    return;
                }

                indexMutator.accept(prop);
            };

            Iterator<IndexSpec> it = specs.iterator();
            while (it.hasNext()) {
                IndexSpec is = it.next();

                if (is.getProperties().stream().filter(IndexSpec.Property::isUnique).count() > 1) {
                    throw new IllegalArgumentException("SQL Graph doesn't support unique indices over multiple " +
                            "properties");
                }

                it.remove();

                indexChecker.accept(is,
                        prop -> sqlg.createKeyIndex(prop.getName(), is.getElementType(), (Parameter[]) null));
            }

            //now remove those that are defined but no longer needed
            specs.forEach(is -> indexChecker.accept(is,
                    prop -> sqlg.dropKeyIndex(prop.getName(), is.getElementType())));

            sqlg.commit();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not create the database schema and indices.", e);
        }
    }

    @Override public RuntimeException translateException(RuntimeException inputException, CanonicalPath affectedPath) {
        if (inputException instanceof InsertException) {
            if (Relationship.class.equals(affectedPath.getSegment().getElementType())) {
                throw new RelationAlreadyExistsException(inputException, null, RelationFilter.pathTo(affectedPath));
            } else {
                return new EntityAlreadyExistsException(inputException, affectedPath);
            }
        } else {
            return inputException;
        }
    }

    private static Set<Configuration.Property> sysPropsAsProperties() {
        return System.getProperties().entrySet().stream().map(e -> new Configuration.Property() {
            @Override public String getPropertyName() {
                return (String) e.getKey();
            }

            @Override public List<String> getSystemPropertyNames() {
                return Collections.singletonList((String) e.getKey());
            }
        }).collect(Collectors.toSet());
    }
}
