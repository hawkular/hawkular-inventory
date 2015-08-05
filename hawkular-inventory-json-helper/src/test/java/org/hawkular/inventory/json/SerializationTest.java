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
package org.hawkular.inventory.json;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.junit.Assert;
import org.junit.BeforeClass;
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

    private static ObjectMapper mapper;

    @BeforeClass
    public static void setup() {
        JsonFactory f = new JsonFactory();

        mapper = new ObjectMapper(f);

        InventoryJacksonConfig.configure(mapper);
    }

    @Test
    public void testCanonicalPath() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(Resource.class);
        test(CanonicalPath.fromString("/t;t/e;e/r;r"));
    }

    @Test
    public void testRelativePath() throws Exception {
        PathDeserializer.setCurrentEntityType(Metric.class);
        PathDeserializer.setCurrentRelativePathOrigin(CanonicalPath.fromString("/t;t/e;e/r;r"));
        test(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"), Metric.class)
        );

        //the test above doesn't test for deserializing a de-typed path.
        RelativePath rp = deserialize("\"../g\"", RelativePath.class);
        Assert.assertEquals(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"),
                Metric.class), rp);
    }

    @Test
    public void testTenant() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;c"));
        PathDeserializer.setCurrentEntityType(Tenant.class);

        Tenant t = new Tenant(CanonicalPath.fromString("/t;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(t);

        //a detyped variant should work, too
        Assert.assertEquals(t, deserialize("{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}",
                Tenant.class));
    }

    @Test
    public void testEnvironment() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(Environment.class);

        Environment env = new Environment(CanonicalPath.fromString("/t;t/e;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(env);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(env, deserialize("{\"path\":\"/t;t/e;c\",\"properties\":{\"a\":\"b\"}}",
                Environment.class));
        //a detyped variant should work, too
        Assert.assertEquals(env, deserialize("{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}", Environment.class));
        Assert.assertEquals(env, deserialize("{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}", Environment.class));
    }

    @Test
    public void testResourceType() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(ResourceType.class);

        ResourceType rt = new ResourceType(CanonicalPath.fromString("/t;t/rt;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(rt);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(rt, deserialize("{\"path\":\"/t;t/rt;c\",\"properties\":{\"a\":\"b\"}}",
                ResourceType.class));
        //a detyped variant should work, too
        Assert.assertEquals(rt, deserialize("{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}",
                ResourceType.class));
        Assert.assertEquals(rt, deserialize("{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}",
                ResourceType.class));
    }

    @Test
    public void testMetricType() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(MetricType.class);

        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), MetricUnit.BYTE, MetricDataType.GAUGE,
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(mt);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(mt, deserialize("{\"path\":\"/t;t/mt;c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTE\"}",
                MetricType.class));
        //a detyped variant should work, too
        Assert.assertEquals(mt, deserialize("{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTE\"}",
                MetricType.class));
        Assert.assertEquals(mt, deserialize("{\"path\":\"/c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTE\"}",
                MetricType.class));
    }

    @Test
    public void testFeed() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(Feed.class);

        Feed f = new Feed(CanonicalPath.fromString("/t;t/e;e/f;c"),
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(f);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(f,
                deserialize("{\"path\":\"/t;t/e;e/f;c\",\"properties\":{\"a\":\"b\"}}", Feed.class));
        //a detyped variant should work, too
        Assert.assertEquals(f, deserialize("{\"path\":\"/t;t/e/c\",\"properties\":{\"a\":\"b\"}}", Feed.class));
        Assert.assertEquals(f, deserialize("{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}", Feed.class));
    }

    @Test
    public void testResourceInEnvironment() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(Resource.class);

        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/r;c"), new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(r);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(r,
                deserialize("{\"path\":\"/t;t/e;e/r;c\",\"properties\":{\"a\":\"b\"}}", Resource.class));
        //a detyped variant should work, too
        Assert.assertEquals(r, deserialize("{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}", Resource.class));
    }

    @Test
    public void testMetricInEnvironment() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(Metric.class);

        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/m;c"), new MetricType(
                CanonicalPath.fromString("/t;t/mt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(m,
                deserialize("{\"path\":\"/t;t/e;e/m;c\",\"properties\":{\"a\":\"b\"}}", Metric.class));
        //a detyped variant should work, too
        Assert.assertEquals(m, deserialize("{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}", Metric.class));
    }

    @Test
    public void testResourceInFeed() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(Resource.class);

        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/f;f/r;c"), new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(r);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(r,
                deserialize("{\"path\":\"/t;t/e;e/f;f/r;c\",\"properties\":{\"a\":\"b\"}}", Resource.class));
        //a detyped variant should work, too
        Assert.assertEquals(r, deserialize("{\"path\":\"/e/f/c\",\"properties\":{\"a\":\"b\"}}", Resource.class));
    }

    @Test
    public void testMetricInFeed() throws Exception {
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        PathDeserializer.setCurrentEntityType(Metric.class);

        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/f;f/m;c"), new MetricType(
                CanonicalPath.fromString("/t;t/mt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);

        //test deserialization with a full path instead of the tenant-less which was tested by the above
        Assert.assertEquals(m,
                deserialize("{\"path\":\"/t;t/e;e/f;f/m;c\",\"properties\":{\"a\":\"b\"}}", Metric.class));
        //a detyped variant should work, too
        Assert.assertEquals(m, deserialize("{\"path\":\"/e/f/c\",\"properties\":{\"a\":\"b\"}}", Metric.class));
    }

    private void test(Object o) throws Exception {
        Class<?> cls = o.getClass();

        Object o2 = deserialize(serialize(o), cls);

        Assert.assertEquals(o, o2);
    }

    private static String serialize(Object object) throws IOException {
        StringWriter out = new StringWriter();

        JsonGenerator gen = mapper.getFactory().createGenerator(out);

        gen.writeObject(object);

        gen.close();

        out.close();

        return out.toString();
    }

    private static <T> T deserialize(String json, Class<T> type) throws Exception {
        JsonParser parser = mapper.getFactory().createParser(json);

        return parser.readValueAs(type);
    }
}
