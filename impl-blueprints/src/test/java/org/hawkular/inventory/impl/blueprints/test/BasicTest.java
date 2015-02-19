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
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDefinition;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.model.Version;
import org.hawkular.inventory.impl.blueprints.InventoryService;
import org.junit.After;
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
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.StreamSupport;

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
        setupData();
    }

    private void setupData() throws Exception {
        inventory.tenants().create("com.acme.tenant");
        inventory.tenants().get("com.acme.tenant").environments().create("production");
        inventory.tenants().get("com.acme.tenant").types()
                .create(new ResourceType.Blueprint("URL", new Version("1.0")));
        inventory.tenants().get("com.acme.tenant").metricDefinitions()
                .create(new MetricDefinition.Blueprint("ResponseTime", MetricUnit.MILLI_SECOND));
        inventory.tenants().get("com.acme.tenant").types().get("URL").metricDefinitions().add("ResponseTime");
        inventory.tenants().get("com.acme.tenant").environments().get("production").metrics()
                .create(new Metric.Blueprint(
                        new MetricDefinition("com.acme.tenant", "ResponseTime", MetricUnit.MILLI_SECOND),
                        "host1_ping_response"));
        inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .create(new Resource.Blueprint("host1", new ResourceType("com.acme.tenant", "URL", "1.0")));
        inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .get("host1").metrics().add("host1_ping_response");

        inventory.tenants().create("com.example.tenant");
        inventory.tenants().get("com.example.tenant").environments().create("test");
        inventory.tenants().get("com.example.tenant").types()
                .create(new ResourceType.Blueprint("Kachna", new Version("1.0")));
        inventory.tenants().get("com.example.tenant").types()
                .create(new ResourceType.Blueprint("Playroom", new Version("1.0")));
        inventory.tenants().get("com.example.tenant").metricDefinitions()
                .create(new MetricDefinition.Blueprint("Size", MetricUnit.BYTE));
        inventory.tenants().get("com.example.tenant").types().get("Playroom").metricDefinitions().add("Size");

        inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .create(new Metric.Blueprint(
                        new MetricDefinition("com.example.tenant", "Size", MetricUnit.BYTE),
                        "playroom1_size"));
        inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .create(new Metric.Blueprint(
                        new MetricDefinition("com.example.tenant", "Size", MetricUnit.BYTE),
                        "playroom2_size"));
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .create(new Resource.Blueprint("playroom1", new ResourceType("com.example.tenant", "Playroom", "1.0")));
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .create(new Resource.Blueprint("playroom2", new ResourceType("com.example.tenant", "Playroom", "1.0")));
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom1").metrics().add("playroom1_size");
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom2").metrics().add("playroom2_size");
    }

    @After
    public void teardown() throws Exception {
        inventory.close();
        graph.shutdown();
        deleteGraph();
    }

    private static void deleteGraph() throws Exception {
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
    public void testTenants() throws Exception {
        Function<String, Void> test = (id) -> {
            GraphQuery q = graph.query().has("type", "tenant").has("uid", id);

            Iterator<Vertex> evs = q.vertices().iterator();
            assert evs.hasNext();
            Vertex ev = evs.next();
            assert !evs.hasNext();

            Tenant t = inventory.tenants().get(id).entity();

            assert ev.getProperty("uid").equals(id);
            assert t.getId().equals(id);
            return null;
        };

        test.apply("com.acme.tenant");
        test.apply("com.example.tenant");

        GraphQuery query = graph.query().has("type", "tenant");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testEnvironments() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph)
                    .V().has("type", "tenant").has("uid", tenantId).out("contains")
                    .has("type", "environment").has("uid", id).cast(Vertex.class);

            Iterator<Vertex> envs = q.iterator();
            assert envs.hasNext();
            envs.next();
            assert !envs.hasNext();

            //query, we should get the same results
            Environment env = inventory.tenants().get(tenantId).environments().get(id).entity();
            assert env.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production");
        test.apply("com.example.tenant", "test");

        GraphQuery query = graph.query().has("type", "environment");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testResourceTypes() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "resourceType").has("uid", id)
                    .has("version", "1.0").cast(Vertex.class);

            assert q.hasNext();

            ResourceType rt = inventory.tenants().get(tenantId).types().get(id).entity();
            assert rt.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL");
        test.apply("com.example.tenant", "Kachna");
        test.apply("com.example.tenant", "Playroom");

        GraphQuery query = graph.query().has("type", "resourceType");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 3;
    }

    @Test
    public void testMetricDefinitions() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "metricDefinition")
                    .has("uid", id).cast(Vertex.class);

            assert q.hasNext();

            MetricDefinition md = inventory.tenants().get(tenantId).metricDefinitions().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "ResponseTime");
        test.apply("com.example.tenant", "Size");

        GraphQuery query = graph.query().has("type", "metricDefinition");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testMetricDefsLinkedToResourceTypes() throws Exception {
        TriFunction<String, String, String, Void> test = (tenantId, resourceTypeId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "resourceType")
                    .has("uid", resourceTypeId).out("owns").has("type", "metricDefinition").has("uid", id)
                    .cast(Vertex.class);

            assert q.hasNext();

            MetricDefinition md = inventory.tenants().get(tenantId).types().get(resourceTypeId)
                    .metricDefinitions().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL", "ResponseTime");
        test.apply("com.example.tenant", "Playroom", "Size");
    }

    @Test
    public void testMetrics() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, metricDefId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "environment").has("uid", environmentId)
                    .out("contains").has("type", "metric").has("uid", id).as("metric").in("defines")
                    .has("type", "metricDefinition").has("uid", metricDefId).back("metric").cast(Vertex.class);

            assert q.hasNext();

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).metrics()
                    .getAll(Defined.by(new MetricDefinition(tenantId, metricDefId)), With.id(id)).entities().iterator()
                    .next();
            assert m.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "ResponseTime", "host1_ping_response");
        test.apply("com.example.tenant", "test", "Size", "playroom1_size");
        test.apply("com.example.tenant", "test", "Size", "playroom2_size");

        GraphQuery query = graph.query().has("type", "metric");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 3;
    }

    @Test
    public void testResources() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceTypeId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "environment").has("uid", environmentId)
                    .out("contains").has("type", "resource").has("uid", id).as("resource").in("defines")
                    .has("type", "resourceType").has("uid", resourceTypeId).back("resource").cast(Vertex.class);

            assert q.hasNext();

            Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                    .getAll(Defined.by(new ResourceType(tenantId, resourceTypeId, "1.0")), With.id(id)).entities()
                    .iterator().next();
            assert r.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "URL", "host1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom2");

        GraphQuery query = graph.query().has("type", "resource");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 3;
    }

    @Test
    public void testAssociateMetricWithResource() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceId, metricId) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "environment").has("uid", environmentId)
                    .out("contains").has("type", "resource").has("uid", resourceId).out("owns")
                    .has("type", "metric").has("uid", metricId).cast(Vertex.class);

            assert q.hasNext();

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                    .get(resourceId).metrics().get(metricId).entity();
            assert metricId.equals(m.getId());

            return null;
        };

        test.apply("com.acme.tenant", "production", "host1", "host1_ping_response");
        test.apply("com.example.tenant", "test", "playroom1", "playroom1_size");
        test.apply("com.example.tenant", "test", "playroom2", "playroom2_size");
    }

    @Test
    public void queryMultipleTenants() throws Exception {
        Set<Tenant> tenants = inventory.tenants().getAll().entities();

        assert tenants.size() == 2;
    }

    @Test
    public void queryMultipleEnvironments() throws Exception {
        Set<Environment> environments = inventory.tenants().getAll().environments().getAll().entities();

        assert environments.size() == 2;
    }

    @Test
    public void queryMultipleResourceTypes() throws Exception {
        Set<ResourceType> types = inventory.tenants().getAll().types().getAll().entities();
        assert types.size() == 3;
    }

    @Test
    public void queryMultipleMetricDefs() throws Exception {
        Set<MetricDefinition> types = inventory.tenants().getAll().metricDefinitions().getAll().entities();
        assert types.size() == 2;
    }

    @Test
    public void queryMultipleResources() throws Exception {
        Set<Resource> rs = inventory.tenants().getAll().environments().getAll().resources().getAll().entities();
        assert rs.size() == 3;
    }

    @Test
    public void queryMultipleMetrics() throws Exception {
        Set<Metric> ms = inventory.tenants().getAll().environments().getAll().metrics().getAll().entities();
        assert ms.size() == 3;
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

    private interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    private interface TetraFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w);
    }
}
