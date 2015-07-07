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

  // Number of concurrent clients (agents)
  val clients: Int = Integer.getInteger("clients", 1)
  // Delay before firing up another client
  val ramp: Long = java.lang.Long.getLong("ramp", 0L)

  // The number of resource types for each client
  val resourceTypesNumber: Int = Integer.getInteger("resourceTypes", 10)

  // The number of metric types for each client
  val metricTypesNumber: Int = Integer.getInteger("metricTypes", resourceTypesNumber * 2)

  // The number of resources for each client
  val resourcesNumber: Int = Integer.getInteger("resources", 10)

  // The number of metrics for each client
  val metricsNumber: Int = Integer.getInteger("metrics", resourcesNumber * 2)

  // Interval between requests that don't have to be synchronized
  val interval: Int = Integer.getInteger("interval", 1)

  // ---------------------------

  // http://git.io/vqEnX
  val httpConf = http
    .baseURL(baseURI + "/hawkular/inventory/")
    .disableWarmUp
    .basicAuth(username, password) // perhaps Basic should be there
    .acceptHeader("application/json")
    .contentTypeHeader("application/json;charset=utf-8")
    .acceptEncodingHeader("gzip, deflate")
    .extraInfoExtractor(info => {
      println("\nREQUEST:\nrequestUrl: " + info.request.getUrl)
      println("requestHeader: " + info.request.getHeaders)
      println("requestCookies: " + info.request.getCookies)
      println("requestBody: " + info.request.getStringData)

      println("\nRESPONSE:\nresponseHeader: " + info.response.headers)
      println("responseCookies: " + info.response.cookies)
      println("responseBody: " + info.response.body)
      List(info.response.bodyLength)
    })

  val random = new util.Random

  def resourceTypeJson(id: String): String = s"""
    {
      "id": "resType-$id",
      "properties": {
        "name": "name-$id"
      }
    }
  """

  def metricTypeJson(id: String): String = s"""
    {
      "id": "metricType-$id",
      "unit": "BYTE",
      "properties": {
        "a": "b"
      }
    }
  """

  def associateTypesJson(id: String): String = s"""
    ["metricType-$id"]
  """


  val simulation = repeat(resourceTypesNumber, "resTypesN") {
    def resTypeId = random.nextLong.toString

//curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '{"id": "footype", "properties": {"name": "jmeno"}}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/resourceType
    exec(http("Creating ${resTypesN}. resource type")
      .post("resourceTypes")
      .body(StringBody(session => resourceTypeJson(resTypeId)))
      .check(status.in(200 to 304))
    )
    .pause(interval seconds)
    .repeat(metricTypesNumber, "metricTypesN") {
      def metricTypeId = random.nextLong.toString

//curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '{"id": "aaa", "unit": "BYTE", "properties": {"a": "b"}}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/metricTypes
      exec(http("Creating ${metricTypesN}. metric type")
        .post("metricTypes")
        .body(StringBody(session => metricTypeJson(metricTypeId)))
        .check(status.in(200 to 304))
      )
      .pause(interval seconds)

//curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '["aaa"]' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/resourceTypes/footype/metricTypes
      .exec(http("Associating metric type (${metricTypesN}) with resource type (${resTypesN})")
        .post(s"resourceTypes/resType-$resTypeId/metricTypes")
        .body(StringBody(session => associateTypesJson(metricTypeId)))
        .check(status.in(200 to 304))
      )
      .pause(interval seconds)
    }
    .exec(session => {
      println("session after one resource-metric type creation and association :" + session) 
      session
    })
  }

  val scn = scenario("AgentSimulation").exec(simulation)

  setUp(scn.inject(rampUsers(clients) over (ramp seconds))).protocols(httpConf)
}

