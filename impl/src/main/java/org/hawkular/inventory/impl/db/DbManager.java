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
package org.hawkular.inventory.impl.db;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Small helper class that deals with DB setup
 *
 * @author Heiko W. Rupp
 */
public class DbManager {

    public static void setupDB(Connection connection) {
        try {
            createH2DDL(connection);
        } catch (Exception e) {
                  e.printStackTrace();  // TODO: Customise this generated block
              }
    }

    private static void createH2DDL(Connection c) throws Exception {

        Statement s = c.createStatement();
        s.execute("CREATE TABLE IF NOT EXISTS HWK_RESOURCES " +
              " (  id VARCHAR(250) PRIMARY KEY ,"+
              "  tenant VARCHAR(250) ,  \n" +
              "  type VARCHAR(12) ,   \n" +
              "  payload VARCHAR(1024) )");

        s.execute("CREATE TABLE IF NOT EXISTS HWK_METRICS " +
              " ( resource_id VARCHAR(250) NOT NULL, " +
              "  tenant VARCHAR(250) , " +
              "   metric_name VARCHAR(250) NOT NULL," +
                " payload VARCHAR(2048) )  ");
        s.execute("CREATE UNIQUE INDEX ON HWK_METRICS ( resource_id, metric_name) ");

        s.close();
      }

}
