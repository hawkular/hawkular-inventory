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
package org.hawkular.inventory.impl.tinkerpop.test;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.GraphQuery;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.wrapped.WrappedGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.impl.tinkerpop.InventoryService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;

/**
 * Test some basic functionality
 *
 * @author Lukas Krejci
 * @author Jirka Kremser
 */
public class BasicTest {

    TransactionalGraph graph;
    InventoryService inventory;

    @Before
    public void setup() throws Exception {
        Properties ps = new Properties();
        try (FileInputStream f = new FileInputStream(System.getProperty("graph.config"))) {
            ps.load(f);
        }

        Configuration config = Configuration.builder().withFeedIdStrategy(
                new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
                .withConfiguration(ps)
                .build();

        inventory = new InventoryService();
        inventory.initialize(config);

        graph = inventory.getGraph();

        try {
            inventory.tenants().delete("com.acme.tenant");
        } catch (Exception ignored) {
        }

        try {
            inventory.tenants().delete("com.example.tenant");
        } catch (Exception ignored) {
        }

        try {
            inventory.tenants().delete("perf0");
        } catch (Exception ignored) {
        }

        setupData();
    }

    private void setupData() throws Exception {
        //noinspection AssertWithSideEffects
        assert inventory.tenants()
                .create(Tenant.Blueprint.builder().withId("com.acme.tenant").withProperty("kachny", "moc").build())
                .entity().getId().equals("com.acme.tenant");
        assert inventory.tenants().get("com.acme.tenant").environments().create(new Environment.Blueprint("production"))
                .entity().getId().equals("production");
        assert inventory.tenants().get("com.acme.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("URL", "1.0")).entity().getId().equals("URL");
        assert inventory.tenants().get("com.acme.tenant").metricTypes()
                .create(new MetricType.Blueprint("ResponseTime", MetricUnit.MILLI_SECOND)).entity().getId()
                .equals("ResponseTime");

        inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL").metricTypes().associate("ResponseTime");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessMetrics()
                .create(new Metric.Blueprint("ResponseTime", "host1_ping_response")).entity().getId()
                .equals("host1_ping_response");
        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                .create(new Resource.Blueprint("host1", "URL")).entity()
                .getId().equals("host1");
        inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                .get("host1").metrics().associate("host1_ping_response");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds()
                .create(new Feed.Blueprint("feed1", null)).entity().getId().equals("feed1");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource1", "URL")).entity().getId()
                .equals("feedResource1");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource2", "URL")).entity().getId()
                .equals("feedResource2");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource3", "URL")).entity().getId()
                .equals("feedResource3");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .metrics().create(new Metric.Blueprint("ResponseTime", "feedMetric1")).entity().getId()
                .equals("feedMetric1");

        assert inventory.tenants().create(new Tenant.Blueprint("com.example.tenant")).entity().getId()
                .equals("com.example.tenant");
        assert inventory.tenants().get("com.example.tenant").environments().create(new Environment.Blueprint("test"))
                .entity().getId().equals("test");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Kachna", "1.0")).entity().getId().equals("Kachna");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Playroom", "1.0")).entity().getId().equals("Playroom");
        assert inventory.tenants().get("com.example.tenant").metricTypes()
                .create(new MetricType.Blueprint("Size", MetricUnit.BYTE)).entity().getId().equals("Size");
        inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").metricTypes().associate("Size");

        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .create(new Metric.Blueprint("Size", "playroom1_size")).entity().getId().equals("playroom1_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .create(new Metric.Blueprint("Size", "playroom2_size")).entity().getId().equals("playroom2_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .create(new Resource.Blueprint("playroom1", "Playroom")).entity().getId().equals("playroom1");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .create(new Resource.Blueprint("playroom2", "Playroom")).entity().getId().equals("playroom2");

        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom1").metrics().associate("playroom1_size");
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().associate("playroom2_size");

        // some ad-hoc relationships
        Environment test = inventory.tenants().get("com.example.tenant").environments().get("test").entity();
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .linkWith("yourMom", test, null);
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.incoming)
                .linkWith("IamYourFather", test, null);
    }

    private void teardownData() throws Exception {
        Tenant t = new Tenant("com.example.tenant");
        Environment e = new Environment(t.getId(), "test");
        MetricType sizeType = new MetricType(t.getId(), "Size");
        ResourceType playRoomType = new ResourceType(t.getId(), "Playroom", "1.0");
        ResourceType kachnaType = new ResourceType(t.getId(), "Kachna", "1.0");
        Resource playroom1 = new Resource(t.getId(), e.getId(), null, "playroom1", playRoomType);
        Resource playroom2 = new Resource(t.getId(), e.getId(), null, "playroom2", playRoomType);
        Metric playroom1Size = new Metric(t.getId(), e.getId(), null, "playroom1_size", sizeType);
        Metric playroom2Size = new Metric(t.getId(), e.getId(), null, "playroom2_size", sizeType);

        inventory.inspect(e).feedlessMetrics().delete(playroom2Size.getId());
        assertDoesNotExist(playroom2Size);
        assertExists(t, e, sizeType, playRoomType, kachnaType, playroom1, playroom2, playroom1Size);

        inventory.inspect(t).resourceTypes().delete(kachnaType.getId());
        assertDoesNotExist(kachnaType);
        assertExists(t, e, sizeType, playRoomType, playroom1, playroom2, playroom1Size);

        try {
            inventory.inspect(t).metricTypes().delete(sizeType.getId());
            Assert.fail("Deleting a metric type which references some metrics should not be possible.");
        } catch (IllegalArgumentException ignored) {
            //good
        }

        inventory.inspect(e).feedlessMetrics().delete(playroom1Size.getId());
        assertDoesNotExist(playroom1Size);
        assertExists(t, e, sizeType, playRoomType, playroom1, playroom2);

        inventory.inspect(t).metricTypes().delete(sizeType.getId());
        assertDoesNotExist(sizeType);
        assertExists(t, e, playRoomType, playroom1, playroom2);

        try {
            inventory.inspect(t).resourceTypes().delete(playRoomType.getId());
            Assert.fail("Deleting a resource type which references some resources should not be possible.");
        } catch (IllegalArgumentException ignored) {
            //good
        }

        inventory.inspect(e).feedlessResources().delete(playroom1.getId());
        assertDoesNotExist(playroom1);
        assertExists(t, e, sizeType, playRoomType, playroom2);

        inventory.tenants().delete(t.getId());
        assertDoesNotExist(t);
        assertDoesNotExist(e);
        assertDoesNotExist(playRoomType);
        assertDoesNotExist(playroom2);
    }

    private void assertDoesNotExist(Entity e) {
        try {
            inventory.inspect(e, ResolvableToSingle.class).entity();
            Assert.fail(e + " should have been deleted");
        } catch (EntityNotFoundException ignored) {
            //good
        }
    }

    private void assertExists(Entity e) {
        try {
            inventory.inspect(e, ResolvableToSingle.class);
        } catch (EntityNotFoundException ignored) {
            Assert.fail(e + " should have been present in the inventory.");
        }
    }

    private void assertExists(Entity... es) {
        Stream.of(es).forEach(this::assertExists);
    }

    @After
    public void teardown() throws Exception {
        try {
            teardownData();
        } finally {
            inventory.close();
            deleteGraph();
        }
    }

    private static void deleteGraph() throws Exception {
        Path path = Paths.get("./", "__tinker.graph");

        if (!path.toFile().exists()) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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
            GraphQuery q = graph.query().has("__type", "tenant").has("__eid", id);

            Iterator<Vertex> evs = q.vertices().iterator();
            assert evs.hasNext();
            Vertex ev = evs.next();
            assert !evs.hasNext();

            Tenant t = inventory.tenants().get(id).entity();

            assert ev.getProperty("__eid").equals(id);
            assert t.getId().equals(id);
            return null;
        };

        test.apply("com.acme.tenant");
        test.apply("com.example.tenant");

        GraphQuery query = graph.query().has("__type", "tenant");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }
    @Test
    public void testEntitiesByRelationships() throws Exception {
        Function<Integer, Function<String, Function<String, Function<Integer, Function<String,
                Function<ResolvableToMany<?>, Consumer<ResolvableToMany<?>>>>>>>>
                testHelper = (numberOfParents -> parentType -> edgeLabel -> numberOfKids -> childType ->
                multipleParents -> multipleChildren -> {
                    GremlinPipeline<Graph, Vertex> q1 = new GremlinPipeline<Graph, Vertex>(graph)
                            .V().has("__type", parentType).cast(Vertex.class);
                    Iterator<Vertex> parentIterator = q1.iterator();

                    GremlinPipeline<Graph, Vertex> q2 = new GremlinPipeline<Graph, Vertex>(graph)
                            .V().has("__type", parentType).out(edgeLabel).has("__type", childType)
                            .cast(Vertex.class);
                    Iterator<Vertex> childIterator = q2.iterator();

                    Iterator<?> multipleParentIterator = multipleParents.entities().iterator();
                    Iterator<?> multipleChildrenIterator = multipleChildren.entities().iterator();

                    for (int i = 0; i < numberOfParents; i++) {
                        assert parentIterator.hasNext() : "There must be exactly " + numberOfParents + " " +
                                parentType + "s " + "that have outgoing edge labeled with " + edgeLabel + ". Gremlin " +
                                "query returned only " + i;
                        assert multipleParentIterator.hasNext() : "There must be exactly " + numberOfParents + " " +
                                parentType + "s that have outgoing edge labeled with " + edgeLabel + ". Tested API " +
                                "returned only " + i;
                        parentIterator.next();
                        multipleParentIterator.next();
                    }
                    assert !parentIterator.hasNext() : "There must be " + numberOfParents + " " + parentType +
                            "s. Gremlin query returned more than " + numberOfParents;
                    assert !multipleParentIterator.hasNext() : "There must be " + numberOfParents + " " + parentType +
                            "s. Tested API returned more than " + numberOfParents;

                    for (int i = 0; i < numberOfKids; i++) {
                        assert childIterator.hasNext() : "There must be exactly " + numberOfKids + " " + childType +
                                "s that are directly under " + parentType + " connected with " + edgeLabel +
                                ". Gremlin query returned only " + i;
                        assert multipleChildrenIterator.hasNext();
                        childIterator.next();
                        multipleChildrenIterator.next();
                    }
                    assert !childIterator.hasNext() : "There must be exactly " + numberOfKids + " " + childType + "s";
                    assert !multipleChildrenIterator.hasNext();
                });

        ResolvableToMany parents = inventory.tenants().getAll(Related.by("contains"));
        ResolvableToMany kids = inventory.tenants().getAll().environments().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("tenant").apply("contains").apply(2).apply("environment").apply(parents).accept(kids);

        kids = inventory.tenants().getAll().resourceTypes().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("tenant").apply("contains").apply(3).apply("resourceType").apply(parents)
                .accept(kids);

        kids = inventory.tenants().getAll().metricTypes().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("tenant").apply("contains").apply(2).apply("metricType").apply(parents)
                .accept(kids);

        parents = inventory.tenants().getAll().environments().getAll(Related.by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().feedlessMetrics().getAll(
                Related.asTargetBy("contains"));
        testHelper.apply(2).apply("environment").apply("contains").apply(3).apply("metric").apply(parents).
                accept(kids);

        kids = inventory.tenants().getAll().environments().getAll().feedlessResources().getAll(
                Related.asTargetBy("contains"));
        testHelper.apply(2).apply("environment").apply("contains").apply(3).apply("resource").apply(parents).accept
                (kids);

        parents = inventory.tenants().getAll().environments().getAll(Related.by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().allMetrics().getAll(
                Related.asTargetBy("defines"));
        testHelper.apply(2).apply("metricType").apply("defines").apply(4).apply("metric").apply(parents)
                .accept(kids);
    }

    @Test
    public void testRelationshipServiceNamed1() throws Exception {
        Set<Relationship> contains = inventory.tenants().getAll().relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getId())
                && "URL".equals(rel.getTarget().getId()))
                : "Tenant 'com.acme.tenant' must contain ResourceType 'URL'.";
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getId())
                && "production" .equals(rel.getTarget().getId()))
                : "Tenant 'com.acme.tenant' must contain Environment 'production'.";
        assert contains.stream().anyMatch(rel -> "com.example.tenant".equals(rel.getSource().getId())
                && "Size".equals(rel.getTarget().getId()))
                : "Tenant 'com.example.tenant' must contain MetricType 'Size'.";

        contains.forEach((r) -> {
            assert r.getId() != null;
        });
    }

    @Test
    public void testRelationshipServiceNamed2() throws Exception {
        Set<Relationship> contains = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "playroom1" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom1'.";
        assert contains.stream().anyMatch(rel -> "playroom2" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom2'.";
        assert contains.stream().anyMatch(rel -> "playroom2_size" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom2_size'.";
        assert contains.stream().anyMatch(rel -> "playroom1_size" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom1_size'.";
        assert contains.stream().allMatch(rel -> !"production".equals(rel.getSource().getId()))
                : "Environment 'production' cant be the source of these relationships.";

        contains.forEach((r) -> {
            assert r.getId() != null;
        });
    }

    @Test
    public void testRelationshipServiceLinkedWith() throws Exception {
        Set<Relationship> rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .feedlessResources().get("playroom2").metrics().get("playroom2_size")
                .relationships(Relationships.Direction.outgoing).named("yourMom").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getTarget().getId()) : "Target of relationship 'yourMom' should " +
                "be the 'test' environment";

        rels = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.both)
                .named("IamYourFather").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getSource().getId()) : "Source of relationship 'IamYourFather' " +
                "should be the 'test' environment";
    }

    @Test
    public void testRelationshipServiceLinkedWithAndDelete() throws Exception {
        Tenant tenant = inventory.tenants().get("com.example.tenant").entity();
        Relationship link = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .feedlessResources().get("host1").relationships(Relationships.Direction.incoming)
                .linkWith("crossTenantLink", tenant, null).entity();

        assert inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.outgoing)
                .named("crossTenantLink").entities().size() == 1 : "Relation 'crossTenantLink' was not found.";
        // delete the relationship
        inventory.tenants().get("com.example.tenant").relationships(/*defaults to outgoing*/).delete(link.getId());
        assert inventory.tenants().get("com.example.tenant").relationships()
                .named("crossTenantLink").entities().size() == 0 : "Relation 'crossTenantLink' was found.";

        // try deleting again
        try {
            inventory.tenants().get("com.example.tenant").relationships(/*defaults to outgoing*/).delete(link.getId());
            assert true : "It shouldn't be possible to delete the same relationship twice";
        } catch (RelationNotFoundException e) {
            // good
        }
    }

    @Test
    public void testRelationshipServiceUpdateRelationship1() throws Exception {
        final String someKey = "k3y";
        final String someValue = "v4lu3";
        Relationship rel1 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .named("yourMom").entities().iterator().next();
        assert null == rel1.getProperties().get(someKey) : "There should not be any property with key 'k3y'";

        Relationship rel2 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .named("yourMom").entities().iterator().next();
        assert rel1.getId().equals(rel2.getId()) && null == rel2.getProperties().get(someKey) : "There should not be" +
                " any property with key 'k3y'";

        // persist the change
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .update(rel1.getId(), Relationship.Update.builder().withProperty(someKey, someValue).build());

        Relationship rel3 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .named("yourMom").entities().iterator().next();
        assert rel1.getId().equals(rel3.getId()) && someValue.equals(rel3.getProperties().get(someKey))
                : "There should be the property with key 'k3y' and value 'v4lu3'";
    }

    @Test
    public void testRelationshipServiceGetAllFilters() throws Exception {
        Set<Relationship> rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(Relationships.Direction.outgoing).getAll(RelationWith.name("contains")).entities();
        assert rels != null && rels.size() == 4 : "There should be 4 relationships conforming the filters";
        assert rels.stream().anyMatch(rel -> "playroom2_size".equals(rel.getTarget().getId()));
        assert rels.stream().anyMatch(rel -> "playroom1".equals(rel.getTarget().getId()));


        rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(Relationships.Direction.outgoing).getAll(RelationWith.name("contains"), RelationWith
                        .targetOfType(Metric.class)).entities();
        assert rels != null && rels.size() == 2 : "There should be 2 relationships conforming the filters";
        assert rels.stream().allMatch(rel -> Metric.class.equals(rel.getTarget().getClass())) : "The type of all the " +
                "targets should be the 'Metric'";


        rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(Relationships.Direction.incoming).getAll(RelationWith.name("contains")).entities();

        assert rels != null && rels.size() == 1 : "There should be just 1 relationship conforming the filters";
        assert "com.example.tenant".equals(rels.iterator().next().getSource().getId()) : "Tenant 'com.example" +
                ".tenant' was not found";


        rels = inventory.tenants().getAll().relationships().named
                (contains).environments().getAll().relationships().getAll(RelationWith
                .properties("label", "contains"), RelationWith.targetsOfTypes(Resource.class, Metric.class))
                .entities();
        assert rels != null && rels.size() == 6 : "There should be 6 relationships conforming the filters";
        assert rels.stream().allMatch(rel -> "test".equals(rel.getSource().getId())
                || "production".equals(rel.getSource().getId())) : "Source should be either 'test' or 'production'";
        assert rels.stream().allMatch(rel -> Resource.class.equals(rel.getTarget().getClass())
                || Metric.class.equals(rel.getTarget().getClass())) : "Target should be either a metric or a " +
                "resource";
    }

    @Test
    public void testRelationshipServiceGetAllFiltersWithSubsequentCalls() throws Exception {
        Metric metric = inventory.tenants().getAll().relationships().named
                (contains).environments().getAll().relationships().getAll(RelationWith
                .properties("label", "contains"), RelationWith.targetsOfTypes(Resource.class, Metric.class)).metrics
                ().get("playroom1_size").entity();
        assert "playroom1_size".equals(metric.getId()) : "Metric playroom1_size was not found using various relation " +
                "filters";

        try {
            inventory.tenants().getAll().relationships().named
                    (contains).environments().getAll().relationships().getAll(RelationWith
                    .properties("label", "contains"), RelationWith.targetsOfTypes(Resource.class)).metrics
                    ().get("playroom1_size").entity();
            assert false : "this code should not be reachable. There should be no metric reachable under " +
                    "'RelationWith.targetsOfTypes(Resource.class))' filter";
        } catch (EntityNotFoundException e) {
            // good
        }
    }

    @Test
    public void testRelationshipServiceCallChaining() throws Exception {
        MetricType metricType = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named("owns").metricTypes().get("Size").entity();// not empty
        assert "Size".equals(metricType.getId()) : "ResourceType[Playroom] -owns-> MetricType[Size] was not found";

        try {
            inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships()
                    .named("contains").metricTypes().get("Size").entity();
            assert false : "There is no such an entity satisfying the query, this code shouldn't be reachable";
        } catch (EntityNotFoundException e) {
            // good
        }

        Set<Resource> resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named
                ("defines").resources().getAll().entities();
        assert resources.stream().allMatch(res -> "playroom1".equals(res.getId()) || "playroom2".equals(res.getId()))
                : "ResourceType[Playroom] -defines-> resources called playroom1 and playroom2";

        resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships().named
                ("owns").resources().getAll().entities(); // empty
        assert resources.isEmpty()
                : "No resources should be found under the relationship called owns from resource type";
    }

    @Test
    public void testEnvironments() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph)
                    .V().has("__type", "tenant").has("__eid", tenantId).out("contains")
                    .has("__type", "environment").has("__eid", id).cast(Vertex.class);

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

        GraphQuery query = graph.query().has("__type", "environment");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testResourceTypes() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("__type", "tenant")
                    .has("__eid", tenantId).out("contains").has("__type", "resourceType").has("__eid", id)
                    .has("__version", "1.0").cast(Vertex.class);

            assert q.hasNext();

            ResourceType rt = inventory.tenants().get(tenantId).resourceTypes().get(id).entity();
            assert rt.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL");
        test.apply("com.example.tenant", "Kachna");
        test.apply("com.example.tenant", "Playroom");

        GraphQuery query = graph.query().has("__type", "resourceType");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 3;
    }

    @Test
    public void testMetricDefinitions() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("__type", "tenant")
                    .has("__eid", tenantId).out("contains").has("__type", "metricType")
                    .has("__eid", id).cast(Vertex.class);

            assert q.hasNext();

            MetricType md = inventory.tenants().get(tenantId).metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "ResponseTime");
        test.apply("com.example.tenant", "Size");

        GraphQuery query = graph.query().has("__type", "metricType");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testMetricDefsLinkedToResourceTypes() throws Exception {
        TriFunction<String, String, String, Void> test = (tenantId, resourceTypeId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("__type", "tenant")
                    .has("__eid", tenantId).out("contains").has("__type", "resourceType")
                    .has("__eid", resourceTypeId).out("owns").has("__type", "metricType").has("__eid", id)
                    .cast(Vertex.class);

            assert q.hasNext();

            MetricType md = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                    .metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL", "ResponseTime");
        test.apply("com.example.tenant", "Playroom", "Size");
    }

    @Test
    public void testMetrics() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, metricDefId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("__type", "tenant")
                    .has("__eid", tenantId).out("contains").has("__type", "environment").has("__eid", environmentId)
                    .out("contains").has("__type", "metric").has("__eid", id).as("metric").in("defines")
                    .has("__type", "metricType").has("__eid", metricDefId).back("metric").cast(Vertex.class);

            assert q.hasNext();

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessMetrics()
                    .getAll(Defined.by(new MetricType(tenantId, metricDefId)), With.id(id)).entities().iterator()
                    .next();
            assert m.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "ResponseTime", "host1_ping_response");
        test.apply("com.example.tenant", "test", "Size", "playroom1_size");
        test.apply("com.example.tenant", "test", "Size", "playroom2_size");

        GraphQuery query = graph.query().has("__type", "metric");
        Assert.assertEquals(4, StreamSupport.stream(query.vertices().spliterator(), false).count());
    }

    @Test
    public void testResources() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceTypeId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("__type", "tenant")
                    .has("__eid", tenantId).out("contains").has("__type", "environment").has("__eid", environmentId)
                    .out("contains").has("__type", "resource").has("__eid", id).as("resource").in("defines")
                    .has("__type", "resourceType").has("__eid", resourceTypeId).back("resource").cast(Vertex.class);

            assert q.hasNext();

            Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources()
                    .getAll(Defined.by(new ResourceType(tenantId, resourceTypeId, "1.0")), With.id(id)).entities()
                    .iterator().next();
            assert r.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "URL", "host1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom2");

        GraphQuery query = graph.query().has("__type", "resource");
        Assert.assertEquals(6, StreamSupport.stream(query.vertices().spliterator(), false).count());
    }

    @Test
    public void testAssociateMetricWithResource() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceId, metricId) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("__type", "tenant")
                    .has("__eid", tenantId).out("contains").has("__type", "environment").has("__eid", environmentId)
                    .out("contains").has("__type", "resource").has("__eid", resourceId).out("owns")
                    .has("__type", "metric").has("__eid", metricId).cast(Vertex.class);

            assert q.hasNext();

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources()
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
        Set<ResourceType> types = inventory.tenants().getAll().resourceTypes().getAll().entities();
        assert types.size() == 3;
    }

    @Test
    public void queryMultipleMetricDefs() throws Exception {
        Set<MetricType> types = inventory.tenants().getAll().metricTypes().getAll().entities();
        assert types.size() == 2;
    }

    @Test
    public void queryMultipleResources() throws Exception {
        Set<Resource> rs = inventory.tenants().getAll().environments().getAll().feedlessResources().getAll().entities();
        assert rs.size() == 3;
    }

    @Test
    public void queryMultipleMetrics() throws Exception {
        Set<Metric> ms = inventory.tenants().getAll().environments().getAll().feedlessMetrics().getAll().entities();
        assert ms.size() == 3;
    }

    @Test
    public void testNoTwoFeedsWithSameID() throws Exception {
        Feeds.ReadWrite feeds = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .feeds();

        Feed f1 = feeds.create(new Feed.Blueprint("feed", null)).entity();
        Feed f2  = feeds.create(new Feed.Blueprint("feed", null)).entity();

        assert f1.getId().equals("feed");
        assert !f1.getId().equals(f2.getId());
    }

    @Test
    public void testNoTwoEquivalentEntitiesOnTheSamePath() throws Exception {
        try {
            inventory.tenants().create(new Tenant.Blueprint("com.acme.tenant"));
            Assert.fail("Creating tenant with existing ID should fail");
        } catch (Exception e) {
            //good
        }

        try {
            inventory.tenants().get("com.acme.tenant").environments().create(new Environment.Blueprint("production"));
            Assert.fail("Creating environment with existing ID should fail");
        } catch (Exception e) {
            //good
        }

        try {
            inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                    .create(new Resource.Blueprint("host1", "URL"));
            Assert.fail("Creating resource with existing ID should fail");
        } catch (Exception e) {
            //good
        }
    }

    @Test
    public void testContainsLoopsImpossible() throws Exception {
        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.outgoing)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Self-loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.incoming)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Self-loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").environments().get("test")
                    .relationships(Relationships.Direction.outgoing)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.incoming)
                    .linkWith("contains", new Environment("com.example.tenant", "test"), null);

            Assert.fail("Loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testContainsDiamondsImpossible() throws Exception {
        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.outgoing)
                    .linkWith("contains", new ResourceType("com.acme.tenant", "URL", "1.0"), null);

            Assert.fail("Entity cannot be contained in 2 or more others");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL")
                    .relationships(Relationships.Direction.incoming)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Entity cannot be contained in 2 or more others");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testPropertiesCreated() throws Exception {
        Tenant t = inventory.tenants().get("com.acme.tenant").entity();

        Assert.assertEquals(1, t.getProperties().size());
        Assert.assertEquals("moc", t.getProperties().get("kachny"));
    }

    @Test
    public void testPropertiesUpdatedOnEntities() throws Exception {

        inventory.tenants().update("com.acme.tenant", Tenant.Update.builder().withProperty("ducks", "many")
                .withProperty("hammer", "nails").build());

        Tenant t = inventory.tenants().get("com.acme.tenant").entity();

        Assert.assertEquals(2, t.getProperties().size());
        Assert.assertEquals("many", t.getProperties().get("ducks"));
        Assert.assertEquals("nails", t.getProperties().get("hammer"));

        //reset the change we made back...
        inventory.tenants().update("com.acme.tenant", Tenant.Update.builder().withProperty("kachny", "moc").build());
        testPropertiesCreated();
    }

    @Test
    public void testPropertiesUpdatedOnRelationships() throws Exception {

        Relationship r = inventory.tenants().get("com.acme.tenant").relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        inventory.tenants().get("com.acme.tenant").relationships().update(r.getId(),
                Relationship.Update.builder().withProperty("ducks", "many").withProperty("hammer", "nails").build());

        r = inventory.tenants().get("com.acme.tenant").relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        Assert.assertEquals(2, r.getProperties().size());
        Assert.assertEquals("many", r.getProperties().get("ducks"));
        Assert.assertEquals("nails", r.getProperties().get("hammer"));

        //reset the change we made back...
        inventory.tenants().get("com.acme.tenant").relationships().update(r.getId(), new Relationship.Update(null));

        r = inventory.tenants().get("com.acme.tenant").relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        Assert.assertEquals(0, r.getProperties().size());
    }

    @Test
    public void testGettingResourcesFromFeedsUsingEnvironments() throws Exception {
        Set<Resource> rs = inventory.tenants().get("com.acme.tenant").environments().get("production").allResources()
                .getAll().entities();

        Assert.assertTrue(rs.stream().anyMatch((r) -> "host1".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource1".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource2".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource3".equals(r.getId())));
    }


    @Test
    public void testGettingMetricsFromFeedsUsingEnvironments() throws Exception {
        Set<Metric> rs = inventory.tenants().get("com.acme.tenant").environments().get("production").allMetrics()
                .getAll().entities();

        Assert.assertTrue(rs.stream().anyMatch((r) -> "host1_ping_response".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedMetric1".equals(r.getId())));
    }

    @Test
    public void testAllPathsMentionedInExceptions() throws Exception {
        try {
            inventory.tenants().get("non-tenant").environments().get("non-env").allResources().getAll().metrics()
                    .get("m").entity();
            Assert.fail("Fetching non-existant entity should have failed");
        } catch (EntityNotFoundException e) {
            Filter[][] paths = e.getFilters();
            Assert.assertEquals(2, paths.length);
            Assert.assertArrayEquals(Filter.by(With.type(Tenant.class), With.id("non-tenant"), Related.by(contains),
                    With.type(Environment.class), With.id("non-env"), Related.by(contains), With.type(Resource.class),
                    Related.by(owns), With.type(Metric.class), With.id("m")).get(), paths[0]);
            Assert.assertArrayEquals(Filter.by(With.type(Tenant.class), With.id("non-tenant"), Related.by(contains),
                    With.type(Environment.class), With.id("non-env"), Related.by(contains), With.type(Feed.class),
                    Related.by(contains), With.type(Resource.class), Related.by(owns), With.type(Metric.class),
                    With.id("m")).get(), paths[1]);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class DummyTransactionalGraph extends WrappedGraph<TinkerGraph> implements TransactionalGraph {

        public DummyTransactionalGraph(org.apache.commons.configuration.Configuration configuration) {
            super(new TinkerGraph(configuration));
        }

        @Override
        @SuppressWarnings("deprecation")
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
