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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.sql.DataSource;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;

import com.tinkerpop.blueprints.CloseableIterable;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Features;
import com.tinkerpop.blueprints.KeyIndexableGraph;
import com.tinkerpop.blueprints.Parameter;
import com.tinkerpop.blueprints.ThreadedTransactionalGraph;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
public final class SqlGraph implements ThreadedTransactionalGraph, KeyIndexableGraph {
    private static final Features FEATURES = new Features();

    static {
        FEATURES.supportsSerializableObjectProperty = false;
        FEATURES.supportsBooleanProperty = true;
        FEATURES.supportsDoubleProperty = true;
        FEATURES.supportsFloatProperty = true;
        FEATURES.supportsIntegerProperty = true;
        FEATURES.supportsPrimitiveArrayProperty = false;
        FEATURES.supportsUniformListProperty = false;
        FEATURES.supportsMixedListProperty = false;
        FEATURES.supportsLongProperty = true;
        FEATURES.supportsMapProperty = false;
        FEATURES.supportsStringProperty = true;

        FEATURES.supportsDuplicateEdges = true;
        FEATURES.supportsSelfLoops = true;
        FEATURES.isPersistent = true;
        FEATURES.isWrapper = false;
        FEATURES.supportsVertexIteration = true;
        FEATURES.supportsEdgeIteration = true;
        FEATURES.supportsVertexIndex = false;
        FEATURES.supportsEdgeIndex = false;
        FEATURES.ignoresSuppliedIds = true;
        FEATURES.supportsTransactions = true;
        FEATURES.supportsIndices = false;
        FEATURES.supportsKeyIndices = true;
        FEATURES.supportsVertexKeyIndex = true;
        FEATURES.supportsEdgeKeyIndex = true;
        FEATURES.supportsEdgeRetrieval = true;
        FEATURES.supportsVertexProperties = true;
        FEATURES.supportsEdgeProperties = true;
        FEATURES.supportsThreadedTransactions = true;
        FEATURES.supportsThreadIsolatedTransactions = false;
    }

    private final DataSource dataSource;
    private Connection connection;
    private Statements statements;
    private boolean dirty;
    private final String verticesTableName;
    private final String edgesTableName;
    private final String vertexPropertiesTableName;
    private final String edgePropertiesTableName;
    private final String vertexIndicesTableName;
    private final String edgeIndicesTableName;
    private final String uniqueVertexPropertiesTableName;
    private final String uniqueEdgePropertiesTableName;
    private final boolean loadPropertiesEagerly;
    private final boolean closeConnectionOnTransactionEnd;
    private final boolean cacheStatements;
    private long transactionCount;
    private Set<String> vertexPropertyIndices;
    private Set<String> edgePropertyIndices;
    private final IndexUpdater indexUpdater;

    private final WeakHashMap<Long, WeakReference<SqlVertex>> vertexCache = new WeakHashMap<>();

    /**
     * Instantiates a new SQL graph configured from the provided configuration object.
     * The following properties are supported:
     * <ul>
     * <li><code>sql.datasource.class</code> - the name of the datasource class from some JDBC driver on the
     * classpath</li>
     * <li><code>sql.datasource.*</code> - any properties of the datasource can be passed using this prefix.
     * E.g. <code>sql.datasource.portNumber</code>, <code>sql.datasource.serverName</code>, etc. See the documentation
     * of the datasource for the list of available properties.</li>
     * <li><code>sql.verticesTable</code> - the name of the vertices table. Defaults to "vertices".</li>
     * <li><code>sql.edgesTable</code> - the name of the edges table. Defaults to "edges".</li>
     * <li><code>sql.vertexPropertiesTable</code> - the name of the table for vertex properties. Defaults to
     * "vertex_properties".</li>
     * <li><code>sql.edgePropertiesTable</code> - the name of the table for edge properties. Defaults to
     * "edge_properties".</li>
     * </ul>
     *
     * @param configuration the configuration to use
     *
     * @throws Exception
     */
    public SqlGraph(Configuration configuration) throws Exception {
        this((DataSource) Class.forName(configuration.getString("sql.datasource.class")).newInstance(), configuration);
        setupDataSource(dataSource, configuration);
    }

    public SqlGraph(Map<String, Object> configuration) throws Exception {
        this(new MapConfiguration(configuration));
    }

    private void setupDataSource(DataSource dataSource, Configuration configuration) throws Exception {
        BeanInfo beanInfo = Introspector.getBeanInfo(dataSource.getClass());
        PropertyDescriptor[] properties = beanInfo.getPropertyDescriptors();
        Map<String, PropertyDescriptor> propsByName = new HashMap<>();
        for (PropertyDescriptor p : properties) {
            propsByName.put(p.getName().toLowerCase(), p);
        }

        Iterator it = configuration.getKeys("sql.datasource");
        while (it.hasNext()) {
            String key = (String) it.next();
            String property = key.substring("sql.datasource.".length()).toLowerCase();

            PropertyDescriptor d = propsByName.get(property);

            if (d == null) {
                continue;
            }

            Method write = d.getWriteMethod();
            if (write != null) {
                write.invoke(dataSource, configuration.getProperty(key));
            }
        }
    }

    public SqlGraph(DataSource dataSource) {
        this(dataSource, null, null, null, null, null, null, null);
    }

    public SqlGraph(DataSource dataSource, Configuration configuration) throws Exception {
        this(dataSource, configuration.getString("sql.verticesTable"),
                configuration.getString("sql.edgesTable"),
                configuration.getString("sql.vertexPropertiesTable"),
                configuration.getString("sql.edgePropertiesTable"),
                configuration.getBoolean("sql.loadPropertiesEagerly", null),
                configuration.getBoolean("sql.closeConnectionOnTransactionEnd", null),
                configuration.getBoolean("sql.cacheStatements", null));
    }

    private SqlGraph(DataSource dataSource, String vTable, String eTable, String vpTable, String epTable,
                     Boolean loadPropertiesEagerly, Boolean closeConnectionOnTransactionEnd, Boolean cacheStatements) {
        this.dataSource = dataSource;
        verticesTableName = vTable == null ? "vertices" : vTable;
        edgesTableName = eTable == null ? "edges" : eTable;
        vertexPropertiesTableName = vpTable == null ? "vertex_properties" : vpTable;
        edgePropertiesTableName = epTable == null ? "edge_properties" : epTable;
        this.loadPropertiesEagerly = loadPropertiesEagerly == null ? true : loadPropertiesEagerly;
        this.closeConnectionOnTransactionEnd =
                closeConnectionOnTransactionEnd == null ? false : closeConnectionOnTransactionEnd;
        this.cacheStatements = cacheStatements == null ? true : cacheStatements;

        this.vertexIndicesTableName = verticesTableName + "_uidxs";
        this.edgeIndicesTableName = edgesTableName + "_uidxs";
        this.uniqueVertexPropertiesTableName = vertexPropertiesTableName + "_uq";
        this.uniqueEdgePropertiesTableName = edgePropertiesTableName + "_uq";

        this.indexUpdater = new IndexUpdater();
    }

    public synchronized void createSchemaIfNeeded() throws SQLException, IOException {
        ensureConnection();

        try (Statement st = connection.createStatement()) {
            st.execute("SELECT 1 FROM " + getVerticesTableName());
            return;
        } catch (SQLException ignored) {
            //good, the schema doesn't exist. Let's continue
            connection.rollback();
        }

        try {
            String dbName = connection.getMetaData().getDatabaseProductName();
            String script = dbName + "-schema.sql";
            InputStream schemaStream = getClass().getClassLoader().getResourceAsStream(script);
            if (schemaStream == null) {
                schemaStream = getClass().getClassLoader().getResourceAsStream("schema.sql");
            }

            if (schemaStream == null) {
                throw new AssertionError("Could not load the schema creation script.");
            }

            String contents = null;
            try (InputStreamReader rdr = new InputStreamReader(schemaStream)) {
                StringBuilder bld = new StringBuilder();
                char[] buffer = new char[512];

                int cnt;
                while ((cnt = rdr.read(buffer)) != -1) {
                    bld.append(buffer, 0, cnt);
                }

                contents = bld.toString();
            }

            contents = contents.replace("%VERTICES%", verticesTableName);
            contents = contents.replace("%VERTEX_PROPERTIES%", vertexPropertiesTableName);
            contents = contents.replace("%EDGES%", edgesTableName);
            contents = contents.replace("%EDGE_PROPERTIES%", edgePropertiesTableName);

            String[] inst = contents.split(";");
            try (Statement st = connection.createStatement()) {
                for (int i = 0; i < inst.length; i++) {
                    // we ensure that there is no spaces before or after the request string
                    // in order to not execute empty statements
                    if (!inst[i].trim().equals("")) {
                        st.executeUpdate(inst[i]);
                    }
                }
            }

            refreshIndices();

            connection.commit();
        } catch (Throwable t) {
            connection.rollback();
            throw t;
        }
    }

    private synchronized void refreshIndices() {
        if (vertexPropertyIndices == null) {
            vertexPropertyIndices = new HashSet<>();
            edgePropertyIndices = new HashSet<>();
            indexUpdater.on(Vertex.class).refreshIndices();
            indexUpdater.on(Edge.class).refreshIndices();
        }
    }

    @Override
    public synchronized TransactionalGraph newTransaction() {
        if (!dirty && !closeConnectionOnTransactionEnd) {
            return this;
        }

        Log.LOG.debug("Opening new transaction...");

        return new SqlGraph(dataSource, verticesTableName, edgesTableName, vertexPropertiesTableName,
                edgePropertiesTableName, loadPropertiesEagerly, closeConnectionOnTransactionEnd, cacheStatements);
    }

    @Override
    public void stopTransaction(Conclusion conclusion) {
        if (conclusion == Conclusion.SUCCESS) {
            commit();
        } else {
            rollback();
        }
    }

    @Override
    public synchronized void commit() {
        try {
            initConnection();

            Log.LOG.debug("Committing...");

            connection.commit();

            accountForTransactionEnd();
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public synchronized void rollback() {
        try {
            initConnection();
            connection.rollback();
            accountForTransactionEnd();
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public Features getFeatures() {
        return FEATURES;
    }

    @Override
    public synchronized Vertex addVertex(Object id) {
        return withSavePoint(() -> {
            PreparedStatement stmt = statements.getAddVertex();

            if (stmt.executeUpdate() == 0) {
                return null;
            }
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                Vertex ret = cache(statements.fromVertexResultSet(rs));
                dirty = true;
                return ret;
            }
        });
    }

    @Override
    public synchronized SqlVertex getVertex(Object id) {
        Long realId = getId(id);

        if (realId == null) {
            return null;
        }

        WeakReference<SqlVertex> ref = vertexCache.get(realId);

        SqlVertex v = ref == null ? null : ref.get();
        if (v != null) {
            return v;
        }

        initConnection();
        try {
            PreparedStatement stmt = statements.getGetVertex(realId);
            if (!stmt.execute()) {
                return null;
            }

            try (ResultSet rs = stmt.getResultSet()) {
                return cache(statements.fromVertexResultSet(rs));
            }
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public synchronized void removeVertex(Vertex vertex) {
        withSavePoint(() -> {
            PreparedStatement stmt = statements.getRemoveVertex((Long) vertex.getId());
            if (stmt.executeUpdate() == 0) {
                throw new IllegalStateException("Vertex with id " + vertex.getId() + " doesn't exist.");
            }
            vertexCache.remove(vertex.getId());
            dirty = true;
            return null;
        });
    }

    @Override
    public synchronized CloseableIterable<Vertex> getVertices() {
        initConnection();
        try {
            PreparedStatement stmt = statements.getAllVertices();

            if (!stmt.execute()) {
                return StatementIterable.empty();
            }

            return new StatementIterable<Vertex>(SqlVertex.GENERATOR, this, stmt);
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public synchronized CloseableIterable<Vertex> getVertices(String key, Object value) {
        return query().has(key, value).vertices();
    }

    @Override
    public synchronized SqlEdge addEdge(Object id, Vertex outVertex, Vertex inVertex, String label) {
        if (label == null) {
            throw new IllegalArgumentException("null label");
        }

        return withSavePoint(() -> {
            PreparedStatement stmt = statements
                    .getAddEdge((Long) inVertex.getId(), (Long) outVertex.getId(), label);

            if (stmt.executeUpdate() == 0) {
                return null;
            }

            long eid = -1;
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (!rs.next()) {
                    return null;
                }

                eid = rs.getLong(1);
            }

            try (ResultSet rs = statements.getGetEdge(eid).executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                SqlEdge ret = SqlEdge.GENERATOR.generate(this, rs);
                dirty = true;
                return ret;
            }
        });
    }

    @Override
    public synchronized SqlEdge getEdge(Object id) {
        Long eid = getId(id);
        if (eid == null) {
            return null;
        }

        initConnection();

        try {
            PreparedStatement stmt = statements.getGetEdge(eid);
            if (!stmt.execute()) {
                return null;
            }

            try (ResultSet rs = stmt.getResultSet()) {
                if (!rs.next()) {
                    return null;
                }
                return SqlEdge.GENERATOR.generate(this, rs);
            }
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public synchronized void removeEdge(Edge edge) {
        withSavePoint(() -> {
            PreparedStatement stmt = statements.getRemoveEdge((Long) edge.getId());
            if (stmt.executeUpdate() == 0) {
                throw new IllegalStateException("Edge with id " + edge.getId() + " doesn't exist.");
            }
            dirty = true;
            return null;
        });
    }

    @Override
    public synchronized Iterable<Edge> getEdges() {
        initConnection();

        try {
            PreparedStatement stmt = statements.getAllEdges();
            return new StatementIterable<Edge>(SqlEdge.GENERATOR, this, stmt);
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public synchronized CloseableIterable<Edge> getEdges(String key, Object value) {
        return query().has(key, value).edges();
    }

    @Override
    public synchronized SqlGraphQuery query() {
        return new SqlGraphQuery(this);
    }

    @Override
    public synchronized void shutdown() {
        if (connection != null) {
            try {
                connection.commit();
                connection.close();
                statements.clearCache();
                Log.LOG.debugf("Shut down. Closed connection %s", connection);
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }
        }
    }

    @Override
    public synchronized <T extends Element> void createKeyIndex(String key, Class<T> elementClass, Parameter...
            indexParameters) {
        if (key == null || elementClass == null) {
            throw new IllegalArgumentException();
        }
        initConnection();
        indexUpdater.on(elementClass).createIndex(key);
    }

    @Override public <T extends Element> void dropKeyIndex(String key, Class<T> elementClass) {
        if (key == null || elementClass == null) {
            throw new IllegalArgumentException();
        }
        initConnection();
        indexUpdater.on(elementClass).dropIndex(key);
    }

    @Override public <T extends Element> Set<String> getIndexedKeys(Class<T> elementClass) {
        if (elementClass == null) {
            throw new IllegalArgumentException();
        }
        initConnection();
        if (Vertex.class.isAssignableFrom(elementClass)) {
            return vertexPropertyIndices;
        } else if (Edge.class.isAssignableFrom(elementClass)) {
            return edgePropertyIndices;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public String toString() {
        return "sqlgraph(" + dataSource.toString() + ")";
    }

    synchronized Connection getConnection() {
        initConnection();
        return connection;
    }

    synchronized Statements getStatements() {
        initConnection();
        return statements;
    }

    synchronized long getTransactionCount() {
        return transactionCount;
    }

    String getVerticesTableName() {
        return verticesTableName;
    }

    String getEdgesTableName() {
        return edgesTableName;
    }

    String getVertexPropertiesTableName() {
        return vertexPropertiesTableName;
    }

    String getUniqueEdgePropertiesTableName() {
        return uniqueEdgePropertiesTableName;
    }

    String getUniqueVertexPropertiesTableName() {
        return uniqueVertexPropertiesTableName;
    }

    String getEdgePropertiesTableName() {
        return edgePropertiesTableName;
    }

    synchronized void setDirty() {
        dirty = true;
    }

    boolean isLoadPropertiesEagerly() {
        return loadPropertiesEagerly;
    }

    boolean isCacheStatements() {
        return cacheStatements;
    }

    void rollbackToSavePoint(Savepoint sp) {
        if (sp != null) {
            try {
                connection.rollback(sp);
                connection.releaseSavepoint(sp);
            } catch (SQLException e) {
                Log.LOG.wRollbackToSavepointFailed(e);
            }
        }
    }

    <R> R withSavePoint(SQLCallable<R> payload) throws SqlGraphException {
        initConnection();
        //Savepoint sp = null;
        try {
            //sp = connection.setSavepoint();
            return payload.call();
        } catch (SQLException e) {
            //rollbackToSavePoint(sp);
            throw new SqlGraphException(e);
        }
    }

    private void ensureConnection() {
        if (connection == null) {
            try {
                connection = dataSource.getConnection();
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                Log.LOG.debugf("Opened connection %s", connection);
                statements = new Statements(this);
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }
        }
    }

    private void initConnection() {
        ensureConnection();
        refreshIndices();
    }

    private Long getId(Object id) {
        if (id == null) {
            throw new IllegalArgumentException("null id");
        } else if (id instanceof String) {
            try {
                return Long.parseLong((String) id);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (id instanceof Number) {
            return ((Number) id).longValue();
        } else {
            return null;
        }
    }

    private SqlVertex cache(SqlVertex v) {
        if (v != null) {
            vertexCache.put(v.getId(), new WeakReference<>(v));
        }

        return v;
    }

    private void accountForTransactionEnd() throws SQLException {
        if (closeConnectionOnTransactionEnd) {
            connection.close();
            Log.LOG.debugf("Closed connection %s", connection);
            connection = null;
            statements.clearCache();
        }
        dirty = false;
        transactionCount++;
    }

    interface SQLCallable<R> {
        R call() throws SQLException;
    }

    private class IndexUpdater {
        Set<String> indices;
        String indexTable;
        String uPropTable;
        String nuPropTable;
        String propTableFk;

        public IndexUpdater on(Class<?> elementClass) {
            if (Vertex.class.isAssignableFrom(elementClass)) {
                indices = vertexPropertyIndices;
                indexTable = vertexIndicesTableName;
                uPropTable = uniqueVertexPropertiesTableName;
                nuPropTable = vertexPropertiesTableName;
                propTableFk = "vertex_id";
            } else if (Edge.class.isAssignableFrom(elementClass)) {
                indices = edgePropertyIndices;
                indexTable = edgeIndicesTableName;
                uPropTable = uniqueEdgePropertiesTableName;
                nuPropTable = edgePropertiesTableName;
                propTableFk = "edge_id";
            } else {
                throw new IllegalArgumentException("Invalid elementClass: " + elementClass);
            }

            return this;
        }

        public IndexUpdater createIndex(String key) {
            refreshIndices();

            if (indices.contains(key)) {
                throw new IllegalArgumentException("Index '" + key + "' already exists");
            }

            String sql = "INSERT INTO " + indexTable + "(name) VALUES (?)";

            try {
                PreparedStatement st = statements.get(sql);
                st.setString(1, key);
                st.executeUpdate();
                indices.add(key);
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }

            //now copy over the data from the non-unique table, if any

            sql = "INSERT INTO " + uPropTable + "(" + propTableFk + ", name, string_value, numeric_value, value_type)"
                    + " SELECT " + propTableFk + ", name, string_value, numeric_value, value_type FROM " + nuPropTable
                    + " WHERE name = ?";

            boolean moved = isRowsUpdated(sql, key);

            //now delete from the non-unique table
            if (moved) {
                deleteOldKeyValues(nuPropTable, key);
            }

            return this;
        }

        public IndexUpdater dropIndex(String key) {
            refreshIndices();

            if (!indices.contains(key)) {
                return this;
            }

            String sql = "DELETE FROM " + indexTable + " WHERE name = ?";

            try {
                PreparedStatement st = statements.get(sql);
                st.setString(1, key);
                st.executeUpdate();
                indices.remove(key);
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }

            //now copy over the data from the unique table, if any

            sql = "INSERT INTO " + nuPropTable + "(" + propTableFk + ", name, string_value, numeric_value, value_type)"
                    + " SELECT " + propTableFk + ", name, string_value, numeric_value, value_type FROM " + uPropTable
                    + " WHERE name = ?";

            boolean moved = isRowsUpdated(sql, key);


            //now delete from the unique table
            if (moved) {
                deleteOldKeyValues(uPropTable, key);
            }

            return this;
        }

        public void refreshIndices() {
            String sql = "SELECT name FROM " + indexTable;

            try (ResultSet rs = statements.get(sql).executeQuery()) {
                indices.clear();
                while (rs.next()) {
                    indices.add(rs.getString(1));
                }
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }
        }

        private boolean isRowsUpdated(String sql, String key) {
            try {
                PreparedStatement st = statements.get(sql);
                st.setString(1, key);
                return st.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }
        }

        private void deleteOldKeyValues(String oldTableName, String key) {
            String sql = "DELETE FROM " + oldTableName + " WHERE name = ?";
            try {
                PreparedStatement st = statements.get(sql);
                st.setString(1, key);
                st.executeUpdate();
            } catch (SQLException e) {
                throw new SqlGraphException(e);
            }
        }
    }
}
