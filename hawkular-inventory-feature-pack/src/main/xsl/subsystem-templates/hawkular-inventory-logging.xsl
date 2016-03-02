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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" version="2.0">

  <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" xalan:indent-amount="4" standalone="no" />
  <xsl:strip-space elements="*" />

  <!-- //*[local-name()='config']/*[local-name()='supplement' and @name='default'] is an xPath's 1.0
       way of saying of xPath's 2.0 prefix-less selector //*:config/*:supplement[@name='default']  -->
  <xsl:template match="//*[local-name()='config']/*[local-name()='supplement' and @name='default']/*[local-name()='replacement' and @placeholder='LOGGERS']">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
      <xsl:element name="logger" namespace="{namespace-uri()}">
        <xsl:attribute name="category">org.hawkular.inventory</xsl:attribute>
        <xsl:element name="level">
          <xsl:attribute name="name">
            <xsl:text disable-output-escaping="yes">${hawkular.log.inventory:INFO}</xsl:text>
          </xsl:attribute>
        </xsl:element>
      </xsl:element>
      <xsl:element name="logger" namespace="{namespace-uri()}">
        <xsl:attribute name="category">org.hawkular.inventory.rest.requests</xsl:attribute>
        <xsl:element name="level">
          <xsl:attribute name="name">
            <xsl:text disable-output-escaping="yes">${hawkular.log.inventory.rest.requests:INFO}</xsl:text>
          </xsl:attribute>
        </xsl:element>
      </xsl:element>
    </xsl:copy>
  </xsl:template>

  <xsl:template
      match="//*[local-name()='config']/*[local-name()='subsystem']/*[local-name()='console-handler']/*[local-name()='level']">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
      <xsl:attribute name="name">DEBUG</xsl:attribute>
    </xsl:copy>
  </xsl:template>

  <!-- copy everything else as-is -->
  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*" />
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
