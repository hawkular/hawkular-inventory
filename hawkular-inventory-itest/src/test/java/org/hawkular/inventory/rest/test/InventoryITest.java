/*
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
package org.hawkular.inventory.rest.test;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.hawkular.accounts.api.model.HawkularUser;
import org.hawkular.accounts.api.model.Persona;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.PathSegmentCodec;
import org.hawkular.inventory.paths.SegmentType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.squareup.okhttp.Response;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class InventoryITest extends AbstractTestBase {
    protected static final String basePath = "/hawkular/inventory";

    protected static final String urlTypeId = "URL";
    protected static final String testEnvId = "test";
    protected static final String environmentId = "itest-env-" + UUID.randomUUID().toString();
    protected static final String pingableHostRTypeId = "itest-pingable-host-" + UUID.randomUUID().toString();
    protected static final String roomRTypeId = "itest-room-type-" + UUID.randomUUID().toString();
    protected static final String copyMachineRTypeId = "itest-copy-machine-type-" + UUID.randomUUID().toString();
    protected static final String date20150626 = "2015-06-26";
    protected static final String date20160801 = "2016-08-01";
    protected static final String expectedLifetime15years = "15y";
    protected static final String facilitiesDept = "Facilities";
    protected static final String itDept = "IT";
    protected static final String typeVersion = "1.0";
    protected static final String responseTimeMTypeId = "itest-response-time-" + UUID.randomUUID().toString();
    protected static final String responseStatusCodeMTypeId = "itest-response-status-code-" +
            UUID.randomUUID().toString();
    protected static final String statusDurationMTypeId = "status.duration.type";
    protected static final String statusCodeMTypeId = "status.code.type";
    protected static final String host1ResourceId = "itest-host1-" + UUID.randomUUID().toString();
    protected static final String host2ResourceId = "itest-host2-" + UUID.randomUUID().toString();
    protected static final String room1ResourceId = "itest-room1-" + UUID.randomUUID().toString();
    protected static final String copyMachine1ResourceId = "itest-copy-machine1-" + UUID.randomUUID().toString();
    protected static final String copyMachine2ResourceId = "itest-copy-machine2-" + UUID.randomUUID().toString();
    protected static final String responseTimeMetricId = "itest-response-time-" + host1ResourceId;
    protected static final String responseStatusCodeMetricId = "itest-response-status-code-" + host1ResourceId;
    protected static final String feedId = "itest-feed-" + UUID.randomUUID().toString();
    protected static final String bulkResourcePrefix = "bulk-resource-" + UUID.randomUUID().toString();
    protected static final String bulkResourceTypePrefix = "bulk-resource-type-" + UUID.randomUUID().toString();
    protected static final String bulkMetricTypePrefix = "bulk-metric-type-" + UUID.randomUUID().toString();
    protected static final String customRelationName = "inTheSameRoom";

    /* key is the path to delete while value is the path to GET to verify the deletion */
    protected static Map<String, String> pathsToDelete = new LinkedHashMap<>();

    protected static String tenantId;

    @BeforeClass
    public static void setupData() throws Throwable {
        Persona persona = getWithRetries("/hawkular/accounts/personas/current", HawkularUser.class, 10, 2000);
        tenantId = persona.getIdAsUUID().toString();
        Assert.assertTrue("Cannot get the current tenant id.", tenantId != null && !tenantId.trim().isEmpty());

        /* assert the test environment exists */
        /* There is a race condition when WildFly agent is enabled:
           both this test and Agent trigger the autocreation of test entities simultaneously,
           and one of them may get only a partially initialized state.
           That is why we do several delayed attempts do perform the first request.
         */
        String path = "/hawkular/inventory/environments/" + testEnvId;
        Environment env = getWithRetries(path, Environment.class, 10, 2000);
        assertEquals("Unable to get the '" + testEnvId + "' environment.", testEnvId, env.getId());

        /* Create an environment that will be used exclusively by this test */
        Response response = postDeletable("environments",
                Environment.Blueprint.builder().withId(environmentId).build());
        assertEquals(201, response.code());
        Environment environment = mapper.readValue(response.body().string(), Environment.class);

        assertEquals(environmentId, environment.getId());
        assertEquals(CanonicalPath.of().tenant(tenantId).environment(environmentId).get(), environment.getPath());
        assertEquals(baseURI + basePath + "/environments/" + environmentId, response.headers().get("Location"));

        /* URL resource type should have been autocreated */
        path = basePath + "/resourceTypes/" + urlTypeId;
        ResourceType resourceType = getWithRetries(path, ResourceType.class, 10, 2000);
        assertEquals("Unable to get the '" + urlTypeId + "' resource type.", urlTypeId, resourceType.getId());
        assertEquals(urlTypeId, resourceType.getId());

        /* Create pingable host resource type */
        response = postDeletable("resourceTypes", ResourceType.Blueprint.builder().withId(pingableHostRTypeId).build());
        assertEquals(201, response.code());

        ResourceType pingableHost = mapper.readValue(response.body().string(), ResourceType.class);

        assertEquals(pingableHostRTypeId, pingableHost.getId());
        assertEquals(baseURI + basePath +"/resourceTypes/" + pingableHostRTypeId,
                response.headers().get("Location"));

        /* Create room resource type */
        response = postDeletable("resourceTypes", ResourceType.Blueprint.builder().withId(roomRTypeId)
                .withProperty("expectedLifetime", expectedLifetime15years)//
                .withProperty("ownedByDepartment", facilitiesDept).build());

        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/resourceTypes/" + roomRTypeId, response.headers().get("Location"));

        /* Create copy machine resource type */
        response = postDeletable("resourceTypes", ResourceType.Blueprint.builder().withId(copyMachineRTypeId)
                .withProperty("expectedLifetime", expectedLifetime15years)//
                .withProperty("ownedByDepartment", itDept).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/resourceTypes/" + copyMachineRTypeId,
                response.headers().get("Location"));


        /* Create a metric type */
        response = postDeletable("metricTypes", MetricType.Blueprint.builder(MetricDataType.COUNTER)
                .withId(responseTimeMTypeId)//
                .withUnit(MetricUnit.MILLISECONDS)//
                .withInterval(1L)//
                .build());

        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/metricTypes/" + responseTimeMTypeId,
                response.headers().get("Location"));

        /* Create another metric type */
        response = postDeletable("metricTypes", MetricType.Blueprint.builder(MetricDataType.GAUGE)
                .withId(responseStatusCodeMTypeId)//
                .withUnit(MetricUnit.NONE)//
                .withInterval(1L)//
                .build());

        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/metricTypes/" + responseStatusCodeMTypeId,
                response.headers().get("Location"));

        /* link pingableHostRTypeId with responseTimeMTypeId and responseStatusCodeMTypeId */
        path = basePath + "/resourceTypes/" + pingableHostRTypeId +"/metricTypes";
        //just testing that both relative and canonical paths work when referencing the types
        response = post(path, "[\"../" + responseTimeMTypeId +"\", \"/" + responseStatusCodeMTypeId +"\"]");

        assertEquals(204, response.code());
        //we will try deleting the associations between resource types and metric types, too
        //this is not necessary because deleting either the resource type or the metric type will take care of it anyway
        //but this is to test that explicit deletes work, too
        // XXX this should check for removal of a single association.
        // OkHttp unconditionally canonicalizes the URL paths, which makes the below constructs impossible to send
        // over the wire using OkHttp (even though they're perfectly valid URLs).
        //pathsToDelete.put(path + "/../" + responseTimeMTypeId, path +"/../" + responseTimeMTypeId);
        // XXX again, this is impossible due to OkHttp unconditionally canonicalizing the URL paths
        //pathsToDelete.put(path + "/../" + responseStatusCodeMTypeId, path +"/../" + responseStatusCodeMTypeId);

        /* add a metric */
        response = postDeletable(environmentId +"/metrics", Metric.Blueprint.builder()
                .withId(responseTimeMetricId) //
                .withMetricTypePath("../" + responseTimeMTypeId) //
                .build());
        //path relative to env
        assertEquals(201, response.code());
        Metric responseTimeMetric = mapper.readValue(response.body().string(), Metric.class);
        assertEquals(responseTimeMetricId, responseTimeMetric.getId());
        assertEquals(baseURI + basePath +"/" + environmentId + "/metrics/" + responseTimeMetricId,
                response.headers().get("Location"));

        /* add another metric */
        response = postDeletable(environmentId +"/metrics", Metric.Blueprint.builder()
                .withId(responseStatusCodeMetricId) //
                .withMetricTypePath("/" + responseStatusCodeMTypeId) //
                .build());
        assertEquals(201, response.code());
        Metric responseStatusCode = mapper.readValue(response.body().string(), Metric.class);
        assertEquals(responseStatusCodeMetricId, responseStatusCode.getId());
        assertEquals(baseURI + basePath +"/" + environmentId + "/metrics/" + responseStatusCodeMetricId,
                response.headers().get("Location"));

        /* add a resource */
        response = postDeletable(environmentId + "/resources",
                Resource.Blueprint.builder() //
                    .withId(host1ResourceId) //
                    .withResourceTypePath("../" + pingableHostRTypeId) //
                    .build());
        assertEquals(201, response.code());
        Resource host1Resource = mapper.readValue(response.body().string(), Resource.class);
        assertEquals(host1ResourceId, host1Resource.getId());
        assertEquals(CanonicalPath.of().tenant(tenantId).environment(environmentId).
            resource(host1ResourceId).get(), host1Resource.getPath());
        assertEquals(CanonicalPath.of().tenant(tenantId).resourceType(pingableHostRTypeId).get(),
                host1Resource.getType().getPath());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + host1ResourceId,
                response.headers().get("Location"));

        /* add another resource */
        response = postDeletable(environmentId + "/resources",
                Resource.Blueprint.builder()//
                .withId(host2ResourceId)//
                .withResourceTypePath("../" + pingableHostRTypeId)//
                .build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + host2ResourceId,
                response.headers().get("Location"));

        /* add a room resource */
        response = postDeletable(environmentId + "/resources",
                Resource.Blueprint.builder().withId(room1ResourceId).withResourceTypePath("../" + roomRTypeId)
                .withProperty("purchaseDate", date20150626).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + room1ResourceId,
                response.headers().get("Location"));

        /* add a copy machine resource */
        response = postDeletable(environmentId + "/resources",
                Resource.Blueprint.builder() //
                .withId(copyMachine1ResourceId) //
                .withResourceTypePath("../" + copyMachineRTypeId)//
                .withProperty("purchaseDate", date20150626)//
                .withProperty("nextMaintenanceDate", date20160801)//
                .build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + copyMachine1ResourceId,
                response.headers().get("Location"));

        response = postDeletable(environmentId + "/resources",
                Resource.Blueprint.builder() //
                .withId(copyMachine2ResourceId) //
                .withResourceTypePath("../" + copyMachineRTypeId) //
                .withProperty("purchaseDate", date20160801) //
                .build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + copyMachine2ResourceId,
                response.headers().get("Location"));

        /* add child resources */
        response = postDeletable(environmentId + "/resources/" + room1ResourceId,
                Resource.Blueprint.builder().withId("table").withResourceTypePath("/" + roomRTypeId).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + room1ResourceId +"/table",
                response.headers().get("Location"));

        response = postDeletable(environmentId + "/resources/" + room1ResourceId +"/table",
                Resource.Blueprint.builder().withId("leg/1").withResourceTypePath("/" + roomRTypeId).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + room1ResourceId +"/table/leg%2F1",
                response.headers().get("Location"));

        response = postDeletable(environmentId + "/resources/" + room1ResourceId +"/table",
                Resource.Blueprint.builder().withId("leg 2").withResourceTypePath("/" + roomRTypeId).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + room1ResourceId +"/table/leg%202",
                response.headers().get("Location"));

        response = postDeletable(environmentId + "/resources/" + room1ResourceId +"/table",
                Resource.Blueprint.builder().withId("leg;3").withResourceTypePath("/" + roomRTypeId).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + room1ResourceId +"/table/leg;3",
                response.headers().get("Location"));
        response = postDeletable(environmentId + "/resources/" + room1ResourceId +"/table",
                Resource.Blueprint.builder().withId("leg-4").withResourceTypePath("/" + roomRTypeId).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/" + room1ResourceId +"/table/leg-4",
                response.headers().get("Location"));

        //alternative child hierarchies
        response = postDeletable(environmentId + "/resources",
                Resource.Blueprint.builder().withId("weapons").withResourceTypePath("/" + roomRTypeId).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId + "/resources/weapons",
                response.headers().get("Location"));

        path = basePath + "/" + environmentId + "/resources/weapons/children";
        response = post(path, JsonNodeFactory.instance.arrayNode() //
                .add("/e;" + environmentId + "/r;" + room1ResourceId + "/r;table/r;leg%2F1") //
                .add("../" + room1ResourceId + "/table/leg-4") //
                .toString());
        assertEquals(204, response.code());
        // XXX again, this is impossible due to OkHttp unconditionally canonicalizing the URL paths
//        pathsToDelete.put(path + "/../table/leg%2F1", path + "/../table/leg%2F1")
//        pathsToDelete.put(path + "/../table/leg-4", path + "/../table/leg-4")

        /* link the metric to resource */
        path = basePath + "/" + environmentId + "/resources/" + host1ResourceId +"/metrics";
        response = post(path,
                JsonNodeFactory.instance.arrayNode() //
                .add("/e;"+ environmentId + "/m;"+ responseTimeMetricId)
                .add("/e;"+ environmentId + "/m;"+ responseStatusCodeMetricId)
                .toString());
        assertEquals(204, response.code());
        // XXX again, this is impossible due to OkHttp unconditionally canonicalizing the URL paths
        // pathsToDelete.put(path + "/../" + responseTimeMetricId, path + "/../" + responseTimeMetricId);
        // XXX again, this is impossible due to OkHttp unconditionally canonicalizing the URL paths
        //pathsToDelete.put(path + "/../" + responseStatusCodeMetricId, path + "/../" + responseStatusCodeMetricId);

        /* add a feed */
        response = postDeletable("feeds", Feed.Blueprint.builder().withId(feedId).build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/feeds/" + feedId, response.headers().get("Location"));

        /* add a custom relationship, no need to clean up, it'll be deleted together with the resources */
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("from", "2000-01-01");
        properties.put("confidence", "90%");
        CanonicalPath src = CanonicalPath.fromString("/t;" + tenantId + "/e;" + environmentId + "/r;"
                + host2ResourceId);
        CanonicalPath target = CanonicalPath.fromString("/t;" + tenantId + "/e;" + environmentId + "/r;"
                + host1ResourceId);
        Relationship h1h2Rel = new Relationship("42", customRelationName, src, target, properties);
        response = postNew(basePath + "/" + environmentId +"/resources/" + host2ResourceId +"/relationships", h1h2Rel);
        assertEquals(201, response.code());
        JsonNode h1h2Json = mapper.readTree(response.body().string());
        assertEquals(customRelationName, h1h2Json.get("name").asText());

        // relationship with tenant
        CanonicalPath tenantPath = CanonicalPath.fromString("/t;" + tenantId);
        Relationship tenantRel = new Relationship(UUID.randomUUID().toString(), "sampleRelationship", tenantPath,
                tenantPath);
        post(basePath + "/tenants/relationships", mapper.writeValueAsString(tenantRel));
        assertEquals(201, response.code());

        // add operation type to the resource type
        response = postDeletable("resourceTypes/" + pingableHostRTypeId +"/operationTypes",
                OperationType.Blueprint.builder().withId("start").build());
        assertEquals(201, response.code());

        response = postDeletable("resourceTypes/" + pingableHostRTypeId +"/operationTypes",
                OperationType.Blueprint.builder().withId("stop").build());
        assertEquals(201, response.code());

        // add some parameters to it
        String startOpParamTypes = "{" //
                + "\"role\" : \"parameterTypes\"," //
                + "\"value\": {" //
                    + "\"title\" : \"blah\"," //
                    + "\"type\": \"object\"," //
                    + "\"properties\": { \"quick\": { \"type\": \"boolean\"}}" //
                + "}" //
            + "}";
        response = post(basePath + "/resourceTypes/" + pingableHostRTypeId +"/operationTypes/start/data",
                startOpParamTypes);
        assertEquals(201, response.code());

        response = post(basePath + "/resourceTypes/" + pingableHostRTypeId +"/operationTypes/start/data",
                "{\"role\": \"returnType\", \"value\": {\"title\": \"blah\", \"type\": \"boolean\"}}");
        assertEquals(201, response.code());

        /* add a resource type json schema */
        String schema = "{"
            + "\"value\": {" //
                + "\"title\"     : \"Character\"," //
                + "\"type\"      : \"object\"," //
                + "\"properties\": {" //
                    + "\"firstName\" : {\"type\": \"string\"}," //
                    + "\"secondName\": {\"type\": \"string\"}," //
                    + "\"age\"       : {" //
                        + "\"type\"            : \"integer\"," //
                        + "\"minimum\"         : 0," //
                        + "\"exclusiveMinimum\": false" //
                    + "}," //
                    + "\"male\"      : {" //
                        + "\"description\": \"true if the character is a male\"," //
                        + "\"type\"       : \"boolean\"" //
                    + "}," //
                    + "\"foo\"       : {" //
                        + "\"type\"      : \"object\"," //
                        + "\"properties\": {" //
                            + "\"something\": {\"type\": \"string\"}," //
                            + "\"someArray\": {" //
                                + "\"type\"       : \"array\"," //
                                + "\"minItems\"   : 3," //
                                + "\"items\"      : {\"type\": \"integer\"}," //
                                + "\"uniqueItems\": false" //
                            + "}," //
                            + "\"foo\"      : {" //
                                + "\"type\"      : \"object\"," //
                                + "\"properties\": {" //
                                    + "\"question\": {" //
                                        + "\"type\"   : \"string\"," //
                                        + "\"pattern\": \"^.*\\\\?$\"" //
                                    + "}," //
                                    + "\"answer\"  : {" //
                                        + "\"description\": \"the answer (example of any type)\"" //
                                    + "}," //
                                    + "\"foo\"     : {" //
                                        + "\"type\"      : \"object\"," //
                                        + "\"properties\": {" //
                                            + "\"foo\": {" //
                                                + "\"type\"      : \"object\"," //
                                                + "\"properties\": {" //
                                                    + "\"fear\" : {" //
                                                        + "\"type\": \"string\"," //
                                                        + "\"enum\": [\"dentists\", \"lawyers\", \"rats\"]" //
                                                    + "}" //
                                                + "}" //
                                            + "}" //
                                        + "}" //
                                    + "}" //
                                + "}" //
                            + "}" //
                        + "}" //
                    + "}" //
                + "}," //
                + "\"required\"  : [\"firstName\", \"secondName\", \"male\", \"age\", \"foo\"]" //
            + "}," //
            + "\"role\" : \"configurationSchema\"" //
        + "}";
        response = post(basePath + "/resourceTypes/" + pingableHostRTypeId +"/data", schema);
        assertEquals(201, response.code());

        /* add an invalid config data to a resource (invalid ~ not valid against the json schema) */
        String invalidData = "{" //
                + "\"value\": {" //
                    + "\"firstName\": \"John\"," //
                    + "\"secondName\": \"Smith\"" //
                + "}," //
                + "\"role\" : \"configuration\"" //
        + "}";

        response = post(basePath + "/" + environmentId +"/resources/" + host2ResourceId +"/data", invalidData);
        assertEquals(400, response.code());

        /* add a config data to a resource, no need to clean up, it'll be deleted together with the resources */
        String data = "{" //
            + "\"value\"     : {" //
                + "\"firstName\" : \"Winston\"," //
                + "\"secondName\": \"Smith\"," //
                + "\"sdf\"       : \"sdf\"," //
                + "\"male\"      : true," //
                + "\"age\"       : 42," //
                + "\"foo\"       : {" //
                    + "\"something\": \"whatever\"," //
                    + "\"someArray\": [1, 1, 2, 3, 5, 8]," //
                    + "\"foo\"      : {" //
                        + "\"answer\"  : 5," //
                        + "\"question\": \"2+2=?\"," //
                        + "\"foo\"     : {" //
                            + "\"foo\": {" //
                                + "\"fear\": \"rats\"" //
                            + "}" //
                        + "}" //
                    + "}" //
                + "}" //
            + "}," //
            + "\"role\"      : \"configuration\"," //
            + "\"properties\": {" //
                + "\"war\"      : \"peace\"," //
                + "\"freedom\"  : \"slavery\"," //
                + "\"ignorance\": \"strength\"" //
            + "}" //
        + "}";
        response = post(basePath + "/" + environmentId +"/resources/" + host2ResourceId +"/data", data);
        assertEquals(201, response.code());

        //add resource-owner metric
        response = postDeletable(environmentId +"/resources/" + host2ResourceId +"/metrics",
                Metric.Blueprint.builder() //
                .withId("resource-owned-metric") //
                .withMetricTypePath("/"+responseTimeMTypeId) //
                .build());
        assertEquals(201, response.code());
        assertEquals(baseURI + basePath +"/" + environmentId +"/resources/" + host2ResourceId
                +"/metrics/resource-owned-metric",
                response.headers().get("Location"));
    }


    @AfterClass
    public static void deleteEverything() throws IOException {
        /* the following would delete all data of the present user. We cannot do that as long as we do not have
         * a dedicated user for running this very single test class. */
        // Response response = client.delete(path : basePath + "/tenant")
        // assertEquals(204, response.code())

        /* Let's delete the entities one after another in the reverse order as we created them */
        List<Map.Entry<String, String>> entries = new ArrayList<Map.Entry<String, String>>(pathsToDelete.entrySet());
        Collections.reverse(entries);
        for (Map.Entry<String, String> en : entries) {
            String path = en.getKey();
            String getValidationPath = en.getValue();
            Response response = client.newCall(newAuthRequest().url(baseURI + path).delete().build()).execute();
            assertEquals(
                    "Could not delete path [" + baseURI + path + "]: " + response.body().string(), 204,
                    response.code());
            if (getValidationPath != null) {
                response = client.newCall(newAuthRequest().url(baseURI + path).build()).execute();
                assertEquals("The path " + getValidationPath
                        + " should not exist after the entity was deleted: " + response.body().string(), 404,
                        response.code());
            }
        }
    }


    @Test
    public void ping() throws Throwable {
        Response response = get(basePath + "");
        assertEquals(200, response.code());
    }

    @Test
    public void testEnvironmentsCreated() throws Throwable {
        assertEntitiesExist("environments", "/e;"+ testEnvId, "/e;"+ environmentId);
    }

    @Test
    public void testResourceTypesCreated() throws Throwable {
        assertEntityExists("resourceTypes/" + urlTypeId, "/rt;" + urlTypeId);
        assertEntityExists("resourceTypes/" + pingableHostRTypeId, "/rt;" + pingableHostRTypeId);
        assertEntityExists("resourceTypes/" + roomRTypeId, "/rt;" + roomRTypeId);

        // commented out as it interfers with WildFly Agent
        // assertEntitiesExist("resourceTypes", [urlTypeId, pingableHostRTypeId, roomRTypeId])

    }


    @Test
    public void testMetricTypesCreated() throws Throwable {
        assertEntityExists("metricTypes/" + responseTimeMTypeId, "/mt;" + responseTimeMTypeId);
        assertEntityExists("metricTypes/" + statusDurationMTypeId, "/mt;" + statusDurationMTypeId);
        assertEntityExists("metricTypes/" + statusCodeMTypeId, "/mt;" + statusCodeMTypeId);
        // commented out as it interfers with WildFly Agent
        // assertEntitiesExist("metricTypes",
        //    [responseTimeMTypeId, responseStatusCodeMTypeId, statusDurationMTypeId, statusCodeMTypeId])
    }

    @Test
    public void testOperationTypesCreated() throws Throwable {
        Response response = get(basePath + "/resourceTypes/" + pingableHostRTypeId +"/operationTypes");
        JsonNode json = mapper.readTree(response.body().string());
        assertEquals(2, json.size());

        assertEntityExists("resourceTypes/" + pingableHostRTypeId +"/operationTypes/start",
                "/rt;" + pingableHostRTypeId + "/ot;start");
        assertEntityExists("resourceTypes/" + pingableHostRTypeId +"/operationTypes/start/data",
                new String[] {"dataType", "returnType"}, "/rt;" + pingableHostRTypeId + "/ot;start/d;returnType");
        assertEntityExists("resourceTypes/" + pingableHostRTypeId + "/operationTypes/start/data",
                new String[] { "dataType", "parameterTypes" },
                "/rt;" + pingableHostRTypeId + "/ot;start/d;parameterTypes");
    }

    @Test
    public void testMetricTypesLinked() throws Throwable {
        assertEntitiesExist("resourceTypes/" + pingableHostRTypeId +"/metricTypes", "/mt;" + responseTimeMTypeId,
                 "/mt;" + responseStatusCodeMTypeId);
    }

    @Test
    public void testResourcesCreated() throws Throwable {
        assertEntityExists(environmentId + "/resources/" + host1ResourceId, "/e;" + environmentId + "/r;"
                + host1ResourceId);
        assertEntityExists(environmentId + "/resources/" + host2ResourceId, "/e;" + environmentId + "/r;"
                + host2ResourceId);
        assertEntityExists(environmentId + "/resources/" + room1ResourceId, "/e;" + environmentId + "/r;"
                + room1ResourceId);
    }





    @Test
    public void testResourcesFilters() throws Throwable {

        /* filter by resource properties */
        Response response = get(basePath + "/" + environmentId +"/resources",
                "properties", "purchaseDate:" + date20150626, "sort", "id");
        JsonNode json = mapper.readTree(response.body().string());
        assertEquals(2, json.size());
        assertEquals(copyMachine1ResourceId, json.get(0).get("id").asText());
        assertEquals(room1ResourceId, json.get(1).get("id").asText());

        response = get(basePath + "/" + environmentId +"/resources",
             "properties", "nextMaintenanceDate:"+ date20160801);
        json = mapper.readTree(response.body().string());
        assertEquals(1, json.size());
        assertEquals(copyMachine1ResourceId, json.get(0).get("id").asText());

        /* query by two props at once */
        response = get(basePath + "/" + environmentId +"/resources",
            "properties", "nextMaintenanceDate:" +date20160801 +",purchaseDate:" +date20150626);
        json = mapper.readTree(response.body().string());
        assertEquals(1, json.size());
        assertEquals(copyMachine1ResourceId, json.get(0).get("id").asText());

        /* query by property existence */
        response = get(basePath + "/" + environmentId +"/resources",
            "properties", "purchaseDate", "sort", "id");
        json = mapper.readTree(response.body().string());
        assertEquals(3, json.size());
        assertEquals(copyMachine1ResourceId, json.get(0).get("id").asText());
        assertEquals(copyMachine2ResourceId, json.get(1).get("id").asText());
        assertEquals(room1ResourceId, json.get(2).get("id").asText());

        /* filter by type */
        response = get(basePath + "/" + environmentId +"/resources",
            "type.id", pingableHostRTypeId);
        json = mapper.readTree(response.body().string());
        assertEquals(2, json.size());

        response = get(basePath + "/" + environmentId +"/resources",
            "type.id", roomRTypeId, "type.version", typeVersion);
        json = mapper.readTree(response.body().string());
        assertEquals(2, json.size());

    }

    @Test
    public void testMetricsCreated() throws Throwable {
        assertEntityExists(environmentId +"/metrics/" + responseTimeMetricId,
                "/e;"+ environmentId + "/m;"+ responseTimeMetricId);
        assertEntityExists(environmentId +"/metrics/" + responseStatusCodeMetricId,
                "/e;"+ environmentId + "/m;"+ responseStatusCodeMetricId);
        assertEntitiesExist(environmentId +"/metrics",
                "/e;"+ environmentId + "/m;"+ responseTimeMetricId,
                "/e;"+ environmentId + "/m;"+ responseStatusCodeMetricId,
                "/e;"+ environmentId + "/r;"+ host2ResourceId + "/m;resource-owned-metric");
    }

    @Test
    public void testMetricsLinked() throws Throwable {
        assertEntitiesExist(environmentId +"/resources/" + host1ResourceId +"/metrics",
                "/e;" + environmentId + "/m;" + responseTimeMetricId, "/e;" + environmentId + "/m;" +
               responseStatusCodeMetricId);
    }

    @Test
    public void testConfigCreated() throws Throwable {
        assertEntityExists(environmentId +"/resources/" + host2ResourceId +"/data",
                "/e;" + environmentId + "/r;" + host2ResourceId + "/d;configuration");
//        assertEntitiesExist(environmentId +"/resources/"
//        + host2ResourceId%2Ftable/data?dataType=connectionConfiguration",
//                ["/e;" + environmentId + "/r;" + host2ResourceId + "/d;connectionConfiguration"])
    }

    @Test
    public void testPaging() throws Throwable {
        String path = basePath + "/" + environmentId +"/resources";
        Response response = get(path, "type.id", pingableHostRTypeId, "page", "0", "per_page", "2", "sort", "id");
        JsonNode json = mapper.readTree(response.body().string());
        assertEquals(2, json.size());

        JsonNode first = json.get(0);
        JsonNode second = json.get(1);

        response = get(path, "type.id", pingableHostRTypeId, "page", "0", "per_page", "1", "sort", "id");
        json = mapper.readTree(response.body().string());
        assertEquals(1, json.size());
        assertEquals(first, json.get(0));


        response = get(path, "type.id", pingableHostRTypeId, "page", "1", "per_page", "1", "sort", "id");
        json = mapper.readTree(response.body().string());
        assertEquals(1, json.size());
        assertEquals(second, json.get(0));


        response = get(path, "type.id", pingableHostRTypeId, "page", "0", "per_page", "1", "sort", "id",
                                                                   "order", "desc");
        json = mapper.readTree(response.body().string());
        assertEquals(1, json.size());
        assertEquals(second, json.get(0));


        response = get(path, "type.id", pingableHostRTypeId, "page", "1", "per_page", "1", "sort", "id",
                                                                   "order", "desc");
        json = mapper.readTree(response.body().string());
        assertEquals(1, json.size());
        assertEquals(first, json.get(0));
    }

    @Test
    public void testTenantsContainEnvironments() throws Throwable {
        assertRelationshipExists("tenant/relationships",
                "/t;"+ tenantId + "",
                contains.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "");

        assertRelationshipJsonldExists("tenant/relationships",
                tenantId,
                contains.name(),
                environmentId);
    }



    @Test
    public void testTenantsContainResourceTypes() throws Throwable {
        assertRelationshipExists("resourceTypes/" + urlTypeId +"/relationships",
                "/t;"+ tenantId + "",
                contains.name(),
                "/t;"+ tenantId + "/rt;"+ urlTypeId + "");

        assertRelationshipExists("tenant/relationships",
                "/t;"+ tenantId + "",
                contains.name(),
                "/t;"+ tenantId + "/rt;" +pingableHostRTypeId);
    }

    @Test
    public void testTenantsContainMetricTypes() throws Throwable {
        assertRelationshipExists("metricTypes/" + responseTimeMTypeId +"/relationships",
                "/t;"+ tenantId + "",
                contains.name(),
                "/t;"+ tenantId + "/mt;" + responseTimeMTypeId);

        assertRelationshipExists("tenant/relationships",
                "/t;"+ tenantId + "",
                contains.name(),
                "/t;"+ tenantId + "/mt;" + statusCodeMTypeId);
    }


    @Test
    public void testEnvironmentsContainResources() throws Throwable {
        assertRelationshipExists("environments/" + environmentId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "",
                contains.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host2ResourceId + "");

        assertRelationshipExists("environments/" + environmentId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "",
                contains.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host1ResourceId + "");

        assertRelationshipJsonldExists("environments/" + environmentId +"/relationships",
                environmentId,
                contains.name(),
                host1ResourceId);

        assertRelationshipJsonldExists("environments/" + environmentId +"/relationships",
                environmentId,
                contains.name(),
                host2ResourceId);
    }



    @Test
    public void testTenantsContainFeeds() throws Throwable {
        assertRelationshipExists("feeds/" + feedId +"/relationships",
                "/t;"+ tenantId + "",
                contains.name(),
                "/t;"+ tenantId + "/f;"+ feedId + "");

        assertRelationshipJsonldExists("feeds/" + feedId +"/relationships",
                tenantId,
                contains.name(),
                feedId);
    }

    @Test
    public void testEnvironmentsContainMetrics() throws Throwable {
        assertRelationshipExists("environments/" + environmentId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "",
                contains.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "/m;"+ responseTimeMetricId + "");

        assertRelationshipExists("environments/" + environmentId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "",
                contains.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "/m;"+ responseStatusCodeMetricId + "");

        assertRelationshipJsonldExists("environments/" + environmentId +"/relationships",
                environmentId,
                contains.name(),
                responseTimeMetricId);

        assertRelationshipJsonldExists("environments/" + environmentId +"/relationships",
                environmentId,
                contains.name(),
                responseStatusCodeMetricId);
    }

    @Test
    public void testResourceTypesIncorporatesMetricTypes() throws Throwable {
        assertRelationshipExists("resourceTypes/" + pingableHostRTypeId +"/relationships",
                "/t;"+ tenantId + "/rt;" + pingableHostRTypeId,
                incorporates.name(),
                "/t;"+ tenantId + "/mt;" + responseTimeMTypeId);

        assertRelationshipExists("metricTypes/" + responseStatusCodeMTypeId +"/relationships",
                "/t;"+ tenantId + "/rt;" + pingableHostRTypeId,
                incorporates.name(),
                "/t;"+ tenantId + "/mt;" + responseStatusCodeMTypeId);

        assertRelationshipJsonldExists("resourceTypes/" + pingableHostRTypeId +"/relationships",
                pingableHostRTypeId,
                incorporates.name(),
                responseTimeMTypeId);
    }



    @Test
    public void testResourcesIncorporatesMetrics() throws Throwable {
        assertRelationshipExists(environmentId +"/resources/" + host1ResourceId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host1ResourceId + "",
                incorporates.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "/m;"+ responseStatusCodeMetricId + "");

        assertRelationshipExists(environmentId +"/resources/" + host1ResourceId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host1ResourceId + "",
                incorporates.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "/m;"+ responseTimeMetricId + "");

        assertRelationshipJsonldExists(environmentId +"/resources/" + host1ResourceId +"/relationships",
                host1ResourceId,
                incorporates.name(),
                responseTimeMetricId);
    }

    @Test
    public void testResourceTypesDefinesResources() throws Throwable {
        assertRelationshipExists("resourceTypes/" + pingableHostRTypeId +"/relationships",
                "/t;"+ tenantId + "/rt;" + pingableHostRTypeId,
                defines.name(),
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host2ResourceId + "");
    }

    @Test
    public void testMetricTypesDefinesMetrics() throws Throwable {
        assertRelationshipJsonldExists("metricTypes/" + responseStatusCodeMTypeId +"/relationships",
                responseStatusCodeMTypeId,
                defines.name(),
                responseStatusCodeMetricId);

        assertRelationshipJsonldExists("metricTypes/" + responseTimeMTypeId +"/relationships",
                responseTimeMTypeId,
                defines.name(),
                responseTimeMetricId);
    }


    @Test
    public void testCustomRelationship() throws Throwable {
        assertRelationshipJsonldExists(environmentId +"/resources/" + host2ResourceId +"/relationships",
                host2ResourceId,
                customRelationName,
                host1ResourceId);
    }

    @Test
    public void testRelationshipFiltering() throws Throwable {
        assertRelationshipExists(environmentId +"/resources/" + host2ResourceId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host2ResourceId + "",
                customRelationName,
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host1ResourceId + "",
                "property", "from", "propertyValue", "2000-01-01");

        assertRelationshipExists(environmentId +"/resources/" + host2ResourceId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host2ResourceId + "",
                customRelationName,
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host1ResourceId + "",
                "property", "confidence", "propertyValue", "90%");

        assertRelationshipExists(environmentId +"/resources/" + host2ResourceId +"/relationships",
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host2ResourceId + "",
                customRelationName,
                "/t;"+ tenantId + "/e;"+ environmentId + "/r;"+ host1ResourceId + "",
                "named", customRelationName);
    }

    @Test
    public void testResourceHierarchyQuerying() throws Throwable {
        assertEntitiesExist(environmentId +"/resources/" + room1ResourceId +"/children",
                "/e;"+ environmentId + "/r;"+ room1ResourceId + "/r;table");

        String base = "/e;"+ environmentId + "/r;"+ room1ResourceId + "/r;table";
        assertEntitiesExist(environmentId +"/resources/" + room1ResourceId +"/table/children",
                base + "/r;leg%2F1", base + "/r;leg%202", base + "/r;leg;3", base + "/r;leg-4");

        assertEntitiesExist(environmentId +"/resources/weapons/children",
                "/e;"+ environmentId + "/r;"+ room1ResourceId + "/r;table/r;leg%2F1",
                 "/e;"+ environmentId + "/r;"+ room1ResourceId + "/r;table/r;leg-4");
    }

    @Test @Ignore
    public void testResourceBulkCreate() throws Throwable {
        StringBuilder payload = new StringBuilder("{\"/e;test\": {\"resource\": [");
        for (int i = 0; i < 100; i++) {
            payload.append("{ \"id\": \""
                    + bulkResourcePrefix + "-" + i + "\", \"resourceTypePath\": \"/rt;" + roomRTypeId +
                    "\"}");
            if (i != 0) {
                payload.append(",");
            }
        }
        payload.append("]}}");
        Response response = post(basePath + "/bulk", payload.toString());

        assertEquals(201, response.code());
        JsonNode json = mapper.readTree(response.body().string());
        assertEquals(100, json.size());

        for (Iterator<Entry<String, JsonNode>> it = json.fields(); it.hasNext(); ) {
            Entry<String, JsonNode> en = it.next();
            CanonicalPath p = CanonicalPath.fromString(en.getKey());
            String env = p.ids().getEnvironmentId();
            String rid = p.ids().getResourcePath().getSegment().getElementId();
            delete(basePath + "/" + env +"/resources/" + rid);
        }
    }

    @Test
    public void testResourceBulkCreateUnderFeedWithDuplicates() throws Throwable {
        String pathToResType = "/t;"+ tenantId + "/f;"+ feedId + "/rt;"+ bulkResourceTypePrefix + ".1";
        String payload =
        "{"
        + "    \"/t;"+ tenantId + "/f;"+ feedId + "\": {" //
        + "        \"resourceType\": [" //
        + "            {" //
        + "                \"id\": \""+ bulkResourceTypePrefix +".1\"" //
        + "            }," //
        + "            {" //
        + "                \"id\": \""+ bulkResourceTypePrefix +".1\"" //
        + "            }" //
        + "        ]," //
        + "        \"resource\": [" //
        + "            {" //
        + "                \"id\"              : \""+ bulkResourcePrefix + ".1\"," //
        + "                \"resourceTypePath\": \""+ pathToResType + "\"" //
        + "            }," //
        + "            {" //
        + "                \"id\"              : \""+ bulkResourcePrefix + ".2\"," //
        + "                \"resourceTypePath\": \""+ pathToResType + "\"" //
        + "            }" //
        + "        ]," //
        + "        \"metricType\": [" //
        + "            {" //
        + "                \"id\"                : \""+ bulkMetricTypePrefix + ".1\"," //
        + "                \"unit\"              : \"BYTES\"," //
        + "                \"type\"              : \"GAUGE\"," //
        + "                \"collectionInterval\": \"300\"" //
        + "            }," //
        + "            {" //
        + "                \"id\"                : \""+ bulkMetricTypePrefix + ".2\"," //
        + "                \"unit\"              : \"BYTES\"," //
        + "                \"type\"              : \"GAUGE\"," //
        + "                \"collectionInterval\": \"300\"" //
        + "            }" //
        + "        ]" //
        + "    }," //
        + "    \"" + pathToResType +"\": {" //
        + "        \"relationship\": [" //
        + "            {" //
        + "                \"name\"     : \"incorporates\"," //
        + "                \"otherEnd\" : \"/t;"+ tenantId + "/f;"+ feedId + "/mt;"+ bulkMetricTypePrefix + ".1\"," //
        + "                \"direction\": \"outgoing\"" //
        + "            }," //
        + "            {" //
        + "                \"name\"     : \"incorporates\"," //
        + "                \"otherEnd\" : \"/t;"+ tenantId + "/f;"+ feedId + "/mt;"+ bulkMetricTypePrefix + ".1\"," //
        + "                \"direction\": \"outgoing\"" //
        + "            }," //
        + "            {" //
        + "                \"name\"     : \"incorporates\"," //
        + "                \"otherEnd\" : \"/t;"+ tenantId + "/f;"+ feedId + "/mt;"+ bulkMetricTypePrefix + ".2\"," //
        + "                \"direction\": \"outgoing\"" //
        + "            }" //
        + "        ]" //
        + "    }" //
        + "}";

        Response response = post(basePath + "/bulk", payload);

        assertEquals(201, response.code());

        JsonNode json = mapper.readTree(response.body().string());

        JsonNode resourceCodes = json.get("resource");
        JsonNode metricTypeCodes = json.get("metricType");
        JsonNode resourceTypeCodes = json.get("resourceType");
        JsonNode relationshipCodes = json.get("relationship");

        // check, there are no dupes
        assertEquals(2, resourceCodes.size());
        assertEquals(2, metricTypeCodes.size());
        assertEquals(1, resourceTypeCodes.size());
        assertEquals(2, relationshipCodes.size());

        // check, no 409 was raised, because only the first status code is taken
        assertEquals(201, resourceCodes.get("/t;" + tenantId + "/f;" + feedId + "/r;" + bulkResourcePrefix + ".1")
                .asInt());
        assertEquals(201, resourceCodes.get("/t;" + tenantId + "/f;" + feedId + "/r;" + bulkResourcePrefix + ".2")
                .asInt());
        assertEquals(201, resourceTypeCodes.get(pathToResType).asInt());
        assertEquals(201,
                metricTypeCodes.get("/t;" + tenantId + "/f;" + feedId + "/mt;" + bulkMetricTypePrefix + ".1").asInt());
        assertEquals(201,
                metricTypeCodes.get("/t;" + tenantId + "/f;" + feedId + "/mt;" + bulkMetricTypePrefix + ".2").asInt());

        assertEquals(201, relationshipCodes.get("/rl;" + PathSegmentCodec.encode(pathToResType
            + "-(incorporates)->"
            + "/t;"+ tenantId + "/f;"+ feedId + "/mt;"+ bulkMetricTypePrefix + "" + ".1")).asInt());
        assertEquals(201, relationshipCodes.get("/rl;" + PathSegmentCodec.encode(pathToResType
            + "-(incorporates)->"
            + "/t;"+ tenantId + "/f;"+ feedId + "/mt;"+ bulkMetricTypePrefix + "" + ".2")).asInt());

        delete(basePath + "/feeds/" + feedId +"/resources/" + bulkResourcePrefix + ".1");
        delete(basePath + "/feeds/" + feedId +"/metricTypes/" + bulkMetricTypePrefix + ".1");
        delete(basePath + "/feeds/" + feedId +"/metricTypes/" + bulkMetricTypePrefix + ".2");
//        client.delete(path: basePath + "/feeds/" + feedId +"/resourceTypes/" + bulkResourceTypePrefix" + ".1");
    }




    @Test
    public void testResourceBulkCreateWithErrors() throws Throwable {
        StringBuilder payload = new StringBuilder("{\"/e;" + environmentId + "\": {\"resource\": [");
        //this should fail
        payload.append("{\"id\": \"" + room1ResourceId + "\", \"resourceTypePath\": \"/rt;" + roomRTypeId + "\"},");
        //this should succeed
        payload.append("{\"id\": \"" + bulkResourcePrefix + "-1\", \"resourceTypePath\": \"/rt;" + roomRTypeId + "\"}");

        payload.append("]}}");
        Response response = post(basePath + "/bulk", payload.toString());

        assertEquals(201, response.code());
        JsonNode json = mapper.readTree(response.body().string());
        JsonNode codes = json.get("resource");
        assertEquals(2, codes.size());

        assertEquals(409, codes.get("/t;" + tenantId + "/e;" + environmentId + "/r;" + room1ResourceId).asInt());
        assertEquals(201,
                codes.get("/t;" + tenantId + "/e;" + environmentId + "/r;" + bulkResourcePrefix + "-1").asInt());

        delete(basePath + "/" + environmentId +"/resources/" + bulkResourcePrefix + "-1");
    }

    @Test
    public void testBulkCreateAndRelate() throws Throwable {
        String epath = "/t;"+ tenantId + "/e;"+ environmentId;
        String rpath = epath +"/r;" + bulkResourcePrefix + "-1";
        String mpath = epath +"/m;"+ responseTimeMetricId + "";
        String payload = "{" //
                + "\"" + epath + "\": {" //
                    + "\"resource\": [" //
                        + "{" //
                            + "\"id\": \"" + bulkResourcePrefix + "-1\"," //
                            + "\"resourceTypePath\": \"/rt;" + roomRTypeId + "\"" //
                        + "}" //
                    + "]" //
                + "}," +
                "\"" + rpath + "\": {" //
                    + "\"relationship\" : [" //
                        + "{" //
                            + "\"name\": \"incorporates\"," //
                            + "\"otherEnd\": \"" + mpath + "\"," //
                            + "\"direction\": \"outgoing\"" //
                        + "}" //
                    + "]" //
                + "}" //
            + "}";

        Response response = post(basePath + "/bulk", payload);
        assertEquals(201, response.code());
        JsonNode json = mapper.readTree(response.body().string());
        JsonNode resourceCodes = json.get("resource");
        JsonNode relationshipCodes = json.get("relationship");

        assertEquals(1, resourceCodes.size());
        assertEquals(201, resourceCodes.get(rpath).asInt());

        assertEquals(1, relationshipCodes.size());
        assertEquals(201, relationshipCodes.fields().next().getValue().asInt());

        // TODO : find out if this returning 404 instead of 204 is a bug or feature
        //delete(basePath + "/" + environmentId + "/resources/" + bulkResourcePrefix + "-1/metrics/../"
        //        + responseTimeMetricId);
        delete(basePath + "/" + environmentId +"/resources/" + bulkResourcePrefix +"-1");
    }


    @Test
    public void testComplexBulkCreate() throws Throwable {
        String env1 = "bulk-env-" + UUID.randomUUID().toString();
        String env2 = "bulk-env-" + UUID.randomUUID().toString();
        String rt1 = "bulk-URL" + UUID.randomUUID().toString();
        String rt2 = "bulk-URL2" + UUID.randomUUID().toString();
        String mt1 = "bulk-ResponseTime" + UUID.randomUUID().toString();

        String payload =
                "{"//
                + "  \"/t;"+ tenantId + "\": {"//
                + "    \"environment\": ["//
                + "       {"//
                + "         \"id\": \"" + env1 + "\","//
                + "         \"properties\": {\"key\": \"value\"},"//
                + "         \"outgoing\": {"//
                + "           \"customRel\": [\"/t;"+ tenantId + "\"]"//
                + "         }"//
                + "       },"//
                + "       {"//
                + "         \"id\": \""+ env2 +"\","//
                + "         \"properties\": {\"key\": \"value2\"}"//
                + "       }"//
                + "    ],"//
                + "    \"resourceType\": ["//
                + "       {"//
                + "         \"id\": \"" + rt1 +"\""//
                + "       },"//
                + "       {"//
                + "         \"id\": \""+ rt2 +"\""//
                + "       }"//
                + "    ],"//
                + "    \"metricType\": ["//
                + "      {"//
                + "        \"id\": \""+ mt1 +"\","//
                + "        \"type\": \"GAUGE\","//
                + "        \"unit\": \"MILLISECONDS\","//
                + "        \"collectionInterval\": \"1\""//
                + "      }"//
                + "    ]"//
                + "  },"//
                + "  \"/t;"+ tenantId + "/rt;" + rt1 + "\": {"//
                + "    \"dataEntity\": ["//
                + "      {"//
                + "        \"role\": \"configurationSchema\","//
                + "        \"value\": {"//
                + "          \"title\": \"URL config schema\","//
                + "          \"description\": \"A json schema describing configuration of an URL\","//
                + "          \"type\": \"string\""//
                + "        }"//
                + "      }"//
                + "    ],"//
                + "    \"operationType\": ["//
                + "      {"//
                + "        \"id\": \"ping\""//
                + "      }"//
                + "    ]"//
                + "  },"//
                + "  \"/t;"+ tenantId + "/rt;" + rt2 + "\": {"//
                + "    \"dataEntity\": ["//
                + "      {"//
                + "        \"role\": \"connectionConfigurationSchema\","//
                + "        \"value\": {"//
                + "          \"title\": \"URL2 connection config schema\","//
                + "          \"description\": \"A json schema describing connection to an URL\","//
                + "          \"type\": \"string\""//
                + "        }"//
                + "      }"//
                + "    ],"//
                + "    \"operationType\": ["//
                + "      {"//
                + "        \"id\": \"ping-pong\""//
                + "      }"//
                + "    ]"//
                + "  },"//
                + "  \"/t;"+ tenantId + "/e;" + env1 + "\": {"//
                + "    \"resource\": ["//
                + "      {"//
                + "        \"id\": \"url1\","//
                + "        \"resourceTypePath\": \"/t;"+ tenantId + "/rt;" + rt1 +"\""//
                + "      }"//
                + "    ],"//
                + "    \"metric\": ["//
                + "      {"//
                + "        \"id\": \"url1_responseTime\","//
                + "        \"metricTypePath\": \"/t;"+ tenantId + "/mt;"+ mt1 +"\""//
                + "      }"//
                + "    ]"//
                + "  },"//
                + "  \"/t;"+ tenantId + "/e;" + env1 +"/r;url1\": {"//
                + "    \"dataEntity\": ["//
                + "      {"//
                + "        \"role\": \"configuration\","//
                + "        \"value\": \"http://redhat.com\""//
                + "      }"//
                + "    ],"//
                + "    \"relationship\": ["//
                + "      {"//
                + "        \"name\": \"incorporates\","//
                + "        \"otherEnd\": \"/t;"+ tenantId + "/e;" + env1 +"/m;url1_responseTime\","//
                + "        \"direction\": \"outgoing\""//
                + "      }"//
                + "    ]"//
                + "  }"//
                + "}";


        Response response = post(basePath + "/bulk", payload);
        assertEquals(201, response.code());
        JsonNode json = mapper.readTree(response.body().string());

        JsonNode environmentCodes = json.get("environment");
        JsonNode resourceTypeCodes = json.get("resourceType");
        JsonNode metricTypeCodes = json.get("metricType");
        JsonNode dataCodes = json.get("dataEntity");
        JsonNode operationTypeCodes = json.get("operationType");
        JsonNode resourceCodes = json.get("resource");
        JsonNode metricCodes = json.get("metric");
        JsonNode relationshipCodes = json.get("relationship");

        //now make a second call, this time only create a metadata pack.
        //this has to be done in two requests, because the resource types need to be fully populated before they can
        //be put into the pack because afterwards they're frozen
        payload =
                "{"//
                + "  \"/t;"+ tenantId + "\": {"//
                + "    \"metadataPack\": ["//
                + "      {"//
                + "        \"members\": [\"/t;"+ tenantId + "/rt;" + rt1 + "\", \"/t;"+ tenantId + "/rt;" + rt2 +"\","//
                + "             \"/t;"+ tenantId + "/mt;"+ mt1 +"\"]"//
                + "      }"//
                + "    ]"//
                + "  }"//
                + "}";
        response = post(basePath + "/bulk", payload);
        assertEquals(201, response.code());
        json = mapper.readTree(response.body().string());

        JsonNode metadataPackCodes = json.get("metadataPack");

        assertEquals(2, environmentCodes.size());
        assertEquals(201, environmentCodes.get("/t;"+ tenantId + "/e;" + env1).asInt());
        assertEquals(201, environmentCodes.get("/t;"+ tenantId + "/e;" + env2).asInt());

        assertEquals(2, resourceTypeCodes.size());
        assertEquals(201, resourceTypeCodes.get("/t;"+ tenantId + "/rt;" + rt1).asInt());
        assertEquals(201, resourceTypeCodes.get("/t;"+ tenantId + "/rt;" + rt2).asInt());

        assertEquals(1, metricTypeCodes.size());
        assertEquals(201, metricTypeCodes.get("/t;"+ tenantId + "/mt;" + mt1).asInt());

        assertEquals(3, dataCodes.size());
        assertEquals(201, dataCodes.get("/t;"+ tenantId + "/rt;" + rt1 +"/d;configurationSchema").asInt());
        assertEquals(201, dataCodes.get("/t;"+ tenantId + "/rt;" + rt2 +"/d;connectionConfigurationSchema").asInt());
        assertEquals(201, dataCodes.get("/t;"+ tenantId + "/e;" + env1 +"/r;url1/d;configuration").asInt());

        assertEquals(2, operationTypeCodes.size());
        assertEquals(201, operationTypeCodes.get("/t;"+ tenantId + "/rt;" + rt1 +"/ot;ping").asInt());
        assertEquals(201, operationTypeCodes.get("/t;"+ tenantId + "/rt;" + rt2 +"/ot;ping-pong").asInt());

        assertEquals(1, resourceCodes.size());
        assertEquals(201, resourceCodes.get("/t;"+ tenantId + "/e;" + env1 +"/r;url1").asInt());

        assertEquals(1, metricCodes.size());
        assertEquals(201, metricCodes.get("/t;"+ tenantId + "/e;" + env1 +"/m;url1_responseTime").asInt());

        assertEquals(1, relationshipCodes.size());
        assertEquals(201, relationshipCodes.fields().next().getValue().asInt());

        assertEquals(1, metadataPackCodes.size());
        assertEquals(201, metadataPackCodes.fields().next().getValue().asInt());

        response = get(basePath + "/" + env1 +"/resources/url1/metrics");
        json = mapper.readTree(response.body().string());
        assertEquals("/t;"+ tenantId + "/e;" + env1 +"/m;url1_responseTime", json.get(0).get("path").asText());

        String mpPath = metadataPackCodes.fields().next().getKey();
        String mpId = mpPath.substring(mpPath.lastIndexOf(";") + 1);
        delete(basePath + "/metadatapacks/" + mpId);
        delete(basePath + "/environments/" + env1);
        delete(basePath + "/environments/" + env2);
        delete(basePath + "/resourceTypes/" + rt1);
        delete(basePath + "/metricTypes/" + mt1);
    }

    @Test
    public void testMetadataPacks() throws Throwable {
        Response response = post(basePath + "/metadatapacks",
                "{ \"members\": [\"/t;" + tenantId + "/rt;" + urlTypeId + "\"]}");
        JsonNode json = mapper.readTree(response.body().string());
        String mpId = json.get("id").asText();
        String url = baseURI + basePath + "/resourceTypes/" + urlTypeId;
        response = client.newCall(newAuthRequest().url(url).delete().build()).execute();
        assertEquals("Deleting a resource type that is part of metadatapack should not be possible.",
                400, response.code());

        delete(basePath + "/metadatapacks/" + mpId);
    }

    @Test
    public void testRecursiveChildren() throws Throwable {
        try {
            Response response = post(basePath + "/" + environmentId +"/resources",
                "{ \"id\": \"rootResource\", \"resourceTypePath\": \"/" + urlTypeId +"\"}");
            assertEquals(201, response.code());

            response = post(basePath + "/" + environmentId +"/resources/rootResource",
                "{\"id\": \"childResource\", \"resourceTypePath\": \"/" + urlTypeId +"\"}" );
            assertEquals(201, response.code());

            response = post(basePath + "/" + environmentId +"/resources/rootResource/childResource",
                        "{\"id\": \"grandChildResource\", \"resourceTypePath\": \"/" + urlTypeId +"\"}");
            assertEquals(201, response.code());

            response = post(basePath + "/" + environmentId +"/resources/rootResource/childResource",
                        "{\"id\": \"grandChildResource2\", \"resourceTypePath\": \"/" + roomRTypeId + "\"}");
            assertEquals(201, response.code());

            response = get(basePath + "/" + environmentId +"/resources/rootResource/recursiveChildren",
                    "typeId", urlTypeId);

            JsonNode ret = mapper.readTree(response.body().string());

            assertEquals(2, ret.size());
            Assert.assertTrue(toStream(ret).anyMatch(node -> "childResource".equals(node.get("id").asText())));
            Assert.assertTrue(toStream(ret).anyMatch(node -> "grandChildResource".equals(node.get("id").asText())));

            response = get(basePath + "/" + environmentId +"/resources/rootResource/recursiveChildren",
                    "typeId", roomRTypeId);

            ret = mapper.readTree(response.body().string());
            assertEquals(1, ret.size());
            Assert.assertTrue(toStream(ret).anyMatch(node -> "grandChildResource2".equals(node.get("id").asText())));
        } finally {
            delete(basePath + "/" + environmentId +"/resources/rootResource");
        }
    }

    @Test
    public void testSync() throws Throwable {
        String structure = "{"//
                + "\"type\": \"feed\","//
                + "\"data\": {"//
                + "    \"id\": \"sync-feed\""//
                + "},"//
                + "\"children\": {"//
                + "    \"resource\": ["//
                + "        {"//
                + "            \"data\": {"//
                + "                \"id\": \"resource\","//
                + "                \"resourceTypePath\": \"resourceType\""//
                + "            },"//
                + "            \"children\": {"//
                + "                \"resource\": ["//
                + "                    {"//
                + "                        \"data\": {"//
                + "                            \"id\": \"childResource\","//
                + "                            \"resourceTypePath\": \"../resourceType\""//
                + "                        }"//
                + "                    }"//
                + "                ]"//
                + "            }"//
                + "        }"//
                + "    ],"//
                + "    \"resourceType\": ["//
                + "        {"//
                + "            \"data\": {"//
                + "                \"id\": \"resourceType\","//
                + "                \"name\": \"My Resource Type With A Friendly Name\""//
                + "            }"//
                + "        }"//
                + "    ],"//
                + "    \"metric\": ["//
                + "        {"//
                + "            \"data\": {"//
                + "                \"id\": \"metric\","//
                + "                \"metricTypePath\": \"metricType\","//
                + "                \"collectionInterval\": 0"//
                + "            }"//
                + "        }"//
                + "    ],"//
                + "    \"metricType\": ["//
                + "        {"//
                + "            \"data\": {"//
                + "                \"id\": \"metricType\","//
                + "                \"type\": \"GAUGE\","//
                + "                \"unit\": \"NONE\","//
                + "                \"collectionInterval\": 0,"//
                + "                \"name\": \"My Metric Type With A Friendly Name\""//
                + "            }"//
                + "        }"//
                + "    ]"//
                + "}"//
                + "}";

        try {
            Response response = post(basePath + "/feeds", "{\"id\": \"sync-feed\"}");
            assertEquals(201, response.code());

            response = post(basePath + "/sync/f;sync-feed", structure);

            assertEquals(204, response.code());

            //check that stuff is there
            response = get(basePath + "/path/f;sync-feed");
            assertEquals(200, response.code());
            response = get(basePath + "/path/f;sync-feed/r;resource");
            assertEquals(200, response.code());
            response = get(basePath + "/path/f;sync-feed/r;resource/r;childResource");
            assertEquals(200, response.code());
            response = get(basePath + "/path/f;sync-feed/rt;resourceType");
            assertEquals(200, response.code());
            response = get(basePath + "/path/f;sync-feed/mt;metricType");
            assertEquals(200, response.code());
        } finally {
            Response response = get(basePath + "/path/f;sync-feed");
            if (response.code() == 200) {
                delete(basePath + "/feeds/sync-feed");
            }
        }
    }

    protected static void assertEntityExists(String path, String cp) throws Throwable {
        assertEntityExists(path, new String[0], cp);
    }

    protected static void assertEntityExists(String path, String[] queryParams, String cp) throws Throwable {
        Response response = get(basePath + "/" + path, queryParams);
        assertEquals(200, response.code());
        JsonNode json = mapper.readTree(response.body().string());
        assertEquals(fullCanonicalPath(cp), json.get("path").asText());
    }

    protected static void assertEntitiesExist(String path, String... cps) throws Throwable {
        List<String> expectedPaths = Arrays.stream(cps).map(cp -> fullCanonicalPath(cp)).collect(Collectors.toList());

        Response response = get(basePath + "/" + path);
        JsonNode json = mapper.readTree(response.body().string());

        List<String> entityPaths = toStream(json).map(node -> node.get("path").asText()).collect(Collectors.toList());

        for (Iterator<String> it = expectedPaths.iterator(); it.hasNext();) {
            String cp = it.next();
            if (entityPaths.remove(cp)) {
                it.remove();
            }
        }

        Assert.assertTrue("Unexpected entities with paths: " + entityPaths, entityPaths.isEmpty());
        Assert.assertTrue("Following entities not found: " + expectedPaths, expectedPaths.isEmpty());
    }

    protected static Stream<JsonNode> toStream(JsonNode json) {
        Spliterator<JsonNode> jsonSpliterator = Spliterators.spliteratorUnknownSize(json.elements(), 0);
        return StreamSupport.stream(jsonSpliterator, false);
    }

    protected static void assertRelationshipJsonldExists(String path, String source, String label, String target)
            throws Throwable {
        Response response = get(basePath + "/" + path, "jsonld", "true");
        JsonNode json = mapper.readTree(response.body().string());
        boolean found = toStream(json)
                .anyMatch(node ->
                source.equals(node.get("source").get("shortId").asText())
                && label.equals(node.get("name").asText())
                && target.equals(node.get("target").get("shortId").asText()));
        Assert.assertTrue("Following edge not found: source: "+ source +", name: "+ label +", target: " + target,
                found);
    }

    protected static void assertRelationshipExists(final String path, final String source, final String label,
            String target, String... query) throws Throwable {
        Response response = get(basePath + "/" + path, query);
        List<Relationship> rels = mapper.readValue(response.body().string(), new TypeReference<List<Relationship>>(){});

        Assert.assertTrue("Following Relationship not found: " + source +", " + label + ", " + target,
                rels.stream().anyMatch(
                        r -> source.equals(r.getSource().toString())
                            && label.equals(r.getName())
                            && target.equals(r.getTarget().toString())
                        )
                );
    }

    /* Add the deletable path to {@link #pathsToDelete} and send a {@code POST} request using the given map of
     * arguments. */
    protected static Response postDeletable(String path, Entity.Blueprint blueprint) throws Throwable {
        String getVerificationPath = path + "/" + PathSegmentCodec.encode(blueprint.getId());
        return postDeletable(path, blueprint, getVerificationPath);
    }
    protected static Response postDeletable(String path, Entity.Blueprint blueprint, String getVerificationPath)
            throws Throwable {
        String postPath = basePath + "/" + path;
        String key = postPath + "/" + PathSegmentCodec.encode(blueprint.getId());
        pathsToDelete.put(key, basePath + "/" + getVerificationPath);
        return postNew(postPath, blueprint);
    }

    protected static String fullCanonicalPath(String cp) {
        return CanonicalPath.fromPartiallyUntypedString(cp, CanonicalPath.of().tenant(tenantId).get(),
                SegmentType.ANY_ENTITY)
                .toString();
    }

}
