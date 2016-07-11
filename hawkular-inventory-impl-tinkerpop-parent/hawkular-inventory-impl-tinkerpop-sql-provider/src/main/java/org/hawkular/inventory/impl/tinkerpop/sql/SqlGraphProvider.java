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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.impl.tinkerpop.spi.GraphProvider;
import org.hawkular.inventory.impl.tinkerpop.spi.IndexSpec;
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

        ArrayList<IndexSpec> specs = new ArrayList<>(Arrays.asList(indexSpecs));

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
                keyValues.add(" ");
                keyValues.add(p.getName());
            }

            if (Vertex.class.equals(is.getElementType())) {
                sqlg.createVertexLabeledIndex(Vertex.DEFAULT_LABEL, keyValues.toArray());
            } else {
                sqlg.createEdgeLabeledIndex(Edge.DEFAULT_LABEL, keyValues.toArray());
            }
        }

        sqlg.tx().commit();
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
