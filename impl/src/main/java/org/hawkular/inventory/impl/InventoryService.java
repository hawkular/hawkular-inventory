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
package org.hawkular.inventory.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.MetricDefinition;
import org.hawkular.inventory.api.Resource;
import org.hawkular.inventory.api.ResourceType;
import org.hawkular.inventory.impl.db.DbManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * The inventory backend. Currently using the WildFly embedded H2
 * @author Heiko Rupp
 */
@Stateless
public class InventoryService implements Inventory {

    @javax.annotation.Resource( lookup = "java:/jdbc/HawkularDS")
    private DataSource db;

    Gson gson;
    PreparedStatement insertResourceStatement;
    PreparedStatement findResourceByTypeStatement;
    PreparedStatement findResourceByIdStatement;
    PreparedStatement deleteResourceByIdStatement;
    private PreparedStatement addMetricToResourceStatement;
    private PreparedStatement listMetricsOfResourceStatement;
    Connection connection;
    private PreparedStatement deleteMetricsOfResourceStatement;

    public InventoryService() {

        gson = new GsonBuilder().create();

    }

    public InventoryService(Connection conn) {
        this();
        this.connection = conn;
        try {
            DbManager.setupDB(conn);
            prepareH2Statements(conn);
        } catch (Exception e) {
            e.printStackTrace();  // TODO: Customise this generated block
        }
    }

    @PostConstruct
    public void startup() {

        if (db == null) {
            Log.LOG.warn("Backend database not available");
            throw new RuntimeException("No db, can't continue");
        }

        try {
            connection = db.getConnection("sa","sa");
            DbManager.setupDB(connection);
            prepareH2Statements(connection);
        } catch (Exception e) {
            e.printStackTrace();  // TODO: Customise this generated block
        }

    }

    @PreDestroy
    public void cleanup() {
        if (connection!=null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();  // TODO: Customise this generated block
            }
        }
    }

    @Override
    public String addResource(String tenant, Resource resource) throws Exception {

        String id = resource.getId();
        if (id == null || id.isEmpty()) {
            id = createUUID();
            resource.setId(id);
        }

        insertResourceStatement.setString(1, id);
        insertResourceStatement.setString(2, tenant);
        insertResourceStatement.setString(3, resource.getType().name());
        String payload = toJson(resource);
        insertResourceStatement.setString(4, payload);
        insertResourceStatement.execute();

        return id;
    }

    @Override
    public List<Resource> getResourcesForType(String tenant, ResourceType type) throws Exception {

        List<Resource> result = new ArrayList<>();

        findResourceByTypeStatement.setString(1, type.name());
        findResourceByTypeStatement.setString(2, tenant);
        ResultSet resultSet = findResourceByTypeStatement.executeQuery();
        while (resultSet.next()) {
            String payload = resultSet.getString(1);
            Resource resource = fromJson(payload, Resource.class);
            result.add(resource);
        }
        resultSet.close();

        return result;
    }

    @Override
    public Resource getResource(String tenant, String uid) throws Exception {

        Resource result = null;

        findResourceByIdStatement.setString(1, uid);
        findResourceByIdStatement.setString(2, tenant);

        ResultSet resultSet = findResourceByIdStatement.executeQuery();
        while (resultSet.next()) {
            String payload = resultSet.getString(1);
            result = fromJson(payload, Resource.class);
        }
        resultSet.close();

        return result;
    }

    @Override
    public boolean deleteResource(String tenant, String uid) throws Exception {

        deleteMetricsOfResourceStatement.setString(1, uid);
        deleteMetricsOfResourceStatement.setString(2, tenant);
        deleteMetricsOfResourceStatement.executeUpdate();

        deleteResourceByIdStatement.setString(1, uid);
        deleteResourceByIdStatement.setString(2, tenant);
        int count = deleteResourceByIdStatement.executeUpdate();

        return count ==1;
    }

    @Override
    public boolean addMetricToResource(String tenant, String resourceId, String metric_name) throws Exception {
        List<MetricDefinition> definitions = new ArrayList<>(1);
        definitions.add(new MetricDefinition(metric_name));
        return addMetricsToResource(tenant,resourceId,definitions);
    }

    @Override
    public boolean addMetricsToResource(String tenant, String resourceId, Collection<MetricDefinition> definitions)
            throws Exception {

        try {

            for (MetricDefinition definition : definitions) {
                addMetricToResourceStatement.setString(1, resourceId);
                addMetricToResourceStatement.setString(2, tenant);
                addMetricToResourceStatement.setString(3, definition.getName());
                addMetricToResourceStatement.setString(4, toJson(definition));

                addMetricToResourceStatement.addBatch();

            }
            addMetricToResourceStatement.executeBatch();
        } catch (SQLException e) {
            if (!e.getSQLState().equals("23505")) { // violated PK - we don't care
                Log.LOG.warn(e.getMessage());
            }
            return false;
        }

        return true;
    }


    @Override
    public List<MetricDefinition> listMetricsForResource(String tenant, String resourceId) throws Exception {

        List<MetricDefinition> result = new ArrayList<>();

        listMetricsOfResourceStatement.setString(1,resourceId);
        listMetricsOfResourceStatement.setString(2, tenant);

        ResultSet resultSet = listMetricsOfResourceStatement.executeQuery();
        while (resultSet.next()) {
            String payload = resultSet.getString(1);

            result.add(fromJson(payload,MetricDefinition.class));
        }
        resultSet.close();

        return result;
    }

    @Override
    public boolean updateMetric(String tenant, String resourceId, MetricDefinition metric) throws Exception {
        PreparedStatement s = connection.prepareStatement("MERGE INTO HWK_METRICS VALUES (?,?,?,?)");

        s.setString(1, resourceId);
        s.setString(2, tenant);
        s.setString(3, metric.getName());
        s.setString(4, toJson(metric));

        int count = s.executeUpdate();

        return count == 1;

    }

    @Override
    public MetricDefinition getMetric(String tenant, String resourceId, String metricId) throws Exception {
        PreparedStatement s = connection.prepareStatement("SELECT m.payload FROM HWK_METRICS m " +
                "WHERE m.TENANT = ? AND m.RESOURCE_ID = ? and m.METRIC_NAME = ?");

        s.setString(1, tenant);
        s.setString(2, resourceId);
        s.setString(3, metricId);

        MetricDefinition result;

        try (ResultSet resultSet = s.executeQuery()) {
            result = null;
            while (resultSet.next()) {
                String payload = resultSet.getString(1);
                result = fromJson(payload,MetricDefinition.class);
            }

        }
        return result;



    }

    private String createUUID() {
        return "x" + String.valueOf(System.currentTimeMillis());
    }



    void prepareH2Statements(Connection c ) throws Exception {

        // deal with resources
        insertResourceStatement = c.prepareStatement("INSERT INTO HWK_RESOURCES VALUES ( ?, ?, ?, ? ) ");
        findResourceByIdStatement =
                c.prepareStatement("SELECT r.payload FROM HWK_RESOURCES r  WHERE ID = ? AND TENANT = ?");
        findResourceByTypeStatement =
                c.prepareStatement("SELECT r.payload FROM HWK_RESOURCES r WHERE type = ? AND tenant = ?");
        deleteResourceByIdStatement = c.prepareStatement("DELETE FROM HWK_RESOURCES WHERE ID = ? AND TENANT = ?");

        // deal with metrics
        addMetricToResourceStatement = c.prepareStatement("INSERT INTO HWK_METRICS VALUES ( ?,?,?, ?)");
        listMetricsOfResourceStatement = c.prepareStatement("SELECT m.payload FROM HWK_METRICS m WHERE m" +
                ".resource_id = ? AND TENANT = ?");

        deleteMetricsOfResourceStatement =
                c.prepareStatement("DELETE FROM HWK_METRICS where resource_id = ? AND TENANT = ?");

    }

    private String toJson(Object resource) {

        return gson.toJson(resource);

    }

    private <T> T fromJson(String json, Class<T> clazz) {

        return gson.fromJson(json,clazz);
    }

    @SuppressWarnings("unused")
    public void setConnection(Connection conn) {
        // used for mocking
    }
}
