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

import org.hawkular.inventory.api.model.Entity
import org.junit.BeforeClass
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * Test some basic inventory functionality via REST
 * @author Heiko W. Rupp
 */
class RestTest extends AbstractTestBase{

    @BeforeClass
    static void setupData() {
        def tenantId = "com.acme.tenant"
        def environmentId = "production"

        def response = client.post(path: "tenants", body: "{\"id\":\"$tenantId\"}")
        assertEquals(200, response.status)
        assertEquals(tenantId, response.data.id)

        response = client.post(path: "$tenantId/environments", body: "{\"id\":\"$environmentId\"}")
        assertEquals(200, response.status)
        assertEquals(environmentId, response.data.id)

        response = client.post(path: "$tenantId/resourceTypes", body: '{"id":"URL", "version":"1.0"}')
        assertEquals(200, response.status)
        assertEquals("URL", response.data.id)

        response = client.post(path: "$tenantId/metricTypes", body: '{"id":"ResponseTime", "unit" : "ms"}')
        assertEquals(200, response.status)
        assertEquals("ResponseTime", response.data.id)

        response = client.post(path: "$tenantId/resourceTypes/URL/metricTypes",
                body: "{\"id\":\"ResponseTime\"}")
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/metrics",
                body: "{\"id\": \"host1_ping_response\", \"metricTypeId\": \"ResponseTime\"}");
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/resources",
            body: "{\"id\": \"host1\", \"type\": {\"id\": \"URL\", \"version\": \"1.0\"}}")
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/resources/host1/metrics",
            body: "[\"host1_ping_response\"]");
        assertEquals(200, response.status)


        tenantId = "com.example.tenant"
        environmentId = "test"

        response = client.post(path: "tenants", body: "{\"id\":\"$tenantId\"}")
        assertEquals(200, response.status)
        assertEquals(tenantId, response.data.id)

        response = client.post(path: "$tenantId/environments", body: "{\"id\":\"$environmentId\"}")
        assertEquals(200, response.status)
        assertEquals(environmentId, response.data.id)

        response = client.post(path: "$tenantId/resourceTypes", body: '{"id":"Kachna", "version":"1.0"}')
        assertEquals(200, response.status)
        assertEquals("Kachna", response.data.id)

        response = client.post(path: "$tenantId/resourceTypes", body: '{"id":"Playroom", "version":"1.0"}')
        assertEquals(200, response.status)
        assertEquals("Playroom", response.data.id)

        response = client.post(path: "$tenantId/metricTypes", body: '{"id":"Size", "unit":"b"}')
        assertEquals(200, response.status)
        assertEquals("Size", response.data.id)

        response = client.post(path: "$tenantId/resourceTypes/Playroom/metricTypes",
                body: "{\"id\":\"Size\"}")
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/metrics",
                body: "{\"id\": \"playroom1_size\", \"metricTypeId\": \"Size\"}");
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/metrics",
                body: "{\"id\": \"playroom2_size\", \"metricTypeId\": \"Size\"}");
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/resources",
                body: "{\"id\": \"playroom1\", \"type\": {\"id\": \"Playroom\", \"version\": \"1.0\"}}")
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/resources",
                body: "{\"id\": \"playroom2\", \"type\": {\"id\": \"Playroom\", \"version\": \"1.0\"}}")
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/resources/playroom1/metrics",
                body: "[\"playroom1_size\"]");
        assertEquals(200, response.status)

        response = client.post(path: "$tenantId/$environmentId/resources/playroom2/metrics",
                body: "[\"playroom2_size\"]");
        assertEquals(200, response.status)
    }

    @Test
    void ping() {
        def response = client.get(path: "")
        assertEquals(200, response.status)
    }

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

    private static void assertEntityExists(path, id) {
        def response = client.get(path: path)
        assert id.equals(response.data.id)
    }

    private static void assertEntitiesExist(path, ids) {
        def response = client.get(path: path)

        //noinspection GroovyAssignabilityCheck
        def expectedIds = new ArrayList<>(ids)
        def entityIds = response.data.collect{ it.id }
        ids.forEach{entityIds.remove(it); expectedIds.remove(it)}

        assert entityIds.empty : "Unexpected entities with ids: " + entityIds
        assert expectedIds.empty : "Following entities not found: " + expectedIds
    }

//    @Test
//    void addGetOne() {
//
//        def res = new Resource()
//        res.setType(ResourceType.URL)
//        res.addParameter("url","http://hawkular.org")
//
//        def tenantId = "rest-test";
//
//        def response = client.post(path: "$tenantId/resources", body: res)
//        assertEquals(200, response.status)
//
//
//        def data = response.data
//        def id = data.id
//
//        assertNotEquals("", id, "Id should not be empty")
//
//        response = client.get(path: "$tenantId/resource/$id")
//
//        assertEquals(200, response.status)
//        assertEquals(id,response.data.id)
//    }
//
//    @Test
//    void addOneFindByType() {
//
//        def res = new Resource()
//        res.setType(ResourceType.URL)
//        res.addParameter("url","http://hawkular.org")
//
//        def tenantId = "rest-test3";
//
//        def response = client.post(path: "$tenantId/resources", body: res)
//        assertEquals(200, response.status)
//
//        def data = response.data
//        def id = data.id
//
//        assertNotEquals("", id, "Id should not be empty")
//
//        try {
//            response = client.get(path: "$tenantId/resources", query: [type: "url"] )
//
//            assertEquals(200, response.status)
//            assert response.data.size() > 0
//            assertEquals(id,response.data[0].id)
//        } finally {
//            response = client.delete(path: "$tenantId/resource/$id");
//            assertEquals(200, response.status)
//        }
//
//
//    }
//
//    @Test
//    void addOneFindNoType() {
//
//        def res = new Resource()
//        res.setType(ResourceType.URL)
//        res.addParameter("url","http://hawkular.org")
//
//        def tenantId = "rest-test4";
//
//        def response = client.post(path: "$tenantId/resources", body: res)
//        assertEquals(200, response.status)
//
//        def data = response.data
//        def id = data.id
//
//        assertNotEquals("", id, "Id should not be empty")
//
//        try {
//            response = client.get(path: "$tenantId/resources" )
//
//            assertEquals(200, response.status)
//            assert response.data.size() > 0
//            assertEquals(id,response.data[0].id)
//        } finally {
//            response = client.delete(path: "$tenantId/resource/$id");
//            assertEquals(200, response.status)
//
//        }
//
//
//    }
//
//    @Test
//    void addGetWrongTenant() {
//
//        def res = new Resource()
//        res.setType(ResourceType.URL)
//        res.addParameter("url", "http://hawkular.org")
//
//        def tenantId = "rest-test";
//
//        def response = client.post(path: "$tenantId/resources", body: res)
//        assertEquals(200, response.status)
//
//
//        def data = response.data
//        def id = data.id
//
//        assertNotEquals("", id, "Id should not be empty")
//
//        try {
//            client.get(path: "XX$tenantId/resource/$id")
//            // We should never hit the next line
//            assert false;
//        } catch (HttpResponseException e) {
//            ; // this is good
//        }
//    }
//
//    @Test
//    void addGetDeleteOne() {
//
//        def res = new Resource()
//        res.setType(ResourceType.URL)
//        res.setId("bla-bla")
//        res.addParameter("url","http://hawkular.org")
//
//        def tenantId = "rest-test";
//
//        def response = client.post(path: "$tenantId/resources", body: res)
//        assertEquals(200, response.status)
//        assertEquals("bla-bla",response.data.id)
//
//
//        def data = response.data
//        def id = data.id
//
//        assertNotEquals("", id, "Id should not be empty")
//
//        response = client.get(path: "$tenantId/resource/$id")
//
//        assertEquals(200, response.status)
//        assertEquals(id,response.data.id)
//
//        response = client.delete(path: "$tenantId/resource/$id")
//        assertEquals(200, response.status)
//
//        try {
//            client.get(path: "$tenantId/resource/$id")
//            assert false;
//        } catch (HttpResponseException e) {
//            ; // this is good
//        }
//
//    }
//
//    @Test
//    public void testAddAndUpdateMetricToResource() throws Exception {
//
//        def res = new Resource()
//        res.setType(ResourceType.URL)
//        res.addParameter("url","http://hawkular.org")
//
//        def tenantId = "rest-test";
//
//        def response = client.post(path: "$tenantId/resources", body: res)
//        assertEquals(200, response.status)
//
//        def rid = response.data.id
//
//        client.put(path: "$tenantId/resource/$rid/metrics", body: ["cpu.load1"])
//        client.put(path: "$tenantId/resource/$rid/metrics", body: ["cpu.load5", "cpu.load15"])
//
//        def metricDefinition = new MetricDefinition("cpu.load1",MetricUnit.NONE); // name is on purpose like above
//        metricDefinition.description = "This is the one minute load of the CPU"
//        client.put(path: "$tenantId/resource/$rid/metrics", body: [ metricDefinition ])
//
//        response = client.get(path: "$tenantId/resource/$rid/metrics")
//
//        assertEquals(200,response.status)
//        def data = response.data
//
//        assert data.size() == 3
//
//        println(rid)
//
//        metricDefinition.unit = MetricUnit.BYTE;
//        response = client.put(path: "$tenantId/resource/$rid/metric/cpu.load1", body: metricDefinition);
//
//        assertEquals(200, response.status)
//
//        response = client.get(path: "$tenantId/resource/$rid/metric/cpu.load1")
//        assertEquals(200, response.status)
//
//        assertEquals("BYTE", response.data.unit)
//
//    }
//
//    @Test
//    public void testAddMetricToUnknownResource() throws Exception {
//
//        def tenantId = "bla"
//        def rid = "-1"
//        try {
//            client.put(path: "$tenantId/resource/$rid/metrics", body: ["cpu.load1"])
//            assert false : "We should have gotten a 404, but obviously didnt"
//        } catch (HttpResponseException e) {
//            ; // This is good
//        }
//    }

}
