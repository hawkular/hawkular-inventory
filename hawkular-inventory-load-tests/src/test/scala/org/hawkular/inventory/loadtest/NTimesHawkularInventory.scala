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

object NTimesHawkularInventory extends Helpers {

  def uuid = java.util.UUID.randomUUID.toString

  def insertFeed() = {
    http("insert feed")
      .post("feeds")
      .body(StringBody(s => {
        val feedId = s("feedId").as[String]
        logd("inserting feed:" + feedId)
        s"""{
          "id": "${feedId}"
        }"""
      }))
      .check(status in 201)
  }

  def bulkInsertSimulationForOneFeed() = {
      exec(http("bulk insert")
      .post(baseURI + bulkInsertPath)
      .body(ELFileBody("data.json")).asJSON
      .check(status is 201)
      )
  }

  def bulkInsertSimulationForMultipleFeeds(wildflyNumber: Int) = {
//    val uuids = List.tabulate(wildflyNumber)(_ => uuid)
    repeat(wildflyNumber) {
      exec(_.set("feedId", uuid))
      .exec(insertFeed())
      .pause(1 seconds)
      .exec(bulkInsertSimulationForOneFeed())
    }
  }

  val scenario1 = scenario("Bulk insert (fill the inventory)")
    .pause(4 seconds)
    .exec(bulkInsertSimulationForMultipleFeeds(wildflyNumber))

  val allScenarios = scenario1

  def getAllScenarios() = allScenarios
}

