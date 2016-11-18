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
package org.hawkular.inventory.json;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;
import static org.hawkular.inventory.api.model.MetricDataType.COUNTER;
import static org.hawkular.inventory.api.model.MetricDataType.GAUGE;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import org.hawkular.inventory.api.FilterFragment;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.filters.Contained;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Incorporated;
import org.hawkular.inventory.api.filters.Marker;
import org.hawkular.inventory.api.filters.RecurseFilter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.SwitchElementType;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.IdentityHash;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.SyncHash;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.spi.NoopFilter;
import org.hawkular.inventory.json.mixins.model.TenantlessCanonicalPathMixin;
import org.hawkular.inventory.json.mixins.model.TenantlessRelativePathMixin;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.RelativePath;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public class SerializationTest {

    private ObjectMapper mapper;

    @Before
    public void setup() {
        JsonFactory f = new JsonFactory();

        mapper = new ObjectMapper(f);

        InventoryJacksonConfig.configure(mapper);
    }

    @Test
    public void testCanonicalPath() throws Exception {
        test(CanonicalPath.fromString("/t;t/e;e/r;r"));
    }

    @Test
    public void testTenantlessCanonicalPath() throws Exception {
        mapper.addMixIn(CanonicalPath.class, TenantlessCanonicalPathMixin.class);
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        DetypedPathDeserializer.setCurrentEntityType(Resource.class);
        test(CanonicalPath.fromString("/t;t/e;e/r;r"));
    }

    @Test
    public void testRelativePath() throws Exception {
        test(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"),
                Metric.SEGMENT_TYPE));
    }

    @Test
    public void testTenantlessRelativePath() throws Exception {
        mapper.addMixIn(RelativePath.class, TenantlessRelativePathMixin.class);
        DetypedPathDeserializer.setCurrentEntityType(Metric.class);
        DetypedPathDeserializer.setCurrentRelativePathOrigin(CanonicalPath.fromString("/t;t/e;e/r;r"));
        test(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"),
                Metric.SEGMENT_TYPE)        );

        //the test above doesn't test for deserializing a de-typed path.
        RelativePath rp = deserialize("\"../g\"", RelativePath.class);
        Assert.assertEquals(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"),
                Metric.SEGMENT_TYPE), rp);
    }

    @Test
    public void testTenant() throws Exception {
        Tenant t = new Tenant(CanonicalPath.fromString("/t;c"), "contentHash", new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(t);
    }

    @Test
    public void testDetypedTenant() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(null);

        Tenant t = new Tenant(CanonicalPath.fromString("/t;c"), "contentHash", new HashMap<String, Object>() {{
            put("a", "b");
        }});
        String ser = "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}";

        testDetyped(t, ser);
    }

    @Test
    public void testEnvironment() throws Exception {
        Environment env = new Environment(CanonicalPath.fromString("/t;t/e;c"), "contentHash",
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(env);
    }

    @Test
    public void testDetypedEnvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Environment env = new Environment(CanonicalPath.fromString("/t;t/e;c"), "contentHash",
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        testDetyped(env, "{\"path\":\"/e;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(env, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(env, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceType() throws Exception {
        ResourceType rt = new ResourceType(CanonicalPath.fromString("/t;t/rt;c"), "a", "b", "c",
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(rt);
    }

    @Test
    public void testDetypedResourceType() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        ResourceType rt = new ResourceType(CanonicalPath.fromString("/t;t/rt;c"), null, null, null,
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        testDetyped(rt, "{\"path\":\"/t;t/rt;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(rt, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(rt, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricType() throws Exception {
        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), "a", null, null, MetricUnit.BYTES,
                COUNTER, new HashMap<String, Object>() {{
                    put("a", "b");
                }}, 0L);

        test(mt);
    }

    @Test
    public void testMetricTypeIsUpperCase() throws Exception {
        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), "a", null, null, MetricUnit.BYTES,
                COUNTER, new HashMap<String, Object>() {{
            put("a", "b");
        }}, 0L);

        String ser = serialize(mt);

        JsonNode json = mapper.readTree(ser);

        Assert.assertTrue(json.isObject());
        Assert.assertTrue(json.has("metricDataType"));
        Assert.assertEquals(COUNTER.name(), json.get("metricDataType").textValue());
    }

    @Test
    public void testMetricTypeBlueprintIsUpperCase() throws Exception {
        MetricType.Blueprint mt = MetricType.Blueprint.builder(COUNTER).withId("c").withInterval(0L)
                .withUnit(MetricUnit.BYTES).withProperties(new HashMap<String, Object>() {{
                    put("a", "b");
                }}).build();

        String ser = serialize(mt);

        JsonNode json = mapper.readTree(ser);

        Assert.assertTrue(json.isObject());
        Assert.assertTrue(json.has("metricDataType"));
        Assert.assertEquals(COUNTER.name(), json.get("metricDataType").textValue());
    }

    @Test
    public void testDetypedMetricType() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), null, null, null, MetricUnit.BYTES, GAUGE,
                new HashMap<String, Object>() {{
                    put("a", "b");
                }}, 0L);

        testDetyped(mt, "{\"path\":\"/t;t/mt;c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTES\", " +
                "\"collectionInterval\":\"0\"}");
        testDetyped(mt, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTES\", " +
                "\"collectionInterval\":\"0\"}");
        testDetyped(mt, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTES\"," +
                " \"collectionInterval\":\"0\"}");
    }

    @Test
    public void testFeed() throws Exception {
        Feed f = new Feed(CanonicalPath.fromString("/t;t/f;c"), null, null, null, new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(f);
    }

    @Test
    public void testDetypedFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Feed f = new Feed(CanonicalPath.fromString("/t;t/f;c"), null, null, null, new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(f, "{\"path\":\"/t;t/f;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(f, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(f, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceInEnvironment() throws Exception {
        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/r;c"), null, null, null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null, null, null),
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(r);
    }

    @Test
    public void testDetypedResourceInEvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/r;c"), null, null, null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null, null, null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(r, "{\"path\":\"/t;t/e;e/r;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(r, "{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricInEnvironment() throws Exception {
        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/m;c"), null, null, null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null, null, null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);
    }

    @Test
    public void testDetypedMetricInEnvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/m;c"), null, null, null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null, null, null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(m, "{\"path\":\"/t;t/e;e/m;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(m, "{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceInFeed() throws Exception {
        Resource r = new Resource(CanonicalPath.fromString("/t;t/f;f/r;c"), null, null, null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null, null, null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(r);
    }

    @Test
    public void testDetypedResourceInFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Resource r = new Resource(CanonicalPath.fromString("/t;t/f;f/r;c"), null, null, null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null, null, null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(r, "{\"path\":\"/t;t/f;f/r;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(r, "{\"path\":\"/f/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricInFeed() throws Exception {
        Metric m = new Metric(CanonicalPath.fromString("/t;t/f;f/m;c"), null, null, null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null, null, null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);
    }

    @Test
    public void testDetypedMetricInFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Metric m = new Metric(CanonicalPath.fromString("/t;t/f;f/m;c"), null, null, null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null, null, null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(m, "{\"path\":\"/t;t/f;f/m;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(m, "{\"path\":\"/f/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testOperationType() throws Exception {
        OperationType ot = new OperationType(CanonicalPath.fromString("/t;t/rt;rt/ot;ot"), null, null, null);

        test(ot);
    }

    @Test
    public void testStructuredData() throws Exception {
        test(StructuredData.get().bool(true));
        test(StructuredData.get().integral(42L));
        test(StructuredData.get().floatingPoint(1.D));
        test(StructuredData.get().string("answer"));
        test(StructuredData.get().list().addBool(true).build());
        test(StructuredData.get().list().addList().addBool(true).addIntegral(2L).closeList().build());
        test(StructuredData.get().list().addMap().putIntegral("answer", 42L).closeMap().build());
        test(StructuredData.get().map().putBool("yes", true).build());
        test(StructuredData.get().map().putList("answer-list").addIntegral(42L).closeList().build());
    }

    @Test
    public void testDataEntity() throws Exception {
        test(new DataEntity(CanonicalPath.of().tenant("t").environment("e").resource("r").get(),
                DataRole.Resource.connectionConfiguration,
                StructuredData.get().list().addIntegral(1).addIntegral(2).build(), null, null, null));
    }

    @Test
    public void testEntityBlueprint() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>() {{
            put("key", "value");
        }};

        Map<String, Set<CanonicalPath>> incoming = new HashMap<String, Set<CanonicalPath>>() {{
            put("duck", Collections.singleton(CanonicalPath.of().tenant("t").get()));
        }};
        Map<String, Set<CanonicalPath>> outgoing = new HashMap<String, Set<CanonicalPath>>() {{
            put("kachna", Collections.singleton(CanonicalPath.of().tenant("t").get()));
        }};

        testBlueprint(DataEntity.Blueprint.builder().withRole(DataRole.ResourceType.configurationSchema).withName("nd")
                .withIncomingRelationships(incoming).withOutgoingRelationships(outgoing).withProperties(properties)
                .build(), (bl, dbl) -> {
            Assert.assertEquals(bl.getRole(), dbl.getRole());
            Assert.assertEquals(bl.getValue(), dbl.getValue());
        });

        testBlueprint(Environment.Blueprint.builder().withId("e").withName("ne").withIncomingRelationships(incoming)
                .withOutgoingRelationships(outgoing).withProperties(properties).build(), null);

        testBlueprint(Feed.Blueprint.builder().withId("f").withName("nf").withIncomingRelationships(incoming)
                .withOutgoingRelationships(outgoing).withProperties(properties).build(), null);

        testBlueprint(Metric.Blueprint.builder().withId("m").withName("nm").withIncomingRelationships(incoming)
                .withOutgoingRelationships(outgoing).withProperties(properties).withMetricTypePath("kachna")
                .build(), null);

        testBlueprint(MetricType.Blueprint.builder(GAUGE).withId("mt").withName("nmt").withUnit(MetricUnit.NONE)
                .withIncomingRelationships(incoming).withOutgoingRelationships(outgoing).withProperties(properties)
                .build(), null);

        testBlueprint(OperationType.Blueprint.builder().withId("ot").withName("not")
                .withIncomingRelationships(incoming).withOutgoingRelationships(outgoing).withProperties(properties)
                .build(), null);

        testBlueprint(Resource.Blueprint.builder().withId("r").withName("nr")
                .withIncomingRelationships(incoming).withOutgoingRelationships(outgoing).withProperties(properties)
                .withResourceTypePath("kachna").build(), null);

        testBlueprint(ResourceType.Blueprint.builder().withId("rt").withName("nrt")
                .withIncomingRelationships(incoming).withOutgoingRelationships(outgoing).withProperties(properties)
                .build(), null);

        testBlueprint(Tenant.Blueprint.builder().withId("t").withName("nt").withIncomingRelationships(incoming)
                .withOutgoingRelationships(outgoing).withProperties(properties).build(), null);
    }

    @Test
    public void testEntityUpdate() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>() {{
            put("key", "value");
        }};

        testUpdate(DataEntity.Update.builder().withName("nd").withProperties(properties)
                .withValue(null).build(), (bl, dbl) -> {
            Assert.assertEquals(bl.getValue(), dbl.getValue());
        });

        testUpdate(Environment.Update.builder().withName("ne").withProperties(properties).build(), null);

        testUpdate(Feed.Update.builder().withName("nf").withProperties(properties).build(), null);

        testUpdate(Metric.Update.builder().withName("nm").withProperties(properties).build(), null);

        testUpdate(MetricType.Update.builder().withName("nmt").withProperties(properties).build(), null);

        testUpdate(OperationType.Update.builder().withName("not").withProperties(properties).build(), null);

        testUpdate(Resource.Update.builder().withName("nr").withProperties(properties).build(), null);

        testUpdate(ResourceType.Update.builder().withName("nrt").withProperties(properties).build(), null);

        testUpdate(Tenant.Update.builder().withName("nt").withProperties(properties).build(), null);
    }

    @Test
    public void testQuery() throws Exception {
        Query query = Query.to(CanonicalPath.fromString("/t;fooTenant"));
        test(query);

        query = new Query.Builder().with(new FilterFragment(new With.Ids("a"))).build();
        test(query);

        query = Query.path().with(type(Tenant.class)).with(id("t")).filter().with(by(contains)).with(type
                (Environment.class)).with(id("e")).with(by(contains)).with(type(Tenant.class)).with(id("t2")).get();
        test(query);

        query = Query.path().with(type(Tenant.class)).with(id("t")).with(by(contains)).with(type(Environment.class))
                .with(id("e")).with(by(contains)).with(type(Resource.class)).with(id("r")).get();
        test(query);

        query = Query.path().with(RelationWith.Ids.pathTo(CanonicalPath.fromString("/t;fooTenant"))).get();
        test(query);

        // RelationFilter
        query = Query.filter().with(RelationWith.name("__inPrediction"), RelationWith.ids("id1", "id2"),
                RelationWith.id("id4"), RelationWith.property("prop"), RelationWith.propertyValue("prop2", "value"))
                .with(RelationWith.sourceOfType(Tenant.class))
                .with(RelationWith.targetsOfTypes(Metric.class, MetricType.class))
                .with(RelationWith.SourceOrTargetOfType.pathTo(CanonicalPath.fromString("/t;tenant")))
                .with(RelationWith.SourceOrTargetOfType.by(RelationWith.name("name")).get()).get();
        test(query);

        // Related
        query = Query.filter().with(Related.asTargetWith(CanonicalPath.fromString("/t;tenant"), "relation"),
                Incorporated.by("/t;tenant"), Contained.in(CanonicalPath.fromString("/t;tenant")),
                Defined.by("/t;tenant")).get();
        test(query);

        // With.DataAt
        query = Query.path().with(new With.DataAt(RelativePath.fromString("../e;e/../t;t"))).get();
        test(query);

        // With.RelativePaths
        query = Query.to(CanonicalPath.fromString("/t;tenant")).extend().with(
                With.RelativePaths.pathTo(CanonicalPath.fromString("/t;tenant"))).get();
        test(query);

        // With.DataOfTypes
        query = Query.path().with(With.type(Tenant.class))
                .withExact(Query.filter().with(With.DataOfTypes.pathTo(CanonicalPath.fromString("/t;tenant"))).get())
                .get();
        test(query);

        // With.Types
        query = Query.filter().with(With.Types.by(With.Types.pathTo(CanonicalPath.fromString("/t;tenant"))).get())
                .get();
        test(query);

        // With.PropertyValues
        query = Query.path().with(RelationWith.PropertyValues.pathTo(CanonicalPath.fromString("/t;tenant"))).get();
        test(query);

        // With.CanonicalPaths
        query = Query.filter().with(With.CanonicalPaths.pathTo(CanonicalPath.fromString("/t;tenant"))).get();
        test(query);

        // With.Ids
        query = Query.filter().with(new With.Ids("id1", "id2")).get();
        test(query);

        // With.DataValued
        query = Query.path().with(new With.DataValued(new Double(3))).get();
        test(query);

        // Marker
        query = Query.filter().with(new Marker()).get();
        test(query);

        // SwitchElementType
        query = Query.filter().with(SwitchElementType.incomingRelationships(),
                SwitchElementType.incomingRelationships(),
                SwitchElementType.sourceEntities(),
                SwitchElementType.targetEntities())
                .get();
        test(query);

        // RecurseFilter
        query = Query.filter().with(RecurseFilter.pathTo(CanonicalPath.fromString("/t;tenant"))).get();
        test(query);

        // NoopFilter
        query = Query.filter().with(NoopFilter.INSTANCE).get();
        test(query);
    }

    @Test
    public void testPager() throws Exception {
        Pager pager = Pager.builder().orderBy(Order.by("name", Order.Direction.ASCENDING)).withPageSize(2)
                .withStartPage(1).build();

        String json = serialize(pager);
        Pager fromJson = deserialize(json, Pager.class);

        String expected = "{\"pageNumber\":1,\"pageSize\":2," +
                "\"order\":[{\"field\":\"name\",\"direction\":\"ASCENDING\"}]}";

        assertThat(json, is(equalTo(expected)));
        assertThat(fromJson.getOrder().equals(pager.getOrder()), is(Boolean.TRUE));
    }

    @Test
    public void testInventoryStructure() throws Exception {
        InventoryStructure<?> s = InventoryStructure.Offline.of(Feed.Blueprint.builder().withId("feed").build())
                .addChild(ResourceType.Blueprint.builder().withId("resourceType").build())
                .addChild(MetricType.Blueprint.builder(GAUGE).withId("metricType").withUnit(MetricUnit.NONE)
                        .withInterval(0L).build())
                .addChild(Metric.Blueprint.builder().withId("metrics").withMetricTypePath("metricType").withInterval
                        (0L).build())
                .startChild(
                        Resource.Blueprint.builder().withId("resource").withResourceTypePath("resourceType").build())
                .addChild(Resource.Blueprint.builder().withId("childResource").withResourceTypePath("../.resourceType")
                        .build())
                .end()
                .build();

        test(s);
    }

    @Test
    public void testIdentityHashTree() throws Exception {
        InventoryStructure<?> s = InventoryStructure.Offline.of(Feed.Blueprint.builder().withId("feed").build())
                .addChild(ResourceType.Blueprint.builder().withId("resourceType").build())
                .addChild(MetricType.Blueprint.builder(GAUGE).withId("metricType").withUnit(MetricUnit.NONE)
                        .withInterval(0L).build())
                .addChild(Metric.Blueprint.builder().withId("metrics").withMetricTypePath("metricType").withInterval
                        (0L).build())
                .startChild(
                        Resource.Blueprint.builder().withId("resource").withResourceTypePath("resourceType").build())
                .addChild(Resource.Blueprint.builder().withId("childResource").withResourceTypePath("../.resourceType")
                        .build())
                .end()
                .build();

        IdentityHash.Tree t = IdentityHash.treeOf(s);

        test(t);
    }

    @Test
    public void testSyncHashTree() throws Exception {
        InventoryStructure<?> s = InventoryStructure.Offline.of(Feed.Blueprint.builder().withId("feed").build())
                .addChild(ResourceType.Blueprint.builder().withId("resourceType").build())
                .addChild(MetricType.Blueprint.builder(GAUGE).withId("metricType").withUnit(MetricUnit.NONE)
                        .withInterval(0L).build())
                .addChild(Metric.Blueprint.builder().withId("metrics").withMetricTypePath("metricType").withInterval
                        (0L).build())
                .startChild(
                        Resource.Blueprint.builder().withId("resource").withResourceTypePath("resourceType").build())
                .addChild(Resource.Blueprint.builder().withId("childResource").withResourceTypePath("../.resourceType")
                        .build())
                .end()
                .build();

        SyncHash.Tree t = SyncHash.treeOf(s, CanonicalPath.of().tenant("tnt").feed("feed").get());

        test(t);
    }

    private void testDetyped(Entity<?, ?> orig, String serialized) throws Exception {
        DetypedPathDeserializer.setCurrentEntityType(orig.getClass());
        mapper.addMixIn(CanonicalPath.class, TenantlessCanonicalPathMixin.class);

        Assert.assertEquals(orig, deserialize(serialized, orig.getClass()));
    }

    private <T extends Entity.Blueprint> void testBlueprint(T bl, BiConsumer<T, T> additionalTests) throws Exception {
        String ser = serialize(bl);

        @SuppressWarnings("unchecked")
        T dbl = (T) deserialize(ser, bl.getClass());

        BeanInfo beanInfo = Introspector.getBeanInfo(bl.getClass());
        for (PropertyDescriptor prop : beanInfo.getPropertyDescriptors()) {
            Object origValue = prop.getReadMethod().invoke(bl);
            Object newValue = prop.getReadMethod().invoke(dbl);
            Assert.assertTrue("Unexpected value of property '" + prop.getName() + "' on class " + bl.getClass(),
                    isEqual(origValue, newValue));
        }

        if (additionalTests != null) {
            additionalTests.accept(bl, dbl);
        }
    }

    private <T extends Entity.Update> void testUpdate(T bl, BiConsumer<T, T> additionalTests) throws Exception {
        String ser = serialize(bl);

        @SuppressWarnings("unchecked")
        T dbl = (T) deserialize(ser, bl.getClass());

        Assert.assertEquals(bl.getName(), dbl.getName());
        Assert.assertEquals(bl.getProperties(), dbl.getProperties());

        if (additionalTests != null) {
            additionalTests.accept(bl, dbl);
        }
    }

    private void test(Object o) throws Exception {
        Class<?> cls = o.getClass();

        Object o2 = deserialize(serialize(o), cls);

        Assert.assertEquals(o, o2);

        BeanInfo beanInfo = Introspector.getBeanInfo(cls);
        for (PropertyDescriptor prop : beanInfo.getPropertyDescriptors()) {
            Object origValue = prop.getReadMethod().invoke(o);
            Object newValue = prop.getReadMethod().invoke(o2);
            Assert.assertTrue("Unexpected value of property '" + prop.getName() + "' on class " + cls,
                    isEqual(origValue, newValue));
        }
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null) {
            return b == null;
        } else if (a.getClass().isArray()) {
            if (b == null || !b.getClass().isArray()) {
                return false;
            }

            int aLen = Array.getLength(a);
            int bLen = Array.getLength(b);

            if (aLen != bLen) {
                return false;
            }

            for (int i = 0; i < aLen; ++i) {
                Object aVal = Array.get(a, i);
                Object bVal = Array.get(b, i);

                if (!isEqual(aVal, bVal)) {
                    return false;
                }
            }

            return true;
        } else if (a instanceof Collection) {
            // do a piecewise comparison ourselves. Mainly because Collections.UnmodifiableCollection doesn't implement
            // this as expected. See {@link Collections#unmodifiableCollection(Collection)}
            Collection<?> as = (Collection<?>) a;
            Collection<?> bs = (Collection<?>) b;

            if (as.size() != bs.size()) {
                return false;
            }

            Iterator<?> ai = as.iterator();
            Iterator<?> bi = bs.iterator();

            while (ai.hasNext()) {
                Object ao = ai.next();
                Object bo = bi.next();

                if (!Objects.equals(ao, bo)) {
                    return false;
                }
            }

            return true;
        } else {
            return a.equals(b);
        }
    }

    private String serialize(Object object) throws IOException {
        StringWriter out = new StringWriter();

        JsonGenerator gen = mapper.getFactory().createGenerator(out);

        gen.writeObject(object);

        gen.close();

        out.close();

        return out.toString();
    }

    private <T> T deserialize(String json, Class<T> type) throws Exception {
        JsonParser parser = mapper.getFactory().createParser(json);

        return parser.readValueAs(type);
    }
}
