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

/**
 * Test some basic inventory functionality via REST
 * @author Heiko W. Rupp
 */
class RestEntitiesTest extends RestTest {

    @Test
    void testTenantsCreated() {
        assertEntitiesExist("tenants", ["com.acme.tenant", "com.example.tenant"])
    }

    @Test
    void testEnvironmentsCreated() {
        assertEntitiesExist("com.acme.tenant/environments", ["production"])
        assertEntitiesExist("com.example.tenant/environments", ["test"])
    }

    @Test
    void testResourceTypesCreated() {
        assertEntityExists("com.acme.tenant/resourceTypes/URL", "URL")
        assertEntitiesExist("com.acme.tenant/resourceTypes", ["URL"])

        assertEntityExists("com.example.tenant/resourceTypes/Kachna", "Kachna")
        assertEntityExists("com.example.tenant/resourceTypes/Playroom", "Playroom")
        assertEntitiesExist("com.example.tenant/resourceTypes", ["Playroom", "Kachna"])
    }

    @Test
    void testMetricTypesCreated() {
        assertEntityExists("com.acme.tenant/metricTypes/ResponseTime", "ResponseTime")
        assertEntitiesExist("com.acme.tenant/metricTypes", ["ResponseTime"])

        assertEntityExists("com.example.tenant/metricTypes/Size", "Size")
        assertEntitiesExist("com.example.tenant/metricTypes", ["Size"])
    }

    @Test
    void testMetricTypesLinked() {
        assertEntitiesExist("com.acme.tenant/resourceTypes/URL/metricTypes", ["ResponseTime"])
        assertEntitiesExist("com.example.tenant/resourceTypes/Playroom/metricTypes", ["Size"])
    }

    @Test
    void testResourcesCreated() {
        assertEntityExists("com.acme.tenant/production/resources/host1", "host1")
        assertEntitiesExist("com.acme.tenant/production/resources", ["host1"])

        assertEntityExists("com.example.tenant/test/resources/playroom1", "playroom1")
        assertEntityExists("com.example.tenant/test/resources/playroom2", "playroom2")
        assertEntitiesExist("com.example.tenant/test/resources", ["playroom1", "playroom2"])
    }

    @Test
    void testMetricsCreated() {
        assertEntityExists("com.acme.tenant/production/metrics/host1_ping_response", "host1_ping_response")
        assertEntitiesExist("com.acme.tenant/production/metrics", ["host1_ping_response"])

        assertEntityExists("com.example.tenant/test/metrics/playroom1_size", "playroom1_size")
        assertEntityExists("com.example.tenant/test/metrics/playroom2_size", "playroom2_size")
        assertEntitiesExist("com.example.tenant/test/metrics", ["playroom1_size", "playroom2_size"])
    }

    @Test
    void testMetricsLinked() {
        assertEntitiesExist("com.acme.tenant/production/resources/host1/metrics", ["host1_ping_response"])
        assertEntitiesExist("com.example.tenant/test/resources/playroom1/metrics", ["playroom1_size"])
        assertEntitiesExist("com.example.tenant/test/resources/playroom2/metrics", ["playroom2_size"])
    }
}
