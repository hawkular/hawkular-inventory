/*
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
import org.hawkular.inventory.api.model.CanonicalPath
import org.junit.*

import static org.hawkular.inventory.api.Relationships.WellKnown.*
import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail

/**
 * Test the basic inventory functionality via REST.
 *
 * @author Heiko W. Rupp
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @author jkremser
 */
class InventoryITest extends AbstractTestBase {
    private static final String basePath = "/hawkular/inventory"

    private static final String urlTypeId = "URL"
    private static final String testEnvId = "test"
    private static final String environmentId = "itest-env-" + UUID.randomUUID().toString()
    private static final String pingableHostRTypeId = "itest-pingable-host-" + UUID.randomUUID().toString()
    private static final String roomRTypeId = "itest-room-type-" + UUID.randomUUID().toString()
    private static final String copyMachineRTypeId = "itest-copy-machine-type-" + UUID.randomUUID().toString()
    private static final String date20150626 = "2015-06-26"
    private static final String date20160801 = "2016-08-01"
    private static final String expectedLifetime15years = "15y"
    private static final String facilitiesDept = "Facilities"
    private static final String itDept = "IT"
    private static final String typeVersion = "1.0"
    private static final String responseTimeMTypeId = "itest-response-time-" + UUID.randomUUID().toString()
    private static final String responseStatusCodeMTypeId = "itest-response-status-code-" + UUID.randomUUID().toString()
    private static final String statusDurationMTypeId = "status.duration.type"
    private static final String statusCodeMTypeId = "status.code.type"
    private static final String host1ResourceId = "itest-host1-" + UUID.randomUUID().toString();
    private static final String host2ResourceId = "itest-host2-" + UUID.randomUUID().toString();
    private static final String room1ResourceId = "itest-room1-" + UUID.randomUUID().toString();
    private static final String copyMachine1ResourceId = "itest-copy-machine1-" + UUID.randomUUID().toString();
    private static final String copyMachine2ResourceId = "itest-copy-machine2-" + UUID.randomUUID().toString();
    private static final String responseTimeMetricId = "itest-response-time-" + host1ResourceId;
    private static final String responseStatusCodeMetricId = "itest-response-status-code-" + host1ResourceId;
    private static final String feedId = "itest-feed-" + UUID.randomUUID().toString();
    private static final String bulkResourcePrefix = "bulk-resource-" + UUID.randomUUID().toString();

    /* key is the path to delete while value is the path to GET to verify the deletion */
    private static Map<String, String> pathsToDelete = new LinkedHashMap();

    private static String tenantId;

    private static String customRelationId;

    @BeforeClass
    static void setupData() {

        /* Make sure we can access the tenant first.
         * We will do several attempts because race conditions
         * may happen between this script and WildFly Agent
         * who may have triggered the same initial tasks in Accounts */
        def response = null
        int attemptCount = 5;
        int delay = 500;
        String path = "/hawkular/accounts/personas/current"
        for (int i = 0; i < attemptCount; i++) {
            try {
                response = client.get(path: path)
                /* all is well, we can leave the loop */
                break;
            } catch (groovyx.net.http.HttpResponseException e) {
                /* some initial attempts may fail */
            }
            println "'$path' not ready yet, about to retry after $delay ms"
            /* sleep one second */
            Thread.sleep(delay);
        }
        if (response.status != 200) {
            Assert.fail("Getting path '$path' returned status ${response.status}, tried $attemptCount times");
        }
        tenantId = response.data.id

        /* Ensure the "test" env was autocreated.
         * We will do several attempts because race conditions
         * may happen between this script and WildFly Agent
         * who may have triggered the same initial tasks in Inventory.
         * A successfull GET to /hawkular/inventory/environments/test
         * should mean that all initial tasks are over */
        path = "$basePath/environments/$testEnvId"
        for (int i = 0; i < attemptCount; i++) {
            try {
                response = client.get(path: path)
                /* all is well, we can leave the loop */
                break;
            } catch (groovyx.net.http.HttpResponseException e) {
                /* some initial attempts may fail */
            }
            println "'$path' not ready yet, about to retry after $delay ms"
            /* sleep one second */
            Thread.sleep(delay);
        }
        if (response.status != 200) {
            Assert.fail("Getting path '$path' returned status ${response.status}, tried $attemptCount times");
        }

        /* Create an environment that will be used exclusively by this test */
        response = postDeletable(path: "environments", body: [id : environmentId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/environments/$environmentId", response.headers.Location)

        /* URL resource type should have been autocreated */
        response = client.get(path: "$basePath/resourceTypes/$urlTypeId")
        assertEquals(200, response.status)
        assertEquals(urlTypeId, response.data.id)

        /* Create pingable host resource type */
        response = postDeletable(path: "resourceTypes",
            body: [
                id : pingableHostRTypeId
            ])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/resourceTypes/$pingableHostRTypeId", response.headers.Location)

        /* Create room resource type */
        response = postDeletable(path: "resourceTypes",
            body: [
                id : roomRTypeId,
                properties : [expectedLifetime : expectedLifetime15years, ownedByDepartment : facilitiesDept]
            ])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/resourceTypes/$roomRTypeId", response.headers.Location)

        /* Create copy machine resource type */
        response = postDeletable(path: "resourceTypes",
            body: [
                id : copyMachineRTypeId,
                properties : [expectedLifetime : expectedLifetime15years, ownedByDepartment : itDept]
            ])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/resourceTypes/$copyMachineRTypeId", response.headers.Location)


        /* Create a metric type */
        response = postDeletable(path: "metricTypes", body: [id : responseTimeMTypeId, unit : "MILLISECONDS",
                type: "COUNTER", collectionInterval: "1"])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/metricTypes/$responseTimeMTypeId", response.headers.Location)

        /* Create another metric type */
        response = postDeletable(path: "metricTypes", body: [id : responseStatusCodeMTypeId, unit : "NONE",
                type: "GAUGE", collectionInterval: "1"])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/metricTypes/$responseStatusCodeMTypeId", response.headers.Location)

        /* link pingableHostRTypeId with responseTimeMTypeId and responseStatusCodeMTypeId */
        path = "$basePath/resourceTypes/$pingableHostRTypeId/metricTypes"
        //just testing that both relative and canonical paths work when referencing the types
        response = client.post(path: path,
                body: ["../$responseTimeMTypeId".toString(), "/$responseStatusCodeMTypeId".toString()])

        assertEquals(204, response.status)
        //we will try deleting the associations between resource types and metric types, too
        //this is not necessary because deleting either the resource type or the metric type will take care of it anyway
        //but this is to test that explicit deletes work, too
        pathsToDelete.put("$path/../$responseTimeMTypeId", "$path/../$responseTimeMTypeId")
        pathsToDelete.put("$path/../$responseStatusCodeMTypeId", "$path/../$responseStatusCodeMTypeId")

        /* add a metric */
        response = postDeletable(path: "$environmentId/metrics",
                body: [id: responseTimeMetricId, metricTypePath: "../" + responseTimeMTypeId]);
        //path relative to env
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/metrics/$responseTimeMetricId", response.headers.Location)

        /* add another metric */
        response = postDeletable(path: "$environmentId/metrics",
                //now try using canonical path for referencing the metric type
                body: [id: responseStatusCodeMetricId, metricTypePath: "/$responseStatusCodeMTypeId".toString()]);
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/metrics/$responseStatusCodeMetricId", response.headers.Location)

        /* add a resource */
        response = postDeletable(path: "$environmentId/resources",
                body: [id: host1ResourceId, resourceTypePath: "../$pingableHostRTypeId".toString()])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$host1ResourceId", response.headers.Location)

        /* add another resource */
        response = postDeletable(path: "$environmentId/resources",
                body: [id: host2ResourceId, resourceTypePath: "/" + pingableHostRTypeId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$host2ResourceId", response.headers.Location)

        /* add a room resource */
        response = postDeletable(path: "$environmentId/resources",
                body: [id        : room1ResourceId, resourceTypePath: "/$roomRTypeId".toString(),
                properties: [purchaseDate: date20150626]])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$room1ResourceId", response.headers.Location)

        /* add a copy machine resource */
        response = postDeletable(path: "$environmentId/resources",
                body: [id: copyMachine1ResourceId, resourceTypePath: "/" + copyMachineRTypeId,
                properties : [purchaseDate : date20150626,
                nextMaintenanceDate : date20160801] ])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$copyMachine1ResourceId", response.headers.Location)

        response = postDeletable(path: "$environmentId/resources",
                body: [id: copyMachine2ResourceId, resourceTypePath: "../" + copyMachineRTypeId,
                properties : [purchaseDate : date20160801] ])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$copyMachine2ResourceId", response.headers.Location)

        /* add child resources */
        response = postDeletable(path: "$environmentId/resources/$room1ResourceId",
                body: [id: "table", resourceTypePath: "/" + roomRTypeId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$room1ResourceId/table",
                response.headers.Location)
        response = postDeletable(path: "$environmentId/resources/$room1ResourceId/table",
                body: [id: "leg/1", resourceTypePath: "/" + roomRTypeId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$room1ResourceId/table/leg%2F1",
                response.headers.Location)
        response = postDeletable(path: "$environmentId/resources/$room1ResourceId/table",
                body: [id: "leg 2", resourceTypePath: "/" + roomRTypeId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$room1ResourceId/table/leg%202",
                response.headers.Location)
        response = postDeletable(path: "$environmentId/resources/$room1ResourceId/table",
                body: [id: "leg;3", resourceTypePath: "/" + roomRTypeId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$room1ResourceId/table/leg;3",
                response.headers.Location)
        response = postDeletable(path: "$environmentId/resources/$room1ResourceId/table",
                body: [id: "leg-4", resourceTypePath: "/" + roomRTypeId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/$room1ResourceId/table/leg-4",
                response.headers.Location)

        //alternative child hierarchies
        response = postDeletable(path: "$environmentId/resources",
                body: [id: "weapons", resourceTypePath: "/" + roomRTypeId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/$environmentId/resources/weapons",
                response.headers.Location)

        path = "$basePath/$environmentId/resources/weapons/children"
        response = client.post(path: path,
                body: ["/e;" + environmentId + "/r;" + room1ResourceId + "/r;table/r;leg%2F1", "../" + room1ResourceId
                        + "/table/leg-4"])
        assertEquals(204, response.status)
        pathsToDelete.put("$path/../table/leg%2F1", "$path/../table/leg%2F1")
        pathsToDelete.put("$path/../table/leg-4", "$path/../table/leg-4")

        /* link the metric to resource */
        path = "$basePath/$environmentId/resources/$host1ResourceId/metrics"
        response = client.post(path: path,
                body: ["/e;$environmentId/m;$responseTimeMetricId".toString(),
                       "/e;$environmentId/m;$responseStatusCodeMetricId".toString()]);
        assertEquals(204, response.status)
        pathsToDelete.put("$path/../$responseTimeMetricId", "$path/../$responseTimeMetricId")
        pathsToDelete.put("$path/../$responseStatusCodeMetricId", "$path/../$responseStatusCodeMetricId")

        /* add a feed */
        response = postDeletable(path: "feeds", body: [id: feedId])
        assertEquals(201, response.status)
        assertEquals(baseURI + "$basePath/feeds/$feedId", response.headers.Location)

        /* add a custom relationship, no need to clean up, it'll be deleted together with the resources */
        def relation = [id        : 42, // it's ignored anyway
                        source    : "/t;" + tenantId + "/e;" + environmentId + "/r;" + host2ResourceId,
                        name      : "inTheSameRoom",
                        target    : "/t;" + tenantId + "/e;" + environmentId + "/r;" + host1ResourceId,
                        properties: [
                            from      : "2000-01-01",
                            confidence: "90%"
                        ]]
        response = client.post(path: "$basePath/relationshipsOf/e;$environmentId/r;$host2ResourceId",
                body: relation)

        customRelationId = response.headers.Location.tokenize('/').last()
        assertEquals(201, response.status)

        // add operation type to the resource type
        response = postDeletable(path: "resourceTypes/$pingableHostRTypeId/operationTypes",
                body: [id: "start"])
        assertEquals(201, response.status)

        response = postDeletable(path: "resourceTypes/$pingableHostRTypeId/operationTypes",
                body: [id: "stop"])
        assertEquals(201, response.status)

        // add some parameters to it
        def startOpParamTypes = [role: "parameterTypes", value: [title     : "blah", type: "object",
                                                                 properties: [quick: [type: "boolean"]]]]
        response = client.post(path: "$basePath/resourceTypes/$pingableHostRTypeId/operationTypes/start/data",
                body: startOpParamTypes)
        assertEquals(201, response.status)

        response = client.post(path: "$basePath/resourceTypes/$pingableHostRTypeId/operationTypes/start/data",
                body: [role: "returnType", value: [title: "blah", type: "boolean"]])
        assertEquals(201, response.status)

        /* add a resource type json schema */
        def schema = [
            value: [
                title     : "Character",
                type      : "object",
                properties: [
                    firstName : [type: "string"],
                    secondName: [type: "string"],
                    age       : [
                        type            : "integer",
                        minimum         : 0,
                        exclusiveMinimum: false
                    ],
                    male      : [
                        description: "true if the character is a male",
                        type       : "boolean"
                    ],
                    foo       : [
                        type      : "object",
                        properties: [
                            something: [type: "string"],
                            someArray: [
                                type       : "array",
                                minItems   : 3,
                                items      : [type: "integer"],
                                uniqueItems: false
                            ],
                            foo      : [
                                type      : "object",
                                properties: [
                                    question: [
                                        type   : "string",
                                        pattern: "^.*\\?\$"
                                    ],
                                    answer  : [
                                        description: "the answer (example of any type)"
                                    ],
                                    foo     : [
                                        type      : "object",
                                        properties: [
                                            foo: [
                                                type      : "object",
                                                properties: [
                                                    fear : [
                                                        type: "string",
                                                        enum: ["dentists", "lawyers", "rats"]
                                                    ]
                                                ]
                                            ]
                                        ]
                                    ]
                                ]
                            ]
                        ]
                    ]
                ],
                required  : ["firstName", "secondName", "male", "age", "foo"]
            ],
            role : "configurationSchema"
        ]
        response = client.post(path: "$basePath/resourceTypes/$pingableHostRTypeId/data",
                body: schema)
        assertEquals(201, response.status)

        /* add an invalid config data to a resource (invalid ~ not valid against the json schema) */
        def invalidData = [
            value: [
                firstName : "John",
                secondName: "Smith"
            ]
            ,
            role : "configuration"
        ]

        try {
            response = client.post(path: "$basePath/$environmentId/resources/$host2ResourceId/data",
                body: invalidData)
            Assert.fail("groovyx.net.http.HttpResponseException expected")
        } catch (groovyx.net.http.HttpResponseException e) {
            /* validation should fail resulting in http 400 (BAD REQUEST) */
            assertEquals("Bad Request", e.getMessage())
        }

        /* add a config data to a resource, no need to clean up, it'll be deleted together with the resources */
        def data = [
            value     : [
                firstName : "Winston",
                secondName: "Smith",
                sdf       : "sdf",
                male      : true,
                age       : 42,
                foo       : [
                    something: "whatever",
                    someArray: [1, 1, 2, 3, 5, 8],
                    foo      : [
                        answer  : 5,
                        question: "2+2=?",
                        foo     : [
                            foo: [
                                fear: "rats"
                            ]
                        ]
                    ]
                ]
            ],
            role      : "configuration",
            properties: [
                war      : "peace",
                freedom  : "slavery",
                ignorance: "strength"
            ]
        ]
        response = client.post(path: "$basePath/$environmentId/resources/$host2ResourceId/data",
                body: data)
        assertEquals(201, response.status)

        /* add a config data to a child resource, no need to clean up, it'll be deleted together with the resources */
//        def simpleData = [
//            value: [
//                a: 1,
//                b: "abc",
//                c: true
//            ],
//            role : "connectionConfiguration"
//        ]
//        response = client.post(path: "$basePath/$environmentId/resources/$room1ResourceId%2Ftable/data",
//            body: simpleData)
//        assertEquals(201, response.status)
    }

    @AfterClass
    static void deleteEverything() {
        /* the following would delete all data of the present user. We cannot do that as long as we do not have
         * a dedicated user for running this very single test class. */
        // def response = client.delete(path : "$basePath/tenant")
        // assertEquals(204, response.status)

        /* Let's delete the entities one after another in the reverse order as we created them */
        List<Map.Entry> entries = new ArrayList<Map.Entry>(pathsToDelete.entrySet())
        Collections.reverse(entries)
        for (Map.Entry en : entries) {
            String path = en.getKey();
            String getValidationPath = en.getValue();
            try {
                def response = client.delete(path: path)
                assertEquals(204, response.status)
            } catch (groovyx.net.http.HttpResponseException e) {
                println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                println(path)
                println(e.getMessage())
                println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
            }

            if (getValidationPath != null) {
                try {
                    def response = client.get(path: getValidationPath)
                    Assert.fail("The path '$getValidationPath' should not exist after the entity was deleted")
                } catch (groovyx.net.http.HttpResponseException e) {
                    assertEquals("Error message for path '$path'", "Not Found", e.getMessage())
                }
            }
        }


    }

    @Test
    void ping() {
        def response = client.get(path: "$basePath")
        assertEquals(200, response.status)
    }

    @Test
    void testEnvironmentsCreated() {
        assertEntitiesExist("environments", ["/e;$testEnvId".toString(),
                                             "/e;$environmentId".toString()])
    }

    @Test
    void testResourceTypesCreated() {
        assertEntityExists("resourceTypes/$urlTypeId", "/rt;" + urlTypeId)
        assertEntityExists("resourceTypes/$pingableHostRTypeId", "/rt;" + pingableHostRTypeId)
        assertEntityExists("resourceTypes/$roomRTypeId", "/rt;" + roomRTypeId)

        // commented out as it interfers with WildFly Agent
        // assertEntitiesExist("resourceTypes", [urlTypeId, pingableHostRTypeId, roomRTypeId])

    }

    @Test
    void testMetricTypesCreated() {
        assertEntityExists("metricTypes/$responseTimeMTypeId", "/mt;" + responseTimeMTypeId)
        assertEntityExists("metricTypes/$statusDurationMTypeId", "/mt;" + statusDurationMTypeId)
        assertEntityExists("metricTypes/$statusCodeMTypeId", "/mt;" + statusCodeMTypeId)
        // commented out as it interfers with WildFly Agent
        // assertEntitiesExist("metricTypes",
        //    [responseTimeMTypeId, responseStatusCodeMTypeId, statusDurationMTypeId, statusCodeMTypeId])
    }

    @Test
    void testOperationTypesCreated() {
        def response = client.get(path: "$basePath/resourceTypes/$pingableHostRTypeId/operationTypes")
        assertEquals(2, response.data.size())

        assertEntityExists("resourceTypes/$pingableHostRTypeId/operationTypes/start", "/rt;" + pingableHostRTypeId +
                "/ot;start")
        assertEntityExists("resourceTypes/$pingableHostRTypeId/operationTypes/start/data",
                [dataType: "returnType"], "/rt;" + pingableHostRTypeId + "/ot;start/d;returnType")
        assertEntityExists("resourceTypes/$pingableHostRTypeId/operationTypes/start/data",
                [dataType: "parameterTypes"], "/rt;" + pingableHostRTypeId + "/ot;start/d;parameterTypes")
    }

    @Test
    void testMetricTypesLinked() {
        assertEntitiesExist("resourceTypes/$pingableHostRTypeId/metricTypes",
                ["/mt;$responseTimeMTypeId".toString(),
                 "/mt;$responseStatusCodeMTypeId".toString()])
    }

    @Test
    void testResourcesCreated() {
        assertEntityExists("$environmentId/resources/$host1ResourceId", "/e;" + environmentId + "/r;" + host1ResourceId)
        assertEntityExists("$environmentId/resources/$host2ResourceId", "/e;" + environmentId + "/r;" + host2ResourceId)
        assertEntityExists("$environmentId/resources/$room1ResourceId", "/e;" + environmentId + "/r;" + room1ResourceId)
    }

    @Test
    void testResourcesFilters() {

        /* filter by resource properties */
        def response = client.get(path: "$basePath/$environmentId/resources",
            query: ["properties" : "purchaseDate:$date20150626", sort: "id"])
        assertEquals(2, response.data.size())
        assertEquals(copyMachine1ResourceId, response.data.get(0).id)
        assertEquals(room1ResourceId, response.data.get(1).id)

        response = client.get(path: "$basePath/$environmentId/resources",
            query: ["properties" : "nextMaintenanceDate:$date20160801"])
        assertEquals(1, response.data.size())
        assertEquals(copyMachine1ResourceId, response.data.get(0).id)

        /* query by two props at once */
        response = client.get(path: "$basePath/$environmentId/resources",
            query: ["properties" : "nextMaintenanceDate:$date20160801,purchaseDate:$date20150626"])
        assertEquals(1, response.data.size())
        assertEquals(copyMachine1ResourceId, response.data.get(0).id)

        /* query by property existence */
        response = client.get(path: "$basePath/$environmentId/resources",
            query: ["properties" : "purchaseDate", sort: "id"])
        assertEquals(3, response.data.size())
        assertEquals(copyMachine1ResourceId, response.data.get(0).id)
        assertEquals(copyMachine2ResourceId, response.data.get(1).id)
        assertEquals(room1ResourceId, response.data.get(2).id)

        /* filter by type */
        response = client.get(path: "$basePath/$environmentId/resources",
            query: ["type.id": pingableHostRTypeId])
        assertEquals(2, response.data.size())

        response = client.get(path: "$basePath/$environmentId/resources",
            query: ["type.id": roomRTypeId, "type.version": typeVersion])
        assertEquals(2, response.data.size())

    }

    @Test
    void testMetricsCreated() {
        assertEntityExists("$environmentId/metrics/$responseTimeMetricId",
                "/e;$environmentId/m;$responseTimeMetricId".toString())
        assertEntityExists("$environmentId/metrics/$responseStatusCodeMetricId",
                "/e;$environmentId/m;$responseStatusCodeMetricId".toString())
        assertEntitiesExist("$environmentId/metrics", ["/e;$environmentId/m;$responseTimeMetricId".toString(),
                                                       "/e;$environmentId/m;$responseStatusCodeMetricId".toString()])
    }

    @Test
    void testMetricsLinked() {
        assertEntitiesExist("$environmentId/resources/$host1ResourceId/metrics",
                ["/e;" + environmentId + "/m;" + responseTimeMetricId, "/e;" + environmentId + "/m;" +
               responseStatusCodeMetricId])
    }

    @Test
    void testConfigCreated() {
        print "--- testConfigCreated ---"
        assertEntityExists("$environmentId/resources/$host2ResourceId/data",
                "/e;" + environmentId + "/r;" + host2ResourceId + "/d;configuration")
//        assertEntitiesExist("$environmentId/resources/$host2ResourceId%2Ftable/data?dataType=connectionConfiguration",
//                ["/e;" + environmentId + "/r;" + host2ResourceId + "/d;connectionConfiguration"])
    }

    @Test
    void testPaging() {
        String path = "$basePath/$environmentId/resources"
        def response = client.get(path: path, query: ["type.id": pingableHostRTypeId, page: 0, per_page: 2, sort: "id"])
        assertEquals(2, response.data.size())

        def first = response.data.get(0)
        def second = response.data.get(1)

        response = client.get(path: path, query: ["type.id": pingableHostRTypeId, page: 0, per_page: 1, sort: "id"])
        assertEquals(1, response.data.size())
        assertEquals(first, response.data.get(0))

        response = client.get(path: path, query: ["type.id": pingableHostRTypeId, page: 1, per_page: 1, sort: "id"])
        assertEquals(1, response.data.size())
        assertEquals(second, response.data.get(0))

        response = client.get(path: path, query: ["type.id": pingableHostRTypeId, page : 0, per_page: 1, sort: "id",
                                                                               order: "desc"])
        assertEquals(1, response.data.size())
        assertEquals(second, response.data.get(0))

        response = client.get(path: path, query: ["type.id": pingableHostRTypeId, page : 1, per_page: 1, sort: "id",
                                                                               order: "desc"])
        assertEquals(1, response.data.size())
        assertEquals(first, response.data.get(0))
    }

    @Test
    void testTenantsContainEnvironments() {
        assertRelationshipExists("relationshipsOf/t;$tenantId",
                "/t;$tenantId",
                contains.name(),
                "/t;$tenantId/e;$environmentId")

        assertRelationshipJsonldExists("relationshipsOf/t;$tenantId",
                tenantId,
                contains.name(),
                environmentId)
    }

    @Test
    void testTenantsContainResourceTypes() {
        // tenant is auto-inferred (but it can be provided)
        assertRelationshipExists("relationshipsOf/rt;$urlTypeId",
                "/t;$tenantId",
                contains.name(),
                "/t;$tenantId/rt;$urlTypeId")

        assertRelationshipExists("relationshipsOf/t;$tenantId",
                "/t;$tenantId",
                contains.name(),
                "/t;$tenantId/rt;$pingableHostRTypeId")
    }

    @Test
    void testTenantsContainMetricTypes() {
        assertRelationshipExists("relationshipsOf/t;$tenantId/mt;$responseTimeMTypeId",
                "/t;$tenantId",
                contains.name(),
                "/t;$tenantId/mt;$responseTimeMTypeId")

        assertRelationshipExists("relationshipsOf/t;$tenantId",
                "/t;$tenantId",
                contains.name(),
                "/t;$tenantId/mt;$statusCodeMTypeId")
    }


    @Test
    void testEnvironmentsContainResources() {
        assertRelationshipExists("relationshipsOf/e;$environmentId",
                "/t;$tenantId/e;$environmentId",
                contains.name(),
                "/t;$tenantId/e;$environmentId/r;$host2ResourceId")

        assertRelationshipExists("relationshipsOf/e;$environmentId",
                "/t;$tenantId/e;$environmentId",
                contains.name(),
                "/t;$tenantId/e;$environmentId/r;$host1ResourceId")

        assertRelationshipJsonldExists("relationshipsOf/e;$environmentId",
                environmentId,
                contains.name(),
                host1ResourceId)

        assertRelationshipJsonldExists("relationshipsOf/t;$tenantId/e;$environmentId",
                environmentId,
                contains.name(),
                host2ResourceId)
    }

    @Test
    void testTenantsContainFeeds() {
        assertRelationshipExists("relationshipsOf/f;$feedId",
                "/t;$tenantId",
                contains.name(),
                "/t;$tenantId/f;$feedId")

        assertRelationshipJsonldExists("relationshipsOf/f;$feedId",
                tenantId,
                contains.name(),
                feedId)
    }

    @Test
    void testEnvironmentsContainMetrics() {
        assertRelationshipExists("relationshipsOf/e;$environmentId",
                "/t;$tenantId/e;$environmentId",
                contains.name(),
                "/t;$tenantId/e;$environmentId/m;$responseTimeMetricId")

        assertRelationshipExists("relationshipsOf/e;$environmentId",
                "/t;$tenantId/e;$environmentId",
                contains.name(),
                "/t;$tenantId/e;$environmentId/m;$responseStatusCodeMetricId")

        assertRelationshipJsonldExists("relationshipsOf/e;$environmentId",
                environmentId,
                contains.name(),
                responseTimeMetricId)

        assertRelationshipJsonldExists("relationshipsOf/e;$environmentId",
                environmentId,
                contains.name(),
                responseStatusCodeMetricId)
    }

    @Test
    void testResourceTypesOwnMetricTypes() {
        assertRelationshipExists("relationshipsOf/rt;$pingableHostRTypeId",
                "/t;$tenantId/rt;$pingableHostRTypeId".toString(),
                incorporates.name(),
                "/t;$tenantId/mt;$responseTimeMTypeId")

        assertRelationshipExists("relationshipsOf/mt;$responseStatusCodeMTypeId",
                "/t;$tenantId/rt;$pingableHostRTypeId",
                incorporates.name(),
                "/t;$tenantId/mt;$responseStatusCodeMTypeId")

        assertRelationshipJsonldExists("relationshipsOf/rt;$pingableHostRTypeId",
                pingableHostRTypeId,
                incorporates.name(),
                responseTimeMTypeId)
    }

    @Test
    void testResourcesOwnMetrics() {
        assertRelationshipExists("relationshipsOf/e;$environmentId/r;$host1ResourceId",
                "/t;$tenantId/e;$environmentId/r;$host1ResourceId",
                incorporates.name(),
                "/t;$tenantId/e;$environmentId/m;$responseStatusCodeMetricId")

        assertRelationshipExists("relationshipsOf/e;$environmentId/r;$host1ResourceId",
                "/t;$tenantId/e;$environmentId/r;$host1ResourceId",
                incorporates.name(),
                "/t;$tenantId/e;$environmentId/m;$responseTimeMetricId")

        assertRelationshipJsonldExists("relationshipsOf/e;$environmentId/r;$host1ResourceId",
                host1ResourceId,
                incorporates.name(),
                responseTimeMetricId)
    }

    @Test
    void testResourceTypesDefinesResources() {
        assertRelationshipExists("relationshipsOf/rt;$pingableHostRTypeId",
                "/t;$tenantId/rt;$pingableHostRTypeId",
                defines.name(),
                "/t;$tenantId/e;$environmentId/r;$host2ResourceId")
    }

    @Test
    void testMetricTypesDefinesMetrics() {
        assertRelationshipJsonldExists("relationshipsOf/mt;$responseStatusCodeMTypeId",
                responseStatusCodeMTypeId,
                defines.name(),
                responseStatusCodeMetricId)

        assertRelationshipJsonldExists("relationshipsOf/mt;$responseTimeMTypeId",
                responseTimeMTypeId,
                defines.name(),
                responseTimeMetricId)
    }

    @Test
    void testCustomRelationship() {
        assertRelationshipJsonldExists("relationshipsOf/e;$environmentId/r;$host2ResourceId",
                host2ResourceId,
                "inTheSameRoom",
                host1ResourceId)
    }

    @Test
    void testUpdateCustomRelationshipAndFiltering() {
        def relation = [id        : customRelationId,
                        source    : "/t;" + tenantId + "/e;" + environmentId + "/r;" + host2ResourceId,
                        name      : "inTheSameRoom",
                        target    : "/t;" + tenantId + "/e;" + environmentId + "/r;" + host1ResourceId,
                        properties: [
                            from      : "2000-01-01",
                            confidence: "95%"
                        ]]
        def response = client.put(path: "$basePath/relationshipsOf/e;$environmentId/r;$host2ResourceId",
            body: relation)
        assertEquals(204, response.status)

        response = client.get(path: "$basePath/relationshipsOf/e;$environmentId/r;$host2ResourceId",
            query: [property: "from", propertyValue: "2000-01-01"])
        assertEquals(200, response.status)
        assertEquals(1, response.data.size)
        assertEquals("95%", response.data[0].properties.confidence)
    }

    @Test
    void testDeleteCustomRelationship() {
        def response = client.get(path: "$basePath/relationships/$customRelationId")
        assertEquals(200, response.status)
        response = client.delete(path: "$basePath/relationships/$customRelationId")
        assertEquals(200, response.status)

        try {
            response = client.get(path: "$basePath/relationships/$customRelationId")
            Assert.fail("The relationship '$customRelationId' should not exist after it was deleted")
        } catch (groovyx.net.http.HttpResponseException e) {
            assertEquals("Not Found", e.getMessage())
        }
    }

    @Test
    void testRelationshipFiltering() {
        assertRelationshipExists("relationshipsOf/e;$environmentId/r;$host1ResourceId",
                "/t;$tenantId/e;$environmentId/r;$host2ResourceId",
                "inTheSameRoom",
                "/t;$tenantId/e;$environmentId/r;$host1ResourceId", [property: "from", propertyValue: "2000-01-01"])

        assertRelationshipExists("relationshipsOf/e;$environmentId/r;$host1ResourceId",
                "/t;$tenantId/e;$environmentId/r;$host2ResourceId",
                "inTheSameRoom",
                "/t;$tenantId/e;$environmentId/r;$host1ResourceId", [property: "confidence", propertyValue: "90%"])

        assertRelationshipExists("relationshipsOf/e;$environmentId/r;$host1ResourceId",
                "/t;$tenantId/e;$environmentId/r;$host2ResourceId",
                "inTheSameRoom",
                "/t;$tenantId/e;$environmentId/r;$host1ResourceId", [named: "inTheSameRoom"])
    }

    @Test
    void testResourceHierarchyQuerying() {
        assertEntitiesExist("$environmentId/resources/$room1ResourceId/children",
                ["/e;$environmentId/r;$room1ResourceId/r;table".toString()])

        def base = "/e;$environmentId/r;$room1ResourceId/r;table".toString()
        assertEntitiesExist("$environmentId/resources/$room1ResourceId/table/children",
                [base + "/r;leg%2F1", base + "/r;leg%202", base + "/r;leg;3", base + "/r;leg-4"])

        assertEntitiesExist("$environmentId/resources/weapons/children",
                ["/e;$environmentId/r;$room1ResourceId/r;table/r;leg%2F1".toString(),
                 "/e;$environmentId/r;$room1ResourceId/r;table/r;leg-4".toString()])
    }

    @Test
    @Ignore
    void testResourceBulkCreate() {
        def payload = "{\"/e;test\": {\"resource\": ["
        def rs = new ArrayList<String>()
        100.times {
            rs.add("{ \"id\": \"" + bulkResourcePrefix + "-" + it + "\", \"resourceTypePath\": \"/rt;" + roomRTypeId +
                    "\"}")
        }
        payload += String.join(",", rs) + "]}}"
        def response = client.post(path: "$basePath/bulk", body: payload)

        assertEquals(201, response.status)
        def codes = response.data as Map<String, Integer>
        assertEquals(100, codes.size())

        codes.keySet().forEach({
            def p = CanonicalPath.fromString(it);
            def env = p.ids().getEnvironmentId()
            def rid = p.ids().getResourcePath().getSegment().getElementId()
            client.delete(path: "$basePath/$env/resources/$rid")
        })
    }

    @Test
    void testResourceBulkCreateWithErrors() {
        def payload = "{\"/e;" + environmentId + "\": {\"resource\": ["
        def rs = new ArrayList<String>()
        //this should fail
        rs.add("{\"id\": \"" + room1ResourceId + "\", \"resourceTypePath\": \"/rt;" + roomRTypeId + "\"}")
        //this should succeed
        rs.add("{\"id\": \"" + bulkResourcePrefix + "+1\", \"resourceTypePath\": \"/rt;" + roomRTypeId + "\"}")

        payload += String.join(",", rs) + "]}}"
        def response = client.post(path: "$basePath/bulk", body: payload)

        assertEquals(201, response.status)
        def codes = response.data.resource as Map<String, Integer>
        assertEquals(2, codes.size())

        assertEquals(409, codes.get("/t;" + tenantId + "/e;" + environmentId + "/r;" + room1ResourceId))
        assertEquals(201, codes.get("/t;" + tenantId + "/e;" + environmentId + "/r;" + bulkResourcePrefix + "+1"))

        client.delete(path: "$basePath/$environmentId/resources/$bulkResourcePrefix+1")
    }

    @Test
    void testBulkCreateAndRelate() {
        def epath = "/t;$tenantId/e;$environmentId"
        def rpath = "$epath/r;$bulkResourcePrefix" + "+1"
        def mpath = "$epath/m;$responseTimeMetricId"
        def payload = '{"' + epath + '": {"resource": [' +
                '{"id": "' + bulkResourcePrefix + '+1", "resourceTypePath": "/rt;' + roomRTypeId + '"}]},' +
                '"' + rpath + '": {"relationship" : [' +
                '{"name": "incorporates", "otherEnd": "' + mpath + '", "direction": "outgoing"}]}}'

        def response = client.post(path: "$basePath/bulk", body: payload)

        assertEquals(201, response.status)
        def resourceCodes = response.data.resource as Map<String, Integer>
        def relationshipCodes = response.data.relationship as Map<String, Integer>

        assertEquals(1, resourceCodes.size())
        assertEquals(201, resourceCodes.get(rpath))

        assertEquals(1, relationshipCodes.size())
        assertEquals(201, relationshipCodes.entrySet().getAt(0).getValue())

        client.delete(path: "$basePath/$environmentId/resources/$bulkResourcePrefix+1/metrics/../$responseTimeMetricId")
        client.delete(path: "$basePath/$environmentId/resources/$bulkResourcePrefix+1")
    }

    @Test
    void testComplexBulkCreate() {
        def env1 = "bulk-env-" + UUID.randomUUID().toString()
        def env2 = "bulk-env-" + UUID.randomUUID().toString()
        def rt1 = "bulk-URL" + UUID.randomUUID().toString()
        def rt2 = "bulk-URL2" + UUID.randomUUID().toString()
        def mt1 = "bulk-ResponseTime" + UUID.randomUUID().toString()

        def payload = """
        {
          "/t;$tenantId": {
            "environment": [
               {
                 "id": "$env1",
                 "properties": {"key": "value"},
                 "outgoing": {
                   "customRel": ["/t;$tenantId"]
                 }
               },
               {
                 "id": "$env2",
                 "properties": {"key": "value2"}
               }
            ],
            "resourceType": [
               {
                 "id": "$rt1"
               },
               {
                 "id": "$rt2"
               }
            ],
            "metricType": [
              {
                "id": "$mt1",
                "type": "GAUGE",
                "unit": "MILLISECONDS",
                "collectionInterval": "1"
              }
            ]
          },
          "/t;$tenantId/rt;$rt1": {
            "dataEntity": [
              {
                "role": "configurationSchema",
                "value": {
                  "title": "URL config schema",
                  "description": "A json schema describing configuration of an URL",
                  "type": "string"
                }
              }
            ],
            "operationType": [
              {
                "id": "ping"
              }
            ]
          },
          "/t;$tenantId/rt;$rt2": {
            "dataEntity": [
              {
                "role": "connectionConfigurationSchema",
                "value": {
                  "title": "URL2 connection config schema",
                  "description": "A json schema describing connection to an URL",
                  "type": "string"
                }
              }
            ],
            "operationType": [
              {
                "id": "ping-pong"
              }
            ]
          },
          "/t;$tenantId/e;$env1": {
            "resource": [
              {
                "id": "url1",
                "resourceTypePath": "/t;$tenantId/rt;$rt1"
              }
            ],
            "metric": [
              {
                "id": "url1_responseTime",
                "metricTypePath": "/t;$tenantId/mt;$mt1"
              }
            ]
          },
          "/t;$tenantId/e;$env1/r;url1": {
            "dataEntity": [
              {
                "role": "configuration",
                "value": "http://redhat.com"
              }
            ],
            "relationship": [
              {
                "name": "incorporates",
                "otherEnd": "/t;$tenantId/e;$env1/m;url1_responseTime",
                "direction": "outgoing"
              }
            ]
          }
        }
        """

        def response = client.post(path: "$basePath/bulk", body: payload)
        assertEquals(201, response.status)

        def environmentCodes = response.data.environment as Map<String, Integer>
        def resourceTypeCodes = response.data.resourceType as Map<String, Integer>
        def metricTypeCodes = response.data.metricType as Map<String, Integer>
        def dataCodes = response.data.dataEntity as Map<String, Integer>
        def operationTypeCodes = response.data.operationType as Map<String, Integer>
        def resourceCodes = response.data.resource as Map<String, Integer>
        def metricCodes = response.data.metric as Map<String, Integer>
        def relationshipCodes = response.data.relationship as Map<String, Integer>

        //now make a second call, this time only create a metadata pack.
        //this has to be done in two requests, because the resource types need to be fully populated before they can
        //be put into the pack because afterwards they're frozen
        payload = """
          {
            "/t;$tenantId": {
              "metadataPack": [
                {
                  "members": ["/t;$tenantId/rt;$rt1", "/t;$tenantId/rt;$rt2", "/t;$tenantId/mt;$mt1"]
                }
              ]
            }
          }
        """
        response = client.post(path: "$basePath/bulk", body: payload)
        assertEquals(201, response.status)

        def metadataPackCodes = response.data.metadataPack as Map<String, Integer>

        assertEquals(2, environmentCodes.size())
        assertEquals(201, environmentCodes.get("/t;$tenantId/e;$env1".toString()))
        assertEquals(201, environmentCodes.get("/t;$tenantId/e;$env2".toString()))

        assertEquals(2, resourceTypeCodes.size())
        assertEquals(201, resourceTypeCodes.get("/t;$tenantId/rt;$rt1".toString()))
        assertEquals(201, resourceTypeCodes.get("/t;$tenantId/rt;$rt2".toString()))

        assertEquals(1, metricTypeCodes.size())
        assertEquals(201, metricTypeCodes.get("/t;$tenantId/mt;$mt1".toString()))

        assertEquals(3, dataCodes.size())
        assertEquals(201, dataCodes.get("/t;$tenantId/rt;$rt1/d;configurationSchema".toString()))
        assertEquals(201, dataCodes.get("/t;$tenantId/rt;$rt2/d;connectionConfigurationSchema".toString()))
        assertEquals(201, dataCodes.get("/t;$tenantId/e;$env1/r;url1/d;configuration".toString()))

        assertEquals(2, operationTypeCodes.size())
        assertEquals(201, operationTypeCodes.get("/t;$tenantId/rt;$rt1/ot;ping".toString()))
        assertEquals(201, operationTypeCodes.get("/t;$tenantId/rt;$rt2/ot;ping-pong".toString()))

        assertEquals(1, resourceCodes.size())
        assertEquals(201, resourceCodes.get("/t;$tenantId/e;$env1/r;url1".toString()))

        assertEquals(1, metricCodes.size())
        assertEquals(201, metricCodes.get("/t;$tenantId/e;$env1/m;url1_responseTime".toString()))

        assertEquals(1, relationshipCodes.size())
        assertEquals(201, relationshipCodes.entrySet().getAt(0).getValue())

        assertEquals(1, metadataPackCodes.size())
        assertEquals(201, metadataPackCodes.entrySet().getAt(0).value)

        response = client.get(path: "$basePath/$env1/resources/url1/metrics")
        assertEquals("/t;$tenantId/e;$env1/m;url1_responseTime".toString(), response.data.get(0).path)

        def mpPath = metadataPackCodes.entrySet().getAt(0).key
        def mpId = mpPath.substring(mpPath.lastIndexOf(";") + 1)
        client.delete(path: "$basePath/metadatapacks/" + mpId)
        client.delete(path: "$basePath/environments/$env1")
        client.delete(path: "$basePath/environments/$env2")
        client.delete(path: "$basePath/resourceTypes/$rt1")
        client.delete(path: "$basePath/metricTypes/$mt1")
    }

    @Test
    void testMetadataPacks() {
        def response = client.post(path: "$basePath/metadatapacks", body: """
            {
              "members": ["/t;$tenantId/rt;$urlTypeId"]
            }
        """)

        def mpId = response.data.id

        try {
            //try to delete url - should be impossible now
            client.delete(path: "$basePath/resourceTypes/$urlTypeId")
            fail("Deleting a resource type that is part of metadatapack should not be possible.")
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode)
        } finally {
            client.delete(path: "$basePath/metadatapacks/$mpId")
        }
    }

    private static void assertEntityExists(path, cp) {
        def response = client.get(path: "$basePath/$path")
        assertEquals(200, response.status)
        assertEquals(fullCanonicalPath(cp), response.data.path)
    }

    private static void assertEntityExists(path, queryParams, cp) {
        def response = client.get(path: "$basePath/$path", query: queryParams)
        assertEquals(200, response.status)
        assertEquals(fullCanonicalPath(cp), response.data.path)
    }

    private static void assertEntitiesExist(path, cps) {
        def response = client.get(path: "$basePath/$path")

        def fullCps = cps.collect { fullCanonicalPath(it) }

        //noinspection GroovyAssignabilityCheck
        def expectedPaths = new ArrayList<>(fullCps)
        println response.data
        def entityPaths = response.data.collect { it.path }
        fullCps.forEach { entityPaths.remove(it); expectedPaths.remove(it) }

        Assert.assertTrue("Unexpected entities with paths: " + entityPaths, entityPaths.empty)
        Assert.assertTrue("Following entities not found: " + expectedPaths, expectedPaths.empty)
    }

    private static void assertRelationshipJsonldExists(path, source, label, target) {
        def response = client.get(path: "$basePath/$path", query: [jsonld: true])
        def needle = new Tuple(source, label, target);
        def haystack = response.data.collect{ new Tuple(it["source"]["shortId"], it["name"],
                it["target"]["shortId"])  }
        assert haystack.any{it == needle} : "Following edge not found: " + needle
        haystack.clear()
    }

    private static void assertRelationshipExists(path, source, label, target, query = [:]) {
        def response = client.get(path: "$basePath/$path", query: query)
        def needle = new Tuple(source, label, target);
        def haystack = response.data.collect{ new Tuple(it["source"], it["name"],
                it["target"])  }
        assert haystack.any{it == needle} : "Following edge not found: " + needle
        haystack.clear()
    }

    /* Add the deletable path to {@link #pathsToDelete} and send a {@code POST} request using the given map of
     * arguments. */
    private static Object postDeletable(Map args) {
        String getVerificationPath = args.path + "/" + args.body.id
        postDeletable(args, getVerificationPath)
    }
    private static Object postDeletable(Map args, String getVerificationPath) {
        args.path = basePath + "/" + args.path
        String path = args.path + "/" + args.body.id
        pathsToDelete.put(path, basePath + "/" + getVerificationPath)
        println "posting $args"
        return client.post(args)
    }

    private static String fullCanonicalPath(cp) {
        return CanonicalPath.fromPartiallyUntypedString(cp, CanonicalPath.of().tenant(tenantId).get(), (Class<?>) null)
                .toString()
    }
}
