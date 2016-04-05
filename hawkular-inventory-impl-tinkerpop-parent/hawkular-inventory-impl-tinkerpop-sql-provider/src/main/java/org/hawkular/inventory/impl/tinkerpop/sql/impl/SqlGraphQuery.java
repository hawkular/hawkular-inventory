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
package org.hawkular.inventory.impl.tinkerpop.sql.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.tinkerpop.blueprints.CloseableIterable;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.GraphQuery;
import com.tinkerpop.blueprints.Predicate;
import com.tinkerpop.blueprints.Vertex;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
public final class SqlGraphQuery implements GraphQuery {

    private final SqlGraph graph;

    private QueryFilters
            filters = new QueryFilters();

    private int limit = -1;

    SqlGraphQuery(SqlGraph graph) {
        this.graph = graph;
    }

    @Override
    public SqlGraphQuery has(String key) {
        filters.has(key);
        return this;
    }

    @Override
    public SqlGraphQuery hasNot(String key) {
        filters.hasNot(key);
        return this;
    }

    @Override
    public SqlGraphQuery has(String key, Object value) {
        filters.has(key, value);
        return this;
    }

    @Override
    public SqlGraphQuery hasNot(String key, Object value) {
        filters.hasNot(key, value);
        return this;
    }

    @Override
    public SqlGraphQuery has(String key, Predicate predicate, Object value) {
        filters.has(key, predicate, value);
        return this;
    }

    @Override
    public <T extends Comparable<T>> SqlGraphQuery has(String key, T value, Compare compare) {
        filters.has(key, value, compare);
        return this;
    }

    @Override
    public <T extends Comparable<?>> SqlGraphQuery interval(String key, T startValue, T endValue) {
        filters.interval(key, startValue, endValue);
        return this;
    }

    @Override
    public SqlGraphQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public CloseableIterable<Edge> edges() {
        try {
            PreparedStatement stmt = generateStatement("SELECT id, vertex_in, vertex_out, label",
                graph.getEdgesTableName(), graph.getUniqueEdgePropertiesTableName(),
                    graph.getEdgePropertiesTableName(), SqlEdge.getPropertyTableForeignKey(),
                    SqlEdge.DISALLOWED_PROPERTY_NAMES);

            return new StatementIterable<Edge>(SqlEdge.GENERATOR, graph, stmt);
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public CloseableIterable<Vertex> vertices() {
        try {
            PreparedStatement stmt = generateStatement("SELECT id", graph.getVerticesTableName(),
                graph.getVertexPropertiesTableName(), graph.getUniqueVertexPropertiesTableName(),
                    SqlVertex.getPropertyTableForeignKey(),
                SqlVertex.DISALLOWED_PROPERTY_NAMES);

            return new StatementIterable<Vertex>(SqlVertex.GENERATOR, graph, stmt);
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    private PreparedStatement generateStatement(String select, String mainTable, String propsTable,
                                                String uniquePropsTable, String propsTableFK,
                                                List<String> specialProps) throws SQLException {

        QueryFilters.SqlAndParams sql = filters.generateStatement(select, mainTable, propsTable, uniquePropsTable,
                propsTableFK, specialProps, null);

        if (limit >= 0) {
            sql.sql.append(" LIMIT ").append(limit);
        }

        PreparedStatement stmt = graph.getConnection()
            .prepareStatement(sql.sql.toString(), ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

        int i = 1;
        for (Object p : sql.params) {
            stmt.setObject(i++, p);
        }

        return stmt;
    }

}
