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

import static org.hawkular.inventory.api.model.MetricDataType.GAUGE;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.json.mixins.TenantlessCanonicalPathMixin;
import org.hawkular.inventory.json.mixins.TenantlessRelativePathMixin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
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
        test(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"), Metric.class));
    }

    @Test
    public void testTenantlessRelativePath() throws Exception {
        mapper.addMixIn(RelativePath.class, TenantlessRelativePathMixin.class);
        DetypedPathDeserializer.setCurrentEntityType(Metric.class);
        DetypedPathDeserializer.setCurrentRelativePathOrigin(CanonicalPath.fromString("/t;t/e;e/r;r"));
        test(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"), Metric.class)
        );

        //the test above doesn't test for deserializing a de-typed path.
        RelativePath rp = deserialize("\"../g\"", RelativePath.class);
        Assert.assertEquals(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"),
                Metric.class), rp);
    }

    @Test
    public void testTenant() throws Exception {
        Tenant t = new Tenant(CanonicalPath.fromString("/t;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(t);
    }

    @Test
    public void testDetypedTenant() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(null);

        Tenant t = new Tenant(CanonicalPath.fromString("/t;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});
        String ser = "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}";

        testDetyped(t, ser);
    }

    @Test
    public void testEnvironment() throws Exception {
        Environment env = new Environment(CanonicalPath.fromString("/t;t/e;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(env);
    }

    @Test
    public void testDetypedEnvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Environment env = new Environment(CanonicalPath.fromString("/t;t/e;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(env, "{\"path\":\"/e;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(env, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(env, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceType() throws Exception {
        ResourceType rt = new ResourceType(CanonicalPath.fromString("/t;t/rt;c"), null, new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(rt);
    }

    @Test
    public void testDetypedResourceType() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        ResourceType rt = new ResourceType(CanonicalPath.fromString("/t;t/rt;c"), null, new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(rt, "{\"path\":\"/t;t/rt;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(rt, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(rt, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricType() throws Exception {
        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), null, MetricUnit.BYTES, GAUGE,
                new HashMap<String, Object>() {{
                    put("a", "b");
                }}, 0L);

        test(mt);
    }

    @Test
    public void testDetypedMetricType() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), null, MetricUnit.BYTES, GAUGE,
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
        Feed f = new Feed(CanonicalPath.fromString("/t;t/f;c"), null, new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(f);
    }

    @Test
    public void testDetypedFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Feed f = new Feed(CanonicalPath.fromString("/t;t/f;c"), null, new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        testDetyped(f, "{\"path\":\"/t;t/f;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(f, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(f, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceInEnvironment() throws Exception {
        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/r;c"), null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(r);
    }

    @Test
    public void testDetypedResourceInEvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/r;c"), null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(r, "{\"path\":\"/t;t/e;e/r;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(r, "{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricInEnvironment() throws Exception {
        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/m;c"), null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);
    }

    @Test
    public void testDetypedMetricInEnvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/m;c"), null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(m, "{\"path\":\"/t;t/e;e/m;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(m, "{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceInFeed() throws Exception {
        Resource r = new Resource(CanonicalPath.fromString("/t;t/f;f/r;c"), null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(r);
    }

    @Test
    public void testDetypedResourceInFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Resource r = new Resource(CanonicalPath.fromString("/t;t/f;f/r;c"), null, new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(r, "{\"path\":\"/t;t/f;f/r;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(r, "{\"path\":\"/f/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricInFeed() throws Exception {
        Metric m = new Metric(CanonicalPath.fromString("/t;t/f;f/m;c"), null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);
    }

    @Test
    public void testDetypedMetricInFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Metric m = new Metric(CanonicalPath.fromString("/t;t/f;f/m;c"), null, new MetricType(
                CanonicalPath.fromString("/t;t/mt;k"), null), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(m, "{\"path\":\"/t;t/f;f/m;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(m, "{\"path\":\"/f/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testOperationType() throws Exception {
        OperationType ot = new OperationType(CanonicalPath.fromString("/t;t/rt;rt/ot;ot"), null);

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
                Resources.DataRole.connectionConfiguration,
                StructuredData.get().list().addIntegral(1).addIntegral(2).build(), null));
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

        testBlueprint(DataEntity.Blueprint.builder().withRole(ResourceTypes.DataRole.configurationSchema).withName("nd")
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

        testBlueprint(MetricType.Blueprint.builder(GAUGE).withId("mt").withName("nmt")
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

    private void testDetyped(Entity<?, ?> orig, String serialized) throws Exception {
        DetypedPathDeserializer.setCurrentEntityType(orig.getClass());
        mapper.addMixIn(CanonicalPath.class, TenantlessCanonicalPathMixin.class);

        Assert.assertEquals(orig, deserialize(serialized, orig.getClass()));
    }

    private <T extends Entity.Blueprint> void testBlueprint(T bl, BiConsumer<T, T> additionalTests) throws Exception {
        String ser = serialize(bl);

        @SuppressWarnings("unchecked")
        T dbl = (T) deserialize(ser, bl.getClass());

        Assert.assertEquals(bl.getId(), dbl.getId());
        Assert.assertEquals(bl.getIncomingRelationships(), dbl.getIncomingRelationships());
        Assert.assertEquals(bl.getOutgoingRelationships(), dbl.getOutgoingRelationships());
        Assert.assertEquals(bl.getName(), dbl.getName());
        Assert.assertEquals(bl.getProperties(), dbl.getProperties());

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
