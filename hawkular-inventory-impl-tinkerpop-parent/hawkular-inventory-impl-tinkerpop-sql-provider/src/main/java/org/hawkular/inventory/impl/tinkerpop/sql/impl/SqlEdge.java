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
import java.util.Arrays;
import java.util.List;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
public final class SqlEdge extends SqlElement implements Edge {

    public static final ElementGenerator<SqlEdge> GENERATOR = new ElementGenerator<SqlEdge>() {
        @Override
        public SqlEdge generate(SqlGraph graph, ResultSet rs) {
            try {
                long id = rs.getLong(1);
                long vin = rs.getLong(2);
                long vout = rs.getLong(3);
                String lbl = rs.getString(4);

                return new SqlEdge(graph, id, vin, vout, lbl);
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }
        }
    };

    public static final List<String> DISALLOWED_PROPERTY_NAMES = Arrays.asList("id", "label");

    private final long inVertexId;
    private final long outVertexId;
    private final String label;
    private SqlVertex inVertex;
    private SqlVertex outVertex;

    SqlEdge(SqlGraph graph, long id, long inVertexId, long outVertexId, String label) {
        super(graph, id);
        this.inVertexId = inVertexId;
        this.outVertexId = outVertexId;
        this.label = label;
    }

    public static String getPropertyTableForeignKey() {
        return "edge_id";
    }

    @Override
    public void remove() {
        synchronized (graph) {
            graph.withSavePoint(() -> {
                PreparedStatement stmt = graph.getStatements().getRemoveEdge(getId());
                stmt.executeUpdate();
                graph.setDirty();
                return null;
            });
        }
    }

    @Override
    protected String getPropertiesTableName() {
        return graph.getEdgePropertiesTableName();
    }

    @Override protected String getUniquePropertiesTableName() {
        return graph.getEdgePropertiesTableName();
    }

    @Override
    protected String getPropertyTableElementIdName() {
        return getPropertyTableForeignKey();
    }

    @Override
    protected List<String> getDisallowedPropertyNames() {
        return DISALLOWED_PROPERTY_NAMES;
    }

    @Override
    public Vertex getVertex(Direction direction) throws IllegalArgumentException {
        SqlVertex v = null;
        switch (direction) {
        case BOTH:
            throw new IllegalArgumentException();
        case IN:
            if (inVertex == null) {
                inVertex = graph.getVertex(inVertexId);
            }
            v = inVertex;
            break;
        case OUT:
            if (outVertex == null) {
                outVertex = graph.getVertex(outVertexId);
            }
            v = outVertex;
            break;
        }

        return v;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        SqlEdge sqlEdge = (SqlEdge) o;

        if (inVertexId != sqlEdge.inVertexId) {
            return false;
        }
        if (outVertexId != sqlEdge.outVertexId) {
            return false;
        }
        if (!label.equals(sqlEdge.label)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (inVertexId ^ (inVertexId >>> 32));
        result = 31 * result + (int) (outVertexId ^ (outVertexId >>> 32));
        result = 31 * result + label.hashCode();
        return result;
    }
}
