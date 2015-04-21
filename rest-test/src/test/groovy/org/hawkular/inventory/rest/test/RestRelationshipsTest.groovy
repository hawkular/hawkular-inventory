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
package org.hawkular.inventory.rest.test
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * Test some basic inventory functionality regarding the relationships via REST
 * @author jkremser
 * @since 0.0.2
 */
class RestRelationshipsTest extends RestTest {


    @Test
    void ping() {
        def response = client.get(path: "")
        assertEquals(200, response.status)
    }

    @Test
    void testTenantsContainEnvironments() {
        assertRelationshipExists("tenants/relationships", "com.acme.tenant", "contains", "production")
        assertRelationshipExists("com.example.tenant/environments/relationships", "com.example.tenant",
                "contains", "test")
    }

    @Test
    void testTenantsContainResourceTypes() {
        assertRelationshipExists("com.acme.tenant/relationships", "com.acme.tenant", "contains", "URL")
        assertRelationshipExists("com.example.tenant/resourceTypes/Kachna/relationships", "com.example.tenant",
                "contains", "Kachna")
        assertRelationshipExists("relationships", "com.example.tenant", "contains", "Playroom")
    }


    @Test
    void testTenantsContainMetricTypes() {
        assertRelationshipExists("com.acme.tenant/metricTypes/ResponseTime/relationships", "com.acme.tenant",
                "contains", "ResponseTime")
        assertRelationshipExists("com.example.tenant/relationships", "contains", "Size")
    }

//    @Test
//    void testMetricTypesLinked() {
//        assertEntitiesExist("com.acme.tenant/resourceTypes/URL/metricTypes", ["ResponseTime"])
//        assertEntitiesExist("com.example.tenant/resourceTypes/Playroom/metricTypes", ["Size"])
//    }
//
//    @Test
//    void testResourcesCreated() {
//        assertEntityExists("com.acme.tenant/production/resources/host1", "host1")
//        assertEntitiesExist("com.acme.tenant/production/resources", ["host1"])
//
//        assertEntityExists("com.example.tenant/test/resources/playroom1", "playroom1")
//        assertEntityExists("com.example.tenant/test/resources/playroom2", "playroom2")
//        assertEntitiesExist("com.example.tenant/test/resources", ["playroom1", "playroom2"])
//    }
//
//    @Test
//    void testMetricsCreated() {
//        assertEntityExists("com.acme.tenant/production/metrics/host1_ping_response", "host1_ping_response")
//        assertEntitiesExist("com.acme.tenant/production/metrics", ["host1_ping_response"])
//
//        assertEntityExists("com.example.tenant/test/metrics/playroom1_size", "playroom1_size")
//        assertEntityExists("com.example.tenant/test/metrics/playroom2_size", "playroom2_size")
//        assertEntitiesExist("com.example.tenant/test/metrics", ["playroom1_size", "playroom2_size"])
//    }
//
//    @Test
//    void testMetricsLinked() {
//        assertEntitiesExist("com.acme.tenant/production/resources/host1/metrics", ["host1_ping_response"])
//        assertEntitiesExist("com.example.tenant/test/resources/playroom1/metrics", ["playroom1_size"])
//        assertEntitiesExist("com.example.tenant/test/resources/playroom2/metrics", ["playroom2_size"])
//    }

    private static void assertRelationshipExists(path, source, label, target) {
        def response = client.get(path: path)
        def needle = new Tuple(source, label, target);
        def haystack = response.data.collect{ new Tuple(it["inv:source"]["inv:shortId"], it["inv:label"],
                it[["inv:target"]["inv:shortId"]])  }
        assert haystack.any{it.equals()} : "Following edge not found: " + needle
    }
}
