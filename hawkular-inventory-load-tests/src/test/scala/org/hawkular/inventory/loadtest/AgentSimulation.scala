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

class AgentSimulation extends Simulation with Helpers {

  // http://git.io/vqEnX
  val httpConf = http
    .baseURL(baseURI + "/hawkular/inventory/")
    //.disableWarmUp
    .basicAuth(username, password)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
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

  def getTenant = http("preparation - get tenant")
      .get("tenant")
      .check(status is 200)

  def createSomeResourceType = http("preparation - create res type")
      // create some resource type (creating resources with type URL leads pinger to glut the server log)
      .post("resourceTypes")
      .body(StringBody(session => FakeDataScenarios.resourceTypeJson("something")))
      .check(status.in(List(201, 409))
    )

  val setupScenario = scenario("Setup")
    .exec(session => {
      logd(s"params:\nresourceTypes  ...  $resourceTypesNumber")
      logd(s"metricTypes    ...  $metricTypesNumber")
      logd(s"resources      ...  $resourcesNumber")
      logd(s"metrics        ...  $metricsNumber")
      logd(s"users            ...  $users")
      logd(s"wildflyNumber    ...  $wildflyNumber\n")
      session
    })
    .exec(getTenant)
    .pause(1 seconds)
    .exec(createSomeResourceType)

  val loadTests = NTimesHawkularInventory.getAllScenarios().exec(FakeDataScenarios.getAllScenarios())

  setUp(
    setupScenario.inject(atOnceUsers(1)),
    loadTests.inject(rampUsers(users) over (ramp seconds))
  ).protocols(httpConf)
}

