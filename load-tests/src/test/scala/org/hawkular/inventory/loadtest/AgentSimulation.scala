/**
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
package org.hawkular.inventory.loadtest

import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class AgentSimulation extends Simulation {

  // --------------------------- Options

  val baseURI: String = System.getProperty("baseURI", "http://localhost:8080")
  val username: String = System.getProperty("username", "jdoe")
  val password: String = System.getProperty("password", "password")
  val logLevel: Int = Integer.getInteger("logLevel", 0)

  // Number of concurrent clients (agents)
  val clients: Int = Integer.getInteger("clients", 7)
  // Delay before firing up another client
  val ramp: Long = java.lang.Long.getLong("ramp", 7L)

  // The number of resource types for each client
  val resourceTypesNumber: Int = Integer.getInteger("resourceTypes", 2)

  // The number of metric types for each client
  val metricTypesNumber: Int = Integer.getInteger("metricTypes", resourceTypesNumber * 2)

  // The number of resources for each client
  val resourcesNumber: Int = Integer.getInteger("resources", 2)

  // The number of metrics for each client
  val metricsNumber: Int = Integer.getInteger("metrics", resourcesNumber * 2)

  // Interval between requests that don't have to be synchronized (in millis)
  val interval: Int = Integer.getInteger("interval", 1)

  // ---------------------------

  def log(lvl: Int, str: String): Unit = if (logLevel >= lvl) println(str) else ()
  def log(str: String): Unit = log(1, str)
  def logd(str: String): Unit = log(2, str)

  // http://git.io/vqEnX
  val httpConf = http
    .baseURL(baseURI + "/hawkular/inventory/")
    .disableWarmUp
    .basicAuth(username, password) // perhaps Basic should be there
    .acceptHeader("application/json")
    .contentTypeHeader("application/json;charset=utf-8")
    .acceptEncodingHeader("gzip, deflate")
    .extraInfoExtractor(info => {
      log("\nSending " + info.request.getMethod + " -> URL: " + info.request.getUrl)
      logd("requestHeader: " + info.request.getHeaders)
      logd("requestCookies: " + info.request.getCookies)
      log("requestBody: " + info.request.getStringData)

      log("RESPONSE http " + info.response.statusCode.get)
      logd("responseHeader: " + info.response.headers)
      logd("responseCookies: " + info.response.cookies)
      logd("responseBody: " + info.response.body)
      log("")
      List(info.response.bodyLength)
    })

  val random = new util.Random

// curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '{"id": "footype", "properties": {"name": "jmeno"}}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/resourceType
  def resourceTypeJson(id: String): String = s"""
    {
      "id": "resType-$id",
      "properties": {
        "name": "name-$id"
      }
    }
  """

//curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '{"id": "aaa", "unit": "BYTE", "properties": {"a": "b"}}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/metricTypes
  def metricTypeJson(id: String): String = s"""
    {
      "id": "metricType-$id",
      "unit": "BYTE",
      "properties": {
        "a": "b"
      }
    }
  """

//curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '["aaa"]' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/resourceTypes/footype/metricTypes
  def associateTypesJson(id: String): String = s"""
    ["metricType-$id"]
  """

//curl -ivX POST -H "Content-Type: application/json" -H "Accept: application/json" -d '{"id": "foobar", "resourceTypeId": "URL"}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/test/resources | less
  def resourceJson(id: String): String = s"""
    {
      "id": "res-$id",
      "resourceTypeId": "resType-something",
      "properties": {
        "foo": "bar",
        "bar": "barbar",
        "barbar": "foo"
      }
    }
  """

//curl -ivX POST -H "Accept: application/json" -H "Content-Type: application/json" -d '{"id": "fooMetric", "metricTypeId": "status.code.type"}' 'http://jdoe:password@127.0.0.1:8080/hawkular/inventory/test/metrics'
  def metricJson(id: String): String = s"""
    {
      "id": "metric-$id",
      "metricTypeId": "status.code.type",
      "properties": {
        "bla": "blabla",
        "bar": "barbar",
        "kus": "kuskus"
      }
    }
  """

//curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '["fooMetric"]' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/test/resources/foobar/metrics
  def associateMetricResourceJson(id: String): String = s"""
    ["metric-$id"]
  """

  def simulation(types: Boolean = true) = {
    // hellpers
    def generateRandomString = random.nextLong.toString
    def jsonHelper(getXTypeJson: (String => String), getXJson: (String => String), key: String) =
      StringBody(session => {
        val id = session(key).as[String] 
        if (types) getXTypeJson(id) else getXJson(id)
      })
    val typesStr = if (types) " type" else ""

    exec(session => {
      logd(s"params:\nresourceTypes  ...  $resourceTypesNumber")
      logd(s"metricTypes    ...  $metricTypesNumber")
      logd(s"resources      ...  $resourcesNumber")
      logd(s"metrics        ...  $metricsNumber\n")
      session
    })
    .doIf(session => !types) {
      // create some resource type (creating resources with type URL leads pinger to glut the server log)
      exec(http("preparation - create res type")
        .post("resourceTypes")
        .body(StringBody(session => resourceTypeJson("something")))
        .check(status.in(List(201, 409)))
      )
    }
    .repeat(if (types) resourceTypesNumber else resourcesNumber, "resTypesN") {
      exec(session => {
        // storing the generated resource (type) id into session
        session.set("key1", generateRandomString)
      })
      .exec(http("Creating a resource" + typesStr)
        .post(if (types) "resourceTypes" else "test/resources")
        .body(jsonHelper(resourceTypeJson, resourceJson, "key1"))
        .check(status.in(200 to 304))
      )
      .pause(interval millis)
      .repeat(if (types) metricTypesNumber else metricsNumber, "metricTypesN") {

        exec(session => 
          // storing the generated metric (type) id into session
          session.set("key2", generateRandomString)
        )
        .exec(http("Creating a metric" + typesStr)
          .post(if (types) "metricTypes" else "test/metrics")
          .body(jsonHelper(metricTypeJson, metricJson, "key2"))
          .check(status.in(200 to 304))
        )
        .pause(interval millis)
        .exec(http("Associating metric" + typesStr + " to resource" + typesStr)
          .post(if (types) "resourceTypes/resType-${key1}/metricTypes" else "test/resources/res-${key1}/metrics")
          .body(jsonHelper(associateTypesJson, associateMetricResourceJson, "key2"))
          .check(status.in(200 to 304))
        )
        .pause(interval millis)
      }
      .exec(session => {
        logd("session after one resource-metric" + typesStr + " creation and association :" + session) 
        session
      })
    }
  }

  val scenario1 = scenario("AgentSimulation")
    .exec(simulation(true))
    .exec(simulation(false))

  setUp(scenario1.inject(rampUsers(clients) over (ramp seconds))).protocols(httpConf)
}

