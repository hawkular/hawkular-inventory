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

/**
 * All the curl commands that are commented out in the code should pass
 */
object FakeDataScenarios extends Helpers {
  val random = new util.Random

  //curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '{"id": "footype", "properties": {"foo-name": "jmeno"}}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/resourceTypes
  def resourceTypeJson(id: String): String = s"""
    {
      "id": "resType-$id",
      "properties": {
        "foo-name": "name-$id"
      }
    }
  """

  //curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '{"id": "aaa", "unit": "BYTES", "type": "GAUGE", "collectionInterval": 30, "properties": {"a": "b"}}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/metricTypes
  def metricTypeJson(id: String): String = s"""
    {
      "id": "metricType-$id",
      "unit": "BYTES",
      "type": "GAUGE",
      "collectionInterval": 30
    }
  """

  //curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '["/aaa"]' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/resourceTypes/footype/metricTypes
  def associateTypesJson(id: String): String = s"""
    ["/metricType-$id"]
  """

  //curl -ivX POST -H "Content-Type: application/json" -H "Accept: application/json" -d '{"id": "foobar", "resourceTypePath": "../URL"}' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/test/resources | less
  def resourceJson(id: String): String = s"""
    {
      "id": "res-$id",
      "resourceTypePath": "../resType-something",
      "properties": {
        "foo": "bar",
        "bar": "foo"
      }
    }
  """

  //curl -ivX POST -H "Accept: application/json" -H "Content-Type: application/json" -d '{"id": "fooMetric", "metricTypePath": "../status.code.type"}' 'http://jdoe:password@127.0.0.1:8080/hawkular/inventory/test/metrics'
  def metricJson(id: String): String = s"""
    {
      "id": "metric-$id",
      "metricTypePath": "../status.code.type",
      "properties": {
        "bla": "blabla",
        "kus": "kuskus"
      }
    }
  """

  //curl -ivX POST -H "Content-Type: application/json;charset=utf-8" -d '["../fooMetric"]' http://jdoe:password@127.0.0.1:8080/hawkular/inventory/test/resources/foobar/metrics
  def associateMetricResourceJson(id: String): String = s"""
    ["../metric-$id"]
  """

  def agentSimulation(types: Boolean = true) = {
    // if types is true it creates `resourceTypesNumber` resource types and for each it creates `metricTypesNumber` metric types and associates the two together
    // if types is false it does the same thing for resources and metrics

    // helpers
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
      .repeat(if (types) resourceTypesNumber else resourcesNumber) {
      exec(session => {
        // storing the generated resource (type) id into session
        session.set("key1", generateRandomString)
      })
        .exec(http(s"Creating resource$typesStr")
        .post(if (types) "resourceTypes" else "test/resources")
        .body(jsonHelper(resourceTypeJson, resourceJson, "key1"))
        //.check(status.in(200 to 304))
        .check(status is 201)
        )
        .pause(interval millis)
        .repeat(if (types) metricTypesNumber else metricsNumber) {

        exec(session =>
          // storing the generated metric (type) id into session
          session.set("key2", generateRandomString)
        )
          .exec(http(s"Creating metric$typesStr")
          .post(if (types) "metricTypes" else "test/metrics")
          .body(jsonHelper(metricTypeJson, metricJson, "key2"))
          .check(status is 201)
          )
          .pause(interval millis)
          .exec(http(s"Assoc. metric$typesStr to resource$typesStr")
          .post(if (types) "resourceTypes/resType-${key1}/metricTypes" else "test/resources/res-${key1}/metrics")
          .body(jsonHelper(associateTypesJson, associateMetricResourceJson, "key2"))
          .check(status is 204)
          )
          .pause(interval millis)
      }
        .exec(session => {
        logd("session after one resource-metric" + typesStr + " creation and association :" + session)
        session
      })
    }
  }

  val readSimulation = {
    // it queries all the resource types, metric types, resources and metrics and for the whole list of the results of given entity type
    // it subsequently perform a singe read operation `readEntityNumber` times
    def readEntityMultipleTimes(entityName: String, pathPrefix: String = "") = {
      val plural = entityName + "s"
      exec(http(s"Geting all $plural")
        .get(pathPrefix + plural)
        .check(status is 200)
        .check(jsonPath("$[*].id").findAll.saveAs("entityIds"))
      ).exec(s => {
        if (logLevel >= 2) println("\nids:\n" + s("entityIds"))
        s
      })
        .pause(interval millis)
        .foreach("${entityIds}", "entityId") {
        repeat(readEntityNumber) {
          exec(session => {
            http(s"Getting one $entityName")
              .get(pathPrefix + plural + session("entityId").as[String].replaceAll(" ", "%20"))//"/${java.net.URLEncoder.encode(entityId).toString}")
              .check(status is 200)
            session
          })
        }
      }
    }

    def getAllResourcesOfGivenTypeMultipleTimes(resourceType: String) = {
      repeat(readEntityNumber) {
        exec(
          http("Get resources of a type (a)")
            .get(s"resourceTypes/$resourceType/resources")
            .check(status is 200)
        )
      }
        .pause(interval millis)
        .repeat(readEntityNumber) {
        exec(
          http("Get resources of a type (b)")
            .get("test/resources")
            .queryParam("typeId", resourceType)
            .check(status is 200)
        )
      }
    }

    readEntityMultipleTimes("resourceType")
      .exec(readEntityMultipleTimes("metricType"))
      .exec(readEntityMultipleTimes("resource", "test/"))
      .exec(readEntityMultipleTimes("metric", "test/"))
      .exec(getAllResourcesOfGivenTypeMultipleTimes("resType-something"))
  }

  val scenario1 = scenario("AgentSimulation (fill the inventory)")
    .pause(4 seconds)
    .exec(agentSimulation(true))
    .exec(agentSimulation(false))

  val scenario2 = scenario("Reads")
    .exec(readSimulation)

  val allScenarios = scenario1.exec(scenario2)

  def getAllScenarios() = allScenarios

}

