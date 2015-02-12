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
package org.hawkular.inventory.impl.blueprints.test;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.GraphQuery;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.wrapped.WrappedGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDefinition;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Version;
import org.hawkular.inventory.impl.blueprints.InventoryService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Test some basic functionality
 *
 * @author Lukas Krejci
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BasicTest {

    TransactionalGraph graph;
    InventoryService inventory;

    @Before
    public void setup() throws Exception {
        graph = new DummyTransactionalGraph(new TinkerGraph(new File("./__tinker.graph").getAbsolutePath()));
        inventory = new InventoryService(graph);
    }

    @After
    public void teardown() throws Exception {
        inventory.close();
        graph.shutdown();
    }

    @AfterClass
    public static void deleteGraph() throws Exception {
        Files.walkFileTree(Paths.get("./", "__tinker.graph"), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    public void stage1_testTenantInitialized() throws Exception {
        inventory.tenants().create("com.acme.tenant");
        GraphQuery query = graph.query().has("type", "tenant").has("uid", "com.acme.tenant");

        Iterator<Vertex> tenants = query.vertices().iterator();

        assert tenants.hasNext();
        Vertex t = tenants.next();
        assert !tenants.hasNext();

        //query, we should get the same results
        inventory.tenants().get("com.acme.tenant");
        tenants = query.vertices().iterator();
        assert tenants.hasNext();
        t = tenants.next();
        assert !tenants.hasNext();
    }

    @Test
    public void stage2_testEnvironmentInitialized() throws Exception {
        inventory.tenants().get("com.acme.tenant").environments().create("production");

        GraphQuery tenantQuery = graph.query().has("type", "tenant").has("uid", "com.acme.tenant");

        Iterator<Vertex> tenants = tenantQuery.vertices().iterator();
        assert tenants.hasNext();
        Vertex t = tenants.next();
        assert !tenants.hasNext();


        GremlinPipeline<Vertex, Vertex> envQuery = new GremlinPipeline<Vertex, Vertex>().start(t).out("contains")
                .has("type", "environment").has("uid", "production").cast(Vertex.class);

        Iterator<Vertex> envs = envQuery.iterator();
        assert envs.hasNext();
        Vertex e = envs.next();
        assert !envs.hasNext();

        //query, we should get the same results
        inventory.tenants().get("com.acme.tenant").environments().get("production");

        tenants = tenantQuery.vertices().iterator();
        assert tenants.hasNext();
        t = tenants.next();
        assert !tenants.hasNext();

        //reset, so that we can query again
        envQuery.reset();
        envQuery.setStarts(Arrays.asList(t));

        envs = envQuery.iterator();
        assert envs.hasNext();
        e = envs.next();
        assert !envs.hasNext();
    }

    @Test
    public void stage3_testAddResourceType() throws Exception {

        inventory.tenants().get("com.acme.tenant").types()
                .create(new ResourceType.Blueprint("URL", new Version("1.0")));

        GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                .has("uid", "com.acme.tenant").out("contains").has("type", "resourceType").has("uid", "URL")
                .has("version", "1.0").cast(Vertex.class);

        assert q.hasNext();
    }

    @Test
    public void stage4_testAddMetricDefinition() throws Exception {
        inventory.tenants().get("com.acme.tenant").metricDefinitions()
                .create(new MetricDefinition.Blueprint("ResponseTime", MetricUnit.MILLI_SECOND));

        GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                .has("uid", "com.acme.tenant").out("contains").has("type", "metricDefinition")
                .has("uid", "ResponseTime").has("unit", "ms").cast(Vertex.class);

        assert q.hasNext();
    }

    @Test
    public void stage5_testAddMetricDefToResourceType() throws Exception {
        inventory.tenants().get("com.acme.tenant").types().get("URL").metricDefinitions().add("ResponseTime");

        GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                .has("uid", "com.acme.tenant").out("contains").has("type", "resourceType")
                .has("uid", "URL").out("owns").has("type", "metricDefinition").has("uid", "ResponseTime")
                .has("unit", "ms").cast(Vertex.class);

        assert q.hasNext();
    }

    @Test
    public void stage6_testAddMetric() throws Exception {
        inventory.tenants().get("com.acme.tenant").environments().get("production").metrics()
                .create(new Metric.Blueprint(
                        new MetricDefinition("com.acme.tenant", "ResponseTime", MetricUnit.MILLI_SECOND),
                        "host1_ping_response"));

        GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                .has("uid", "com.acme.tenant").out("contains").has("type", "environment").has("uid", "production")
                .out("contains").has("type", "metric").has("uid", "host1_ping_response").as("metric").in("defines")
                .has("type", "metricDefinition").has("uid", "ResponseTime").back("metric").cast(Vertex.class);

        assert q.hasNext();
    }

    @Test
    public void stage7_testAddResource() throws Exception {
        inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .create(new Resource.Blueprint("host1", new ResourceType("com.acme.tenant", "URL", "1.0")));

        GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                .has("uid", "com.acme.tenant").out("contains").has("type", "environment").has("uid", "production")
                .out("contains").has("type", "resource").has("uid", "host1").as("resource").in("defines")
                .has("type", "resourceType").has("uid", "URL").back("resource").cast(Vertex.class);

        assert q.hasNext();
    }

    @Test
    public void stage8_testAssociateMetricWithResource() throws Exception {
        Metrics.ReadRelate ms = inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .get("host1").metrics();

        try {
            ms.get("host1_ping_response");
        } catch (IllegalArgumentException ignored) {
            //expected
        }


        ms.add("host1_ping_response");

        Metric m = ms.get("host1_ping_response").entity();

        assert "host1_ping_response".equals(m.getId());
        assert "production".equals(m.getEnvironmentId());
        assert "com.acme.tenant".equals(m.getTenantId());
        assert "ResponseTime".equals(m.getDefinition().getId());
    }
/*
    @Test
    public void testAddGetOne() throws Exception {
        InventoryService inv = new InventoryService(graph);

        InventoryServiceOld inventory = new InventoryServiceOld(conn);

        Resource resource = new Resource();
        resource.setType(ResourceType.URL);
        resource.addParameter("url","http://hawkular.org");
        String id = inventory.addResource("test",resource);

        assert id != null;
        assert !id.isEmpty();

        Resource result = inventory.getResource("test",id);
        assert result != null;
        assert result.getId()!=null;
        assert result.getId().equals(id);

        List<Resource> resources = inventory.getResourcesForType("test",ResourceType.URL);
        assert resources != null;
        assert !resources.isEmpty();
        assert resources.size() == 1 : "Found " + resources.size() + " entries, but expected 1";
        assert resources.get(0).equals(result);

    }

    @Test
    public void testAddGetBadTenant() throws Exception {

        InventoryServiceOld inventory = new InventoryServiceOld(conn);

        Resource resource = new Resource();
        resource.setType(ResourceType.URL);
        resource.addParameter("url","http://hawkular.org");
        String id = inventory.addResource("test2",resource);

        Resource result = inventory.getResource("bla",id);
        assert result == null;

    }

    @Test
    public void testAddMetricsToResource() throws Exception {

        InventoryServiceOld inventory = new InventoryServiceOld(conn);

        Resource resource = new Resource();
        resource.setType(ResourceType.URL);
        resource.addParameter("url","http://hawkular.org");
        String tenant = "test3";
        String id = inventory.addResource(tenant,resource);


        inventory.addMetricToResource(tenant,id,"vm.user_load");
        inventory.addMetricToResource(tenant,id,"vm.system_load");
        inventory.addMetricToResource(tenant,id,"vm.size");
        List<MetricDefinition> definitions = new ArrayList<>(2);
        definitions.add(new MetricDefinition("cpu.count1"));
        definitions.add(new MetricDefinition("cpu.count15"));
        MetricDefinition def = new MetricDefinition("cpu.load.42", MetricUnit.NONE);
        def.setDescription("The question, you know :-)");
        definitions.add(def);
        inventory.addMetricsToResource(tenant, id, definitions );

        List<MetricDefinition> metrics = inventory.listMetricsForResource(tenant,id);

        assert metrics.size()==6;

        MetricDefinition updateDef = new MetricDefinition("vm.size");
        updateDef.setUnit(MetricUnit.BYTE);
        updateDef.setDescription("How much memory does the vm use?");

        boolean updated = inventory.updateMetric(tenant,id,updateDef);
        assert updated;

        MetricDefinition vmDef = inventory.getMetric(tenant,id,"vm.size");
        assertNotNull(vmDef);
        assertEquals("vm.size", vmDef.getName());
        assertEquals(MetricUnit.BYTE, vmDef.getUnit());
    }
*/

    private static class DummyTransactionalGraph extends WrappedGraph<TinkerGraph> implements TransactionalGraph {

        public DummyTransactionalGraph(TinkerGraph baseGraph) {
            super(baseGraph);
        }

        @Override
        public void stopTransaction(Conclusion conclusion) {
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }
}
