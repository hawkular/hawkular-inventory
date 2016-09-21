<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
    and other contributors as indicated by the @author tags.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<!-- NOTE: THIS FILE IS ONLY TAKEN INTO ACCOUNT WHEN MAVEN BUILD IS RUN WITH THE "sql" PROFILE -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" version="2.0" exclude-result-prefixes="xalan">

  <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" xalan:indent-amount="4" standalone="no" />

  <!-- //*[local-name()='config']/*[local-name()='supplement' and @name='default'] is an xPath's 1.0
       way of saying of xPath's 2.0 prefix-less selector //*:config/*:supplement[@name='default']  -->
  <xsl:template match="//*[local-name()='config']/*[local-name()='subsystem']/*[local-name()='datasources']">
    <xsl:copy>
      <!--
          If you wish to use Hawkular Inventory with an SQL backend (which is NOT recommended for anything but toy
          deployments) you can start hawkular with the following system properties:
          -Dsql.jdbc.url='jndi:java:jboss/datasources/HawkularInventoryDS_hsqldb'
          -Dhawkular.inventory.tinkerpop.graph-provider-impl=org.hawkular.inventory.impl.tinkerpop.sql.SqlGraphProvider

          Note that inventory only supports HSQLDB or Postgresql as its SQL backends (and the jdbc drivers are not
          deployed in the Hawkular server by default).
      -->
      <datasource jndi-name="java:/jboss/datasources/HawkularInventoryDS_hsqldb" pool-name="HawkularInventoryDS_hsqldb"
                  enabled="true" use-java-context="true">
        <connection-url>
          jdbc:hsqldb:${jboss.server.data.dir}/hawkular-inventory/db;hsqldb.tx=mvcc;hsqldb.cache_size=131072;shutdown=true
        </connection-url>
        <driver>hsqldb</driver>
        <security>
          <user-name>sa</user-name>
          <password>sa</password>
        </security>
      </datasource>

      <datasource jndi-name="java:/jboss/datasources/HawkularInventoryDS_h2" pool-name="HawkularInventoryDS_h2"
                  enabled="true" use-java-context="true">
        <connection-url>
          jdbc:h2:${jboss.server.data.dir}/hawkular-inventory/db;MVCC=true;CACHE_SIZE=131072
        </connection-url>
        <driver>h2</driver>
        <security>
          <user-name>sa</user-name>
          <password>sa</password>
        </security>
      </datasource>

      <datasource jndi-name="java:/jboss/datasources/HawkularInventoryDS_postgres"
                  pool-name="HawkularInventoryDS_postgres" enabled="true" use-java-context="true">
        <connection-url>
          jdbc:postgresql://localhost:5432/hawkular
        </connection-url>
        <driver>postgresql</driver>
        <security>
          <user-name>hawkular</user-name>
          <password>hawkular</password>
        </security>
        <connection-property name="prepareThreshold">0</connection-property>
      </datasource>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="//*[local-name()='config']/*[local-name()='subsystem']/*[local-name()='datasources']/*[local-name()='drivers']">
    <xsl:copy>
      <driver name="postgresql" module="org.postgresql.postgresql">
        <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
      </driver>
      <driver name="hsqldb" module="org.hsqldb.hsqldb"/>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <!-- copy everything else as-is -->
  <xsl:template match="node()|comment()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|comment()|@*" />
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
