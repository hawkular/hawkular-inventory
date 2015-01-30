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
import org.hawkular.inventory.api.Resource;
import org.hawkular.inventory.api.ResourceType;
import org.hawkular.inventory.impl.db.DbManager;

import javax.ejb.Stateless;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;


/**
 * The inventory backend. Currently using the WildFly embedded H2
 * @author Heiko Rupp
 */
@Stateless
//@javax.annotation.Resource(mappedName = "HAWKULAR_DS", name = "java:/jdbc/HawkularDS")
public class InventoryService implements Inventory {

//    @javax.annotation.Resource(mappedName = "HAWKULAR_DS")
    private DataSource db;

    Gson gson;
    PreparedStatement insertStatement;
    PreparedStatement findByTypeStatement;
    PreparedStatement findByIdStatement;
    PreparedStatement deleteByIdStatement;

    public InventoryService() {

        // Workaround until I get the above injection to work
        try {
            InitialContext ic = new InitialContext();
            db = (DataSource) ic.lookup("java:/jdbc/HawkularDS");
            ic.close();
        } catch (NamingException e) {
            e.printStackTrace();  // TODO: Customise this generated block
        }


        if (db == null) {
            Log.LOG.warn("Backend database not available");
            throw new RuntimeException("No db, can't continue");
        }

        Connection connection;
        try {
            connection = db.getConnection("sa","sa");
            DbManager.setupDB(connection);
            prepareH2Statements(connection);
        } catch (Exception e) {
            e.printStackTrace();  // TODO: Customise this generated block
        }

        gson = new GsonBuilder().create();

    }

    public InventoryService(Connection conn) {
        try {
            DbManager.setupDB(conn);
            prepareH2Statements(conn);
        } catch (Exception e) {
            e.printStackTrace();  // TODO: Customise this generated block
        }
        gson = new GsonBuilder().create();
    }

    @Override
    public String addResource(String tenant, Resource resource) throws Exception {

        String id = resource.getId();
        if (id == null || id.isEmpty()) {
            id = createUUID();
            resource.setId(id);
        }

        insertStatement.setString(1,id);
        insertStatement.setString(2,tenant);
        insertStatement.setString(3,resource.getType().name());
        String payload = toJson(resource);
        insertStatement.setString(4,payload);
        insertStatement.execute();

        return id;
    }

    @Override
    public List<Resource> getResourcesForType(String tenant, ResourceType type) throws Exception {

        List<Resource> result = new ArrayList<>();

        findByTypeStatement.setString(1,type.name());
        findByTypeStatement.setString(2, tenant);
        ResultSet resultSet = findByTypeStatement.executeQuery();
        while (resultSet.next()) {
            String payload = resultSet.getString(1);
            Resource resource = fromJson(payload);
            result.add(resource);
        }
        resultSet.close();

        return result;
    }

    @Override
    public Resource getResource(String tenant, String uid) throws Exception {

        Resource result = null;

        findByIdStatement.setString(1,uid);
        findByIdStatement.setString(2, tenant);
        ResultSet resultSet = findByIdStatement.executeQuery();
        while (resultSet.next()) {
            String payload = resultSet.getString(1);
            result = fromJson(payload);
        }
        resultSet.close();

        return result;
    }

    @Override
    public boolean deleteResource(String tenant, String uid) throws Exception {

        deleteByIdStatement.setString(1,uid);
        deleteByIdStatement.setString(2,tenant);
        int count = deleteByIdStatement.executeUpdate();

        return count ==1;
    }

    private String createUUID() {
        return "x" + String.valueOf(System.currentTimeMillis());
    }



    void prepareH2Statements(Connection c ) throws Exception {

        insertStatement = c.prepareStatement("INSERT INTO HWK_RESOURCES VALUES ( ?, ?, ?, ? ) ");
        insertStatement = c.prepareStatement("INSERT INTO HWK_RESOURCES VALUES ( ?, ?, ?, ? ) ");
        findByIdStatement = c.prepareStatement("SELECT r.payload FROM HWK_RESOURCES r  WHERE ID = ? AND TENANT = ?");
        findByTypeStatement = c.prepareStatement("SELECT r.payload FROM HWK_RESOURCES r WHERE type = ? AND tenant = ?");
        deleteByIdStatement = c.prepareStatement("DELETE FROM HWK_RESOURCES WHERE ID = ? AND TENANT = ?");

    }

    private String toJson(Resource resource) {

        return gson.toJson(resource);

    }

    private Resource fromJson(String json) {

        return gson.fromJson(json,Resource.class);
    }

    @SuppressWarnings("unused")
    public void setConnection(Connection conn) {
        // used for mocking
    }
}
