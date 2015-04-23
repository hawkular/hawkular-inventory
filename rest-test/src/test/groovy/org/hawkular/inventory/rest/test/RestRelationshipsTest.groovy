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
 * Test some basic inventory functionality regarding the relationships via REST
 * @author jkremser
 * @since 0.0.2
 */
class RestRelationshipsTest extends RestTest {

    @Test
    void testTenantsContainEnvironments() {
        assertRelationshipExists("relationships", "com.acme.tenant", "contains", "production")
        assertRelationshipExists("tenants/com.example.tenant/relationships", "com.example.tenant",
                "contains", "test")
    }

    @Test
    void testTenantsContainResourceTypes() {
        assertRelationshipExists("tenants/com.acme.tenant/relationships", "com.acme.tenant", "contains", "URL")
        assertRelationshipExists("com.example.tenant/resourceTypes/Kachna/relationships", "com.example.tenant",
                "contains", "Kachna")
        assertRelationshipExists("relationships", "com.example.tenant", "contains", "Playroom")
    }

    @Test
    void testTenantsContainMetricTypes() {
        assertRelationshipExists("com.acme.tenant/metricTypes/ResponseTime/relationships", "com.acme.tenant",
                "contains", "ResponseTime")
        assertRelationshipExists("tenants/com.example.tenant/relationships", "com.example.tenant", "contains", "Size")
    }

    @Test
    void testResourceTypesOwnMetricTypes() {
        assertRelationshipExists("com.acme.tenant/resourceTypes/URL/relationships", "URL", "owns", "ResponseTime")
        assertRelationshipExists("com.example.tenant/resourceTypes/Playroom/relationships", "Playroom", "owns","Size")
    }

    @Test
    void testEnvironmentsContainResources() {
        assertRelationshipExists("com.acme.tenant/environments/production/relationships", "production", "contains",
                "host1")
        assertRelationshipExists("com.example.tenant/environments/test/relationships", "test", "contains", "playroom1")
        assertRelationshipExists("com.example.tenant/environments/test/relationships", "test", "contains", "playroom2")
    }

    @Test
    void testEnvironmentsContainMetrics() {
        assertRelationshipExists("com.acme.tenant/environments/production/relationships", "production", "contains",
                "host1_ping_response")
        assertRelationshipExists("com.example.tenant/environments/test/relationships", "test", "contains", "playroom1")
        assertRelationshipExists("com.example.tenant/environments/test/relationships", "test", "contains", "playroom2")
    }

    @Test
    void testResourcesOwnMetrics() {
        assertRelationshipExists("com.acme.tenant/production/resources/host1/relationships", "host1", "owns",
                "host1_ping_response")
        assertRelationshipExists("com.example.tenant/test/resources/playroom1/relationships", "playroom1", "owns",
                "playroom1_size")
        assertRelationshipExists("com.example.tenant/test/resources/playroom2/relationships", "playroom2", "owns",
                "playroom2_size")
    }

    @Test
    void testResourceTypesDefinesResources() {
        assertRelationshipExists("com.example.tenant/resourceTypes/Playroom/relationships", "Playroom", "defines",
                "playroom1")
        assertRelationshipExists("com.example.tenant/resourceTypes/Playroom/relationships", "Playroom", "defines",
                "playroom2")
        assertRelationshipExists("com.acme.tenant/resourceTypes/URL/relationships", "URL", "defines",
                "host1")
    }

    @Test
    void testMetricTypesDefinesMetrics() {
        assertRelationshipExists("com.acme.tenant/metricTypes/ResponseTime/relationships", "ResponseTime", "defines",
                "host1_ping_response")
        assertRelationshipExists("com.example.tenant/metricTypes/Size/relationships", "Size", "defines",
                "playroom1_size")
        assertRelationshipExists("com.example.tenant/metricTypes/Size/relationships", "Size", "defines",
                "playroom2_size")
    }
}
