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

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * Test some basic inventory functionality via REST
 * @author Heiko W. Rupp
 */
class RestTest extends AbstractTestBase {

    @BeforeClass
    static void setupData() {
        def tenantId = "com.acme.tenant"
        def environmentId = "production"

        def response = client.post(path: "tenants", body: "{\"id\":\"$tenantId\"}")
        assertEquals(201, response.status)
        assertEquals(baseURI + "tenants/$tenantId", response.headers.Location)

        response = client.post(path: "$tenantId/environments", body: "{\"id\":\"$environmentId\"}")
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/environments/$environmentId", response.headers.Location)

        response = client.post(path: "$tenantId/resourceTypes", body: '{"id":"URL", "version":"1.0"}')
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/resourceTypes/URL", response.headers.Location)

        response = client.post(path: "$tenantId/metricTypes", body: '{"id":"ResponseTime", "unit" : "MILLI_SECOND"}')
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/metricTypes/ResponseTime", response.headers.Location)

        response = client.post(path: "$tenantId/resourceTypes/URL/metricTypes",
                body: "{\"id\":\"ResponseTime\"}")
        assertEquals(204, response.status)

        response = client.post(path: "$tenantId/$environmentId/metrics",
                body: "{\"id\": \"host1_ping_response\", \"metricTypeId\": \"ResponseTime\"}");
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/$environmentId/metrics/host1_ping_response", response.headers.Location)

        response = client.post(path: "$tenantId/$environmentId/resources",
            body: "{\"id\": \"host1\", \"resourceTypeId\": \"URL\"}")
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/$environmentId/resources/host1", response.headers.Location)

        response = client.post(path: "$tenantId/$environmentId/resources/host1/metrics",
            body: "[\"host1_ping_response\"]");
        assertEquals(204, response.status)


        tenantId = "com.example.tenant"
        environmentId = "test"

        response = client.post(path: "tenants", body: "{\"id\":\"$tenantId\"}")
        assertEquals(201, response.status)
        assertEquals(baseURI + "tenants/$tenantId", response.headers.Location)

        response = client.post(path: "$tenantId/environments", body: "{\"id\":\"$environmentId\"}")
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/environments/$environmentId", response.headers.Location)

        response = client.post(path: "$tenantId/resourceTypes", body: '{"id":"Kachna", "version":"1.0"}')
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/resourceTypes/Kachna", response.headers.Location)

        response = client.post(path: "$tenantId/resourceTypes", body: '{"id":"Playroom", "version":"1.0"}')
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/resourceTypes/Playroom", response.headers.Location)

        response = client.post(path: "$tenantId/metricTypes", body: '{"id":"Size", "unit":"BYTE"}')
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/metricTypes/Size", response.headers.Location)

        response = client.post(path: "$tenantId/resourceTypes/Playroom/metricTypes",
                body: "{\"id\":\"Size\"}")
        assertEquals(204, response.status)

        response = client.post(path: "$tenantId/$environmentId/metrics",
                body: "{\"id\": \"playroom1_size\", \"metricTypeId\": \"Size\"}");
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/$environmentId/metrics/playroom1_size", response.headers.Location)

        response = client.post(path: "$tenantId/$environmentId/metrics",
                body: "{\"id\": \"playroom2_size\", \"metricTypeId\": \"Size\"}");
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/$environmentId/metrics/playroom2_size", response.headers.Location)

        response = client.post(path: "$tenantId/$environmentId/resources",
                body: "{\"id\": \"playroom1\", \"resourceTypeId\": \"Playroom\"}")
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/$environmentId/resources/playroom1", response.headers.Location)

        response = client.post(path: "$tenantId/$environmentId/resources",
                body: "{\"id\": \"playroom2\", \"resourceTypeId\": \"Playroom\"}")
        assertEquals(201, response.status)
        assertEquals(baseURI + "$tenantId/$environmentId/resources/playroom2", response.headers.Location)

        response = client.post(path: "$tenantId/$environmentId/resources/playroom1/metrics",
                body: "[\"playroom1_size\"]");
        assertEquals(204, response.status)

        response = client.post(path: "$tenantId/$environmentId/resources/playroom2/metrics",
                body: "[\"playroom2_size\"]");
        assertEquals(204, response.status)
    }

    @AfterClass
    static void deleteEverything() {
        def response = client.delete(path : "tenants/com.acme.tenant")
        assertEquals(204, response.status)

        response = client.delete(path : "tenants/com.example.tenant")
        assertEquals(204, response.status)
    }

    @Test
    void ping() {
        def response = client.get(path: "")
        assertEquals(200, response.status)
    }

    public static void assertEntityExists(path, id) {
        def response = client.get(path: path)
        assert id.equals(response.data.id)
    }

    public static void assertEntitiesExist(path, ids) {
        def response = client.get(path: path)

        //noinspection GroovyAssignabilityCheck
        def expectedIds = new ArrayList<>(ids)
        def entityIds = response.data.collect{ it.id }
        ids.forEach{entityIds.remove(it); expectedIds.remove(it)}

        assert entityIds.empty : "Unexpected entities with ids: " + entityIds
        assert expectedIds.empty : "Following entities not found: " + expectedIds
    }

    public static void assertRelationshipExists(path, source, label, target) {
        def response = client.get(path: path)
        def needle = new Tuple(source, label, target);
        def haystack = response.data.collect{ new Tuple(it["inv:source"]["inv:shortId"], it["inv:label"],
                it["inv:target"]["inv:shortId"])  }
        assert haystack.any{it == needle} : "Following edge not found: " + needle
        haystack.clear()
    }
}
