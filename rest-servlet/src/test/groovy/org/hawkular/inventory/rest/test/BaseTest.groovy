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
class BaseTest extends RESTTest{


    @Test
    void ping() {
        def response = rhqm.get(path: "")
        assertEquals(200, response.status)
    }

    @Test
    void addGetOne() {

        def res = new Resource()
        res.setType(ResourceType.URL)
        res.addParameter("url","http://hawkular.org")

        def tenantId = "rest-test";

        def response = rhqm.post(path: "$tenantId/resources", body: res)
        assertEquals(200, response.status)


        def data = response.data
        def id = data.id

        assertNotEquals("", id, "Id should not be empty")

        response = rhqm.get(path: "$tenantId/resource/$id")

        assertEquals(200, response.status)
        assertEquals(id,response.data.id)
    }

    @Test
    void addGetWrongTenant() {

        def res = new Resource()
        res.setType(ResourceType.URL)
        res.addParameter("url", "http://hawkular.org")

        def tenantId = "rest-test";

        def response = rhqm.post(path: "$tenantId/resources", body: res)
        assertEquals(200, response.status)


        def data = response.data
        def id = data.id

        assertNotEquals("", id, "Id should not be empty")

        try {
            response = rhqm.get(path: "XX$tenantId/resource/$id")
            // We should never hit the next line
            assert false;
        } catch (HttpResponseException e) {
            ; // this is good
        }
    }

}
