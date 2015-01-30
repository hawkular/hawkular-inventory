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

import groovyx.net.http.HttpResponseException
import org.hawkular.inventory.api.Resource
import org.hawkular.inventory.api.ResourceType
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotEquals


/**
 * Test some basic inventory functionality via REST
 * @author Heiko W. Rupp
 */
class RestTest extends AbstractTestBase{


    @Test
    void ping() {
        def response = client.get(path: "")
        assertEquals(200, response.status)
    }

    @Test
    void addGetOne() {

        def res = new Resource()
        res.setType(ResourceType.URL)
        res.addParameter("url","http://hawkular.org")

        def tenantId = "rest-test";

        def response = client.post(path: "$tenantId/resources", body: res)
        assertEquals(200, response.status)


        def data = response.data
        def id = data.id

        assertNotEquals("", id, "Id should not be empty")

        response = client.get(path: "$tenantId/resource/$id")

        assertEquals(200, response.status)
        assertEquals(id,response.data.id)
    }

    @Test
    void addGetWrongTenant() {

        def res = new Resource()
        res.setType(ResourceType.URL)
        res.addParameter("url", "http://hawkular.org")

        def tenantId = "rest-test";

        def response = client.post(path: "$tenantId/resources", body: res)
        assertEquals(200, response.status)


        def data = response.data
        def id = data.id

        assertNotEquals("", id, "Id should not be empty")

        try {
            response = client.get(path: "XX$tenantId/resource/$id")
            // We should never hit the next line
            assert false;
        } catch (HttpResponseException e) {
            ; // this is good
        }
    }

    @Test
    void addGetDeleteOne() {

        def res = new Resource()
        res.setType(ResourceType.URL)
        res.setId("bla-bla")
        res.addParameter("url","http://hawkular.org")

        def tenantId = "rest-test";

        def response = client.post(path: "$tenantId/resources", body: res)
        assertEquals(200, response.status)
        assertEquals("bla-bla",response.data.id)


        def data = response.data
        def id = data.id

        assertNotEquals("", id, "Id should not be empty")

        response = client.get(path: "$tenantId/resource/$id")

        assertEquals(200, response.status)
        assertEquals(id,response.data.id)

        response = client.delete(path: "$tenantId/resource/$id")
        assertEquals(200, response.status)

        try {
            client.get(path: "$tenantId/resource/$id")
            assert false;
        } catch (HttpResponseException e) {
            ; // this is good
        }

    }

    @Test
    public void testAddMetricToResoure() throws Exception {

        def res = new Resource()
        res.setType(ResourceType.URL)
        res.addParameter("url","http://hawkular.org")

        def tenantId = "rest-test";

        def response = client.post(path: "$tenantId/resources", body: res)
        assertEquals(200, response.status)

        def rid = response.data.id

        client.put(path: "$tenantId/resource/$rid/metrics/cpu.load1")
        client.put(path: "$tenantId/resource/$rid/metrics/cpu.load5")
        client.put(path: "$tenantId/resource/$rid/metrics/cpu.load15")
        client.put(path: "$tenantId/resource/$rid/metrics/cpu.load15") // on purpose as the previous one

        response = client.get(path: "$tenantId/resource/$rid/metrics")

        assertEquals(200,response.status)
        def data = response.data

        assert data.size() == 3

    }
}
