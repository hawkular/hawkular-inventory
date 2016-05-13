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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" version="2.0" exclude-result-prefixes="xalan">

  <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" xalan:indent-amount="4" standalone="no" />

  <!-- //*[local-name()='config']/*[local-name()='supplement' and @name='default'] is an xPath's 1.0
       way of saying of xPath's 2.0 prefix-less selector //*:config/*:supplement[@name='default']  -->
  <xsl:template match="//*[local-name()='config']/*[local-name()='subsystem']/*[local-name()='datasources']">
    <xsl:copy>
      <!--
          If you wish to use Hawkular Inventory with an SQL backend (which is NOT recommended for anything but toy
          deployments) you can build and deploy the
          hawkular-inventory/hawkular-inventory-impl-tinkerpop-parent/hawkular-inventory-impl-tinkerpop-sql
          -provider, put it in the Hawkular inventory dist's WEB-INF/lib, uncomment and configure
          the below datasource and start hawkular with the following system properties:
          -Dsql.datasource.jndi='java:jboss/datasources/HawkularInventoryDS'
          -Dhawkular.inventory.tinkerpop.graph-provider-impl=org.hawkular.inventory.impl.tinkerpop.sql.SqlGraphProvider

          Note that inventory only supports H2 or Postgresql as its SQL backends (and the postgresql jdbc driver is not
          deployed in the Hawkular server).

        <datasource jndi-name="java:jboss/datasources/HawkularInventoryDS" pool-name="HawkularInventoryDS" enabled="true"
                    use-java-context="true">
          <connection-url>jdbc:h2:${jboss.server.data.dir}/hawkular-inventory/db;MVCC=true;CACHE_SIZE=131072</connection-url>
          <driver>h2</driver>
          <security>
            <user-name>sa</user-name>
            <password>sa</password>
          </security>
        </datasource>
      -->
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
