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
import java.sql.Statement;
import java.util.HashMap;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
final class Statements {

    private final SqlGraph graph;

    private final HashMap<String, ThreadLocal<PreparedStatement>> statementCache = new HashMap<>();

    public Statements(SqlGraph graph) {
        this.graph = graph;
    }

    public PreparedStatement getAddVertex() throws SQLException {
        String sql = "INSERT INTO vertices (id) VALUES (DEFAULT)";
        return get(sql, Statement.RETURN_GENERATED_KEYS);
    }

    public PreparedStatement getAddEdge(long inVertexId, long outVertexId, String label) throws SQLException {
        String sql = "INSERT INTO edges (id, vertex_in, vertex_out, label) VALUES (DEFAULT, ?, ?, ?)";
        PreparedStatement stmt = get(sql, Statement.RETURN_GENERATED_KEYS);
        stmt.setLong(1, inVertexId);
        stmt.setLong(2, outVertexId);
        stmt.setString(3, label);
        return stmt;
    }

    public PreparedStatement getGetEdge(long id) throws SQLException {
        String sql = "SELECT id, vertex_in, vertex_out, label FROM edges WHERE id = ?";
        PreparedStatement stmt = get(sql);
        stmt.setLong(1, id);
        return stmt;
    }

    public PreparedStatement getGetVertex(long id) throws SQLException {
        String sql = "SELECT id FROM vertices WHERE id = ?";
        PreparedStatement stmt = get(sql);
        stmt.setLong(1, id);
        return stmt;
    }

    public PreparedStatement getRemoveVertex(long id) throws SQLException {
        String sql = "DELETE FROM vertices WHERE id = ?";
        PreparedStatement stmt = get(sql);
        stmt.setLong(1, id);
        return stmt;
    }

    public PreparedStatement getRemoveEdge(long id) throws SQLException {
        String sql = "DELETE FROM edges WHERE id = ?";
        PreparedStatement stmt = get(sql);
        stmt.setLong(1, id);
        return stmt;
    }

    public PreparedStatement getAllVertices() throws SQLException {
        String sql = "SELECT id FROM vertices";
        return get(sql);
    }

    public PreparedStatement getAllEdges() throws SQLException {
        String sql = "SELECT id, vertex_in, vertex_out, label FROM edges";
        return get(sql);
    }

    public SqlVertex fromVertexResultSet(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }

        return SqlVertex.GENERATOR.generate(graph, rs);
    }

    public PreparedStatement get(String sql) throws SQLException {
        return get(sql, Statement.NO_GENERATED_KEYS);
    }

    public PreparedStatement get(String sql, int autogenerateKeys) throws SQLException {
        PreparedStatement st;

        if (graph.isCacheStatements()) {
            ThreadLocal<PreparedStatement> sts;

            synchronized (statementCache) {
                sts = statementCache.get(sql);
                if (sts == null) {
                    sts = new ThreadLocal<>();
                    statementCache.put(sql, sts);
                }
            }

            st = sts.get();
            if (st == null) {
                st = graph.getConnection().prepareStatement(sql, autogenerateKeys);
                sts.set(st);
            }
        } else {
            st = graph.getConnection().prepareStatement(sql, autogenerateKeys);
        }

        return st;
    }

    public void clearCache() {
        if (graph.isCacheStatements()) {
            synchronized (statementCache) {
                //XXX properly close the statements?
                statementCache.clear();
            }
        }
    }
}
