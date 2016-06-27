/**
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
package org.hawkular.inventory.loadtest

import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

trait Helpers {

  // --------------------------- Options

  val inventoryPath = "/hawkular/inventory/deprecated/"
  val bulkInsertPath = "/hawkular/inventory/bulk"
  val baseURI: String = System.getProperty("ltest.baseURI", "http://localhost:8080")
  val username: String = System.getProperty("ltest.username", "jdoe")
  val password: String = System.getProperty("ltest.password", "password")
  val logLevel: Int = Integer.getInteger("ltest.logLevel", 1)

  // Number of concurrent clients
  val users: Int = Integer.getInteger("ltest.users", 7)

  // Delay before firing up another client
  val ramp: Long = java.lang.Long.getLong("ltest.ramp", 5L)

  // The number of resource types for each client
  val resourceTypesNumber: Int = Integer.getInteger("ltest.resourceTypes", 2)

  // The number of metric types for each client
  val metricTypesNumber: Int = Integer.getInteger("ltest.metricTypes", resourceTypesNumber * 2)

  // The number of resources for each client
  val resourcesNumber: Int = Integer.getInteger("ltest.resources", 2)

  // The number of metrics for each client
  val metricsNumber: Int = Integer.getInteger("ltest.metrics", resourcesNumber * 2)

  // The number reads fo perform for each entity for each client
  val readEntityNumber: Int = Integer.getInteger("ltest.readEntity", 2)

  // Interval between requests that don't have to be synchronized (in millis)
  val interval: Int = Integer.getInteger("ltest.interval", 1)

  // The number of resource types for each client
  val wildflyNumber: Int = Integer.getInteger("ltest.hawkularInventoryMultiple", 1)

  // ---------------------------

  def log(lvl: Int, str: String): Unit = if (logLevel >= lvl) println(str) else ()
  def log(str: String): Unit = log(1, str)
  def logd(str: String): Unit = log(2, str)

}

