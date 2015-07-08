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
package org.hawkular.inventory.api.test;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.Action.updated;
import static org.hawkular.inventory.api.Relationships.Direction.both;
import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.Relationships.WellKnown.isParentOf;
import static org.hawkular.inventory.api.filters.Related.asTargetBy;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.FeedAlreadyRegisteredException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.base.PathFragment;
import org.hawkular.inventory.base.Query;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import rx.Subscription;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public abstract class AbstractBaseInventoryPersistenceCheck<E> {
    protected BaseInventory<E> inventory;

    protected abstract BaseInventory<E> instantiateNewInventory();

    protected abstract void destroyStorage() throws Exception;

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

        inventory = instantiateNewInventory();
        inventory.initialize(config);

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
                .create(new ResourceType.Blueprint("URL")).entity().getId().equals("URL");
        assert inventory.tenants().get("com.acme.tenant").metricTypes()
                .create(new MetricType.Blueprint("ResponseTime", MetricUnit.MILLI_SECOND)).entity().getId()
                .equals("ResponseTime");

        inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL").metricTypes().associate(CanonicalPath.of()
                .tenant("com.acme.tenant").metricType("ResponseTime").get());

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessMetrics()
                .create(new Metric.Blueprint("/ResponseTime", "host1_ping_response")).entity().getId()
                .equals("host1_ping_response");
        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                .create(new Resource.Blueprint("host1", "/URL")).entity()
                .getId().equals("host1");
        inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                .get("host1").metrics().associate(RelativePath.fromString("../m;host1_ping_response"));

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds()
                .create(new Feed.Blueprint("feed1", null)).entity().getId().equals("feed1");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource1", "/URL")).entity().getId()
                .equals("feedResource1");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource2", "/URL")).entity().getId()
                .equals("feedResource2");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource3", "/URL")).entity().getId()
                .equals("feedResource3");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .metrics().create(new Metric.Blueprint("/ResponseTime", "feedMetric1")).entity().getId()
                .equals("feedMetric1");

        assert inventory.tenants().create(new Tenant.Blueprint("com.example.tenant")).entity().getId()
                .equals("com.example.tenant");
        assert inventory.tenants().get("com.example.tenant").environments().create(new Environment.Blueprint("test"))
                .entity().getId().equals("test");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Kachna")).entity().getId().equals("Kachna");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Playroom", new HashMap<String, Object>() {{
                    put("ownedByDepartment", "Facilities");
                }})).entity().getId().equals("Playroom");
        assert inventory.tenants().get("com.example.tenant").metricTypes()
                .create(new MetricType.Blueprint("Size", MetricUnit.BYTE)).entity().getId().equals("Size");
        inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").metricTypes()
                .associate(RelativePath.to().up().metricType("Size").get());

        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .create(new Metric.Blueprint("/Size", "playroom1_size")).entity().getId().equals("playroom1_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .create(new Metric.Blueprint("/Size", "playroom2_size")).entity().getId().equals("playroom2_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .create(new Resource.Blueprint("playroom1", "/Playroom")).entity().getId().equals("playroom1");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .create(new Resource.Blueprint("playroom2", "/Playroom")).entity().getId().equals("playroom2");

        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom1").metrics().associate(RelativePath.to().up().metric("playroom1_size").get());
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().associate(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").metric("playroom2_size").get());

        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom1").containedChildren().create(new Resource.Blueprint("playroom1.1", "/Playroom"))
                .entity().getId().equals("playroom1.1");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom1").containedChildren().create(new Resource.Blueprint("playroom1.2", "/Playroom"))
                .entity().getId().equals("playroom1.2");
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom1").allChildren().associate(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").resource("playroom2").get());

        // some ad-hoc relationships
        Environment test = inventory.tenants().get("com.example.tenant").environments().get("test").entity();
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").metric("playroom2_size").get()).relationships(outgoing)
                .linkWith("yourMom", CanonicalPath.of().tenant("com.example.tenant").environment("test").get(), null);

        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").metric("playroom2_size").get()).relationships(incoming)
                .linkWith("IamYourFather", CanonicalPath.of().tenant("com.example.tenant").environment("test").get(),
                        new HashMap<String, Object>() {{
                            put("adult", true);
                        }});
    }

    private void teardownData() throws Exception {
        CanonicalPath tenantPath = CanonicalPath.of().tenant("com.example.tenant").get();
        CanonicalPath environmentPath = tenantPath.extend(Environment.class, "test").get();

        Tenant t = new Tenant(tenantPath);
        Environment e = new Environment(environmentPath);
        MetricType sizeType = new MetricType(tenantPath.extend(MetricType.class, "Size").get());
        ResourceType playRoomType = new ResourceType(tenantPath.extend(ResourceType.class, "Playroom").get());
        ResourceType kachnaType = new ResourceType(tenantPath.extend(ResourceType.class, "Kachna").get());
        Resource playroom1 = new Resource(environmentPath.extend(Resource.class, "playroom1").get(), playRoomType);
        Resource playroom2 = new Resource(environmentPath.extend(Resource.class, "playroom2").get(), playRoomType);
        Metric playroom1Size = new Metric(environmentPath.extend(Metric.class, "playroom1_size").get(), sizeType);
        Metric playroom2Size = new Metric(environmentPath.extend(Metric.class, "playroom2_size").get(), sizeType);

        //when an association is deleted, it should not be possible to access the target entity through the same
        //traversal again
        inventory.inspect(playroom2).metrics().disassociate(playroom2Size.getPath());
        Assert.assertFalse(inventory.inspect(playroom2).metrics().get(playroom2Size.getPath()).exists());
        Assert.assertFalse(inventory.inspect(playroom2).metrics().get(
                RelativePath.to().up().metric(playroom2Size.getId()).get()).exists());

        inventory.inspect(playroom2Size).delete();

        assertDoesNotExist(playroom2Size);
        assertExists(t, e, sizeType, playRoomType, kachnaType, playroom1, playroom2, playroom1Size);

        //disassociation using a relative path should work, too
        inventory.inspect(playroom1).metrics().disassociate(RelativePath.to().up().metric(playroom1Size.getId()).get());
        Assert.assertFalse(inventory.inspect(playroom1).metrics().get(playroom1Size.getPath()).exists());
        Assert.assertFalse(inventory.inspect(playroom1).metrics().get(
                RelativePath.to().up().metric(playroom1Size.getId()).get()).exists());

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
            try {
                inventory.close();
            } finally {
                destroyStorage();
            }
        }
    }

    @Test
    public void testTenants() throws Exception {
        Function<String, Void> test = (id) -> {
            Query query = Query.empty().asBuilder()
                    .with(PathFragment.from(type(Tenant.class), id(id))).build();

            Page<E> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

            Assert.assertEquals(1, results.size());

            E tenant = results.get(0);

            String eid = inventory.getBackend().extractId(tenant);

            Assert.assertEquals(id, eid);

            return null;
        };

        test.apply("com.acme.tenant");
        test.apply("com.example.tenant");

        Query query = Query.empty().asBuilder()
                .with(PathFragment.from(type(Tenant.class))).build();

        Page<E> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testEntitiesByRelationships() throws Exception {
        Function<Integer, Function<Class<? extends Entity<?, ?>>, Function<String, Function<Integer,
                Function<Class<? extends Entity<?, ?>>, Function<ResolvableToMany<?>,
                        Consumer<ResolvableToMany<?>>>>>>>>
                testHelper = (numberOfParents -> parentType -> edgeLabel -> numberOfKids -> childType ->
                multipleParents -> multipleChildren -> {
                    InventoryBackend<?> backend = inventory.getBackend();

                    Page<?> parents = backend.query(Query.path().with(type(parentType)).get(),
                            Pager.unlimited(Order.unspecified()));

                    Page<?> children = backend.query(Query.path().with(type(parentType),
                            by(edgeLabel), type(childType)).get(), Pager.unlimited(Order.unspecified()));

                    assert parents.size() == numberOfParents : "There must be exactly " + numberOfParents + " " +
                            parentType + "s " + "that have outgoing edge labeled with " + edgeLabel + ". Backend " +
                            "query returned only " + parents.size();

                    assert multipleParents.entities().size() == numberOfParents : "There must be exactly " +
                            numberOfParents + " " + parentType + "s that have outgoing edge labeled with " + edgeLabel +
                            ". Tested API returned only " + multipleParents.entities().size();

                    assert children.size() == numberOfKids : "There must be exactly " + numberOfKids + " " + childType +
                            "s that are directly under " + parentType + " connected with " + edgeLabel +
                            ". Gremlin query returned only " + children.size();

                    assert multipleChildren.entities().size() == numberOfKids;
                });

        ResolvableToMany parents = inventory.tenants().getAll(by("contains"));
        ResolvableToMany kids = inventory.tenants().getAll().environments().getAll(asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(2).apply(Environment.class).apply(parents)
                .accept(kids);

        kids = inventory.tenants().getAll().resourceTypes().getAll(asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(3).apply(ResourceType.class).apply(parents)
                .accept(kids);

        kids = inventory.tenants().getAll().metricTypes().getAll(asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(2).apply(MetricType.class).apply(parents)
                .accept(kids);

        parents = inventory.tenants().getAll().environments().getAll(by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().feedlessMetrics().getAll(
                asTargetBy("contains"));
        testHelper.apply(2).apply(Environment.class).apply("contains").apply(3).apply(Metric.class).apply(parents).
                accept(kids);

        kids = inventory.tenants().getAll().environments().getAll().feedlessResources().getAll(
                asTargetBy("contains"));
        testHelper.apply(2).apply(Environment.class).apply("contains").apply(3).apply(Resource.class).apply(parents)
                .accept(kids);

        parents = inventory.tenants().getAll().environments().getAll(by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().allMetrics().getAll(
                asTargetBy("defines"));
        testHelper.apply(2).apply(MetricType.class).apply("defines").apply(4).apply(Metric.class).apply(parents)
                .accept(kids);
    }

    @Test
    public void testRelationshipServiceNamed1() throws Exception {
        Set<Relationship> contains = inventory.tenants().getAll().relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getSegment().getElementId())
                && "URL".equals(rel.getTarget().getSegment().getElementId()))
                : "Tenant 'com.acme.tenant' must contain ResourceType 'URL'.";
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getSegment().getElementId())
                && "production".equals(rel.getTarget().getSegment().getElementId()))
                : "Tenant 'com.acme.tenant' must contain Environment 'production'.";
        assert contains.stream().anyMatch(rel -> "com.example.tenant".equals(rel.getSource().getSegment()
                .getElementId()) && "Size".equals(rel.getTarget().getSegment().getElementId()))
                : "Tenant 'com.example.tenant' must contain MetricType 'Size'.";

        contains.forEach((r) -> {
            assert r.getId() != null;
        });
    }

    @Test
    public void testRelationshipServiceNamed2() throws Exception {
        Set<Relationship> contains = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "playroom1".equals(rel.getTarget().getSegment().getElementId()))
                : "Environment 'test' must contain 'playroom1'.";
        assert contains.stream().anyMatch(rel -> "playroom2".equals(rel.getTarget().getSegment().getElementId()))
                : "Environment 'test' must contain 'playroom2'.";
        assert contains.stream().anyMatch(rel -> "playroom2_size".equals(rel.getTarget().getSegment().getElementId()))
                : "Environment 'test' must contain 'playroom2_size'.";
        assert contains.stream().anyMatch(rel -> "playroom1_size".equals(rel.getTarget().getSegment().getElementId()))
                : "Environment 'test' must contain 'playroom1_size'.";
        assert contains.stream().allMatch(rel -> !"production".equals(rel.getSource().getSegment().getElementId()))
                : "Environment 'production' cant be the source of these relationships.";

        contains.forEach((r) -> {
            assert r.getId() != null;
        });
    }

    @Test
    public void testRelationshipServiceLinkedWith() throws Exception {
        Set<Relationship> rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .feedlessMetrics().get("playroom2_size").relationships(outgoing).named("yourMom").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getTarget().getSegment().getElementId())
                : "Target of relationship 'yourMom' should be the 'test' environment";

        rels = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .get("playroom2_size").relationships(both).named("IamYourFather").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getSource().getSegment().getElementId())
                : "Source of relationship 'IamYourFather' should be the 'test' environment";
    }

    @Test
    public void testRelationshipServiceLinkedWithAndDelete() throws Exception {
        Relationship link = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .feedlessResources().get("host1").relationships(incoming)
                .linkWith("crossTenantLink", CanonicalPath.of().tenant("com.example.tenant").get(), null).entity();

        assert inventory.tenants().get("com.example.tenant").relationships(outgoing)
                .named("crossTenantLink").entities().size() == 1 : "Relation 'crossTenantLink' was not found.";
        // delete the relationship
        inventory.tenants().get("com.example.tenant").relationships(/*defaults to outgoing*/).delete(link.getId());
        assert inventory.tenants().get("com.example.tenant").relationships()
                .named("crossTenantLink").entities().size() == 0 : "Relation 'crossTenantLink' was found.";

        // try deleting again
        try {
            inventory.tenants().get("com.example.tenant").relationships(/*defaults to outgoing*/).delete(link.getId());
            assert false : "It shouldn't be possible to delete the same relationship twice";
        } catch (RelationNotFoundException e) {
            // good
        }
    }

    @Test
    public void testRelationshipServiceUpdateRelationship1() throws Exception {
        final String someKey = "k3y";
        final String someValue = "v4lu3";
        Relationship rel1 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .get("playroom2_size").relationships(outgoing).named("yourMom").entities().iterator().next();
        assert null == rel1.getProperties().get(someKey) : "There should not be any property with key 'k3y'";

        Relationship rel2 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .get("playroom2_size").relationships(outgoing).named("yourMom").entities().iterator().next();
        assert rel1.getId().equals(rel2.getId()) && null == rel2.getProperties().get(someKey) : "There should not be" +
                " any property with key 'k3y'";

        // persist the change
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics().get("playroom2_size")
                .relationships(outgoing).update(rel1.getId(), Relationship.Update.builder()
                .withProperty(someKey, someValue).build());

        Relationship rel3 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .get("playroom2_size").relationships(outgoing).named("yourMom").entities().iterator().next();
        assert rel1.getId().equals(rel3.getId()) && someValue.equals(rel3.getProperties().get(someKey))
                : "There should be the property with key 'k3y' and value 'v4lu3'";
    }

    @Test
    public void testRelationshipServiceGetAllFilters() throws Exception {
        Set<Relationship> rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(outgoing).getAll(RelationWith.name("contains")).entities();
        assert rels != null && rels.size() == 4 : "There should be 4 relationships conforming the filters";
        assert rels.stream().anyMatch(rel -> "playroom2_size".equals(rel.getTarget().getSegment().getElementId()));
        assert rels.stream().anyMatch(rel -> "playroom1".equals(rel.getTarget().getSegment().getElementId()));


        rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(outgoing).getAll(RelationWith.name("contains"), RelationWith
                        .targetOfType(Metric.class)).entities();
        assert rels != null && rels.size() == 2 : "There should be 2 relationships conforming the filters";
        assert rels.stream().allMatch(rel ->
                Metric.class.equals(rel.getTarget().getSegment().getElementType())) : "The type of all the " +
                "targets should be the 'Metric'";


        rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(incoming).getAll(RelationWith.name("contains")).entities();

        assert rels != null && rels.size() == 1 : "There should be just 1 relationship conforming the filters";
        assert "com.example.tenant".equals(rels.iterator().next().getSource().getSegment().getElementId())
                : "Tenant 'com.example.tenant' was not found";


        rels = inventory.tenants().getAll().relationships().named
                (contains).environments().getAll().relationships().getAll(RelationWith
                .propertyValues("label", "contains"), RelationWith.targetsOfTypes(Resource.class, Metric.class))
                .entities();
        assert rels != null && rels.size() == 6 : "There should be 6 relationships conforming the filters";
        assert rels.stream().allMatch(rel -> "test".equals(rel.getSource().getSegment().getElementId())
                || "production".equals(rel.getSource().getSegment().getElementId()))
                : "Source should be either 'test' or 'production'";
        assert rels.stream().allMatch(rel -> Resource.class.equals(rel.getTarget().getSegment().getElementType()) ||
                Metric.class.equals(rel.getTarget().getSegment().getElementType()))
                : "Target should be either a metric or a resource";
    }

    @Test
    public void testRelationshipServiceGetAllFiltersWithSubsequentCalls() throws Exception {
        Metric metric = inventory.tenants().getAll().relationships().named
                (contains).environments().getAll().relationships().getAll(RelationWith
                .propertyValues("label", "contains"), RelationWith.targetsOfTypes(Resource.class, Metric.class))
                .metrics().getAll(id("playroom1_size")).entities().iterator().next();
        assert "playroom1_size".equals(metric.getId()) : "Metric playroom1_size was not found using various relation " +
                "filters";

        try {
            inventory.tenants().getAll().relationships().named
                    (contains).environments().getAll().relationships().getAll(RelationWith
                    .propertyValues("label", "contains"), RelationWith.targetsOfTypes(Resource.class))
                    .metrics().getAll(id("playroom1_size")).entities().iterator().next();
            assert false : "this code should not be reachable. There should be no metric reachable under " +
                    "'RelationWith.targetsOfTypes(Resource.class))' filter";
        } catch (NoSuchElementException e) {
            // good
        }
    }

    @Test
    public void testRelationshipServiceCallChaining() throws Exception {
        MetricType metricType = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named("incorporates").metricTypes().get(
                        CanonicalPath.of().tenant("com.example.tenant").metricType("Size").get()).entity();// not empty
        assert "Size".equals(metricType.getId()) : "ResourceType[Playroom] -incorporates-> MetricType[Size] was not " +
                "found";

        try {
            inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships()
                    .named("contains").metricTypes().getAll(id("Size")).entities().iterator().next();
            assert false : "There is no such an entity satisfying the query, this code shouldn't be reachable";
        } catch (NoSuchElementException e) {
            // good
        }

        Set<Resource> resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named
                        ("defines").resources().getAll().entities();
        assert resources.stream().allMatch(res -> Arrays.asList("playroom1", "playroom2", "playroom1.1", "playroom1.2")
                .contains(res.getId())) : "ResourceType[Playroom] -defines-> resources called playroom*";

        resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships().named
                ("incorporates").resources().getAll().entities(); // empty
        assert resources.isEmpty()
                : "No resources should be found under the relationship called incorporates from resource type";
    }

    @Test
    public void testEnvironments() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {
            Query q = Query.empty().asBuilder()
                    .with(PathFragment.from(type(Tenant.class), id(tenantId), by(contains),
                            type(Environment.class), id(id))).build();


            Page<E> envs = inventory.getBackend().query(q, Pager.unlimited(Order.unspecified()));

            Assert.assertEquals(1, envs.size());

            //query, we should get the same results
            Environment env = inventory.tenants().get(tenantId).environments().get(id).entity();
            Assert.assertEquals(id, env.getId());

            env = inventory.getBackend().convert(envs.get(0), Environment.class);
            Assert.assertEquals(id, env.getId());

            return null;
        };

        test.apply("com.acme.tenant", "production");
        test.apply("com.example.tenant", "test");

        Query q = Query.empty().asBuilder()
                .with(PathFragment.from(type(Environment.class))).build();

        Assert.assertEquals(2, inventory.getBackend().query(q, Pager.unlimited(Order.unspecified())).size());
    }

    @Test
    public void testResourceTypes() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            Query query = Query.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(ResourceType.class), id(id)).get();

            Page<?> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

            assert !results.isEmpty();

            ResourceType rt = inventory.tenants().get(tenantId).resourceTypes().get(id).entity();
            assert rt.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL");
        test.apply("com.example.tenant", "Kachna");
        test.apply("com.example.tenant", "Playroom");

        Query query = Query.path().with(type(ResourceType.class)).get();
        assert 3 == inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).size();
    }

    @Test
    public void testMetricDefinitions() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            Query query = Query.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(MetricType.class), id(id)).get();

            assert !inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).isEmpty();

            MetricType md = inventory.tenants().get(tenantId).metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "ResponseTime");
        test.apply("com.example.tenant", "Size");

        Query query = Query.path().with(type(MetricType.class)).get();
        assert 2 == inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).size();
    }

    @Test
    public void testMetricTypesLinkedToResourceTypes() throws Exception {
        TetraFunction<String, String, String, CanonicalPath, Void> test = (tenantId, resourceTypeId, id, path) -> {

            Query q = Query.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(ResourceType.class), id(resourceTypeId), by(incorporates),
                    type(MetricType.class), id(id)).get();

            assert !inventory.getBackend().query(q, Pager.unlimited(Order.unspecified())).isEmpty();

            MetricType md = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                    .metricTypes().get(path).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL", "ResponseTime", CanonicalPath.of().tenant("com.acme.tenant")
                .metricType("ResponseTime").get());
        test.apply("com.example.tenant", "Playroom", "Size", CanonicalPath.of().tenant("com.example.tenant")
                .metricType("Size").get());
    }

    @Test
    public void testMetrics() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, metricDefId, id) -> {

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessMetrics()
                    .getAll(new Filter[][]{
                            {Defined.by(CanonicalPath.of().tenant(tenantId).metricType(metricDefId)
                                    .get())},
                            {id(id)}})
                    .entities().iterator().next();
            assert m.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "ResponseTime", "host1_ping_response");
        test.apply("com.example.tenant", "test", "Size", "playroom1_size");
        test.apply("com.example.tenant", "test", "Size", "playroom2_size");

        Assert.assertEquals(4, inventory.getBackend().query(Query.path().with(type(Metric.class)).get(),
                Pager.unlimited(Order.unspecified())).size());
    }

    @Test
    public void testResources() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceTypeId, id) -> {
            Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources()
                    .getAll(new Filter[][]{
                            {Defined.by(CanonicalPath.of().tenant(tenantId).resourceType(resourceTypeId).get())},
                            {id(id)}})
                    .entities().iterator().next();
            assert r.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "URL", "host1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom2");


        Assert.assertEquals(8, inventory.getBackend().query(Query.path().with(type(Resource.class)).get(),
                Pager.unlimited(Order.unspecified())).size());
    }

    @Test
    @Ignore // TODO remove @Ignore once HWKINVENT-81 is fixed
    public void testResourcesFilteredByTypeProperty() throws Exception {
        Set<Resource> resources = inventory.tenants().get("com.example.tenant").environments().get("test")
                .feedlessResources().getAll(new Filter[][]{
                        {Defined.by(CanonicalPath.of().tenant("com.example.tenant").resourceType("Playroom").get()),
                                With.propertyValue("ownedByDepartment", "Facilities")},
                }).entities();
        Assert.assertEquals(1, resources.size());
        Assert.assertEquals("playroom1", resources.iterator().next().getId());
    }

    @Test
    public void testAssociateMetricWithResource() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceId, metricId) -> {
            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources()
                    .get(resourceId).metrics().getAll(id(metricId)).entities().iterator().next();
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
        Feed f2 = null;
        try {
            f2 = feeds.create(new Feed.Blueprint("feed", null)).entity();
        } catch (FeedAlreadyRegisteredException fare) {
            //good
        }
        f2 = feeds.create(new Feed.Blueprint(null, null)).entity();
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
            inventory.tenants().get("com.example.tenant").relationships(outgoing)
                    .linkWith("contains", CanonicalPath.of().tenant("com.example.tenant").get(), null);

            Assert.fail("Self-loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").relationships(incoming)
                    .linkWith("contains", CanonicalPath.of().tenant("com.example.tenant").get(), null);

            Assert.fail("Self-loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").environments().get("test")
                    .relationships(outgoing)
                    .linkWith("contains", CanonicalPath.of().tenant("com.example.tenant").get(), null);

            Assert.fail("Loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").relationships(incoming)
                    .linkWith("contains", CanonicalPath.of().tenant("com.example.tenant")
                            .environment("test").get(), null);

            Assert.fail("Loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testContainsDiamondsImpossible() throws Exception {
        try {
            inventory.tenants().get("com.example.tenant").relationships(outgoing)
                    .linkWith("contains", CanonicalPath.of().tenant("com.acme.tenant").resourceType("URL").get(), null);

            Assert.fail("Entity cannot be contained in 2 or more others");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL")
                    .relationships(incoming)
                    .linkWith("contains", CanonicalPath.of().tenant("com.example.tenant").get(), null);

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
        inventory.tenants().get("com.acme.tenant").update(Tenant.Update.builder().withProperty("kachny", "moc")
                .build());

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
    public void testPaging() throws Exception {
        //the page is not modifiable but we'll need to modify this later on in the tests
        List<Metric> allResults = new ArrayList<>(inventory.tenants().getAll().environments().getAll().feedlessMetrics()
                .getAll().entities(Pager.unlimited(Order.by("id", Order.Direction.DESCENDING))));

        assert allResults.size() == 3;

        Pager firstPage = new Pager(0, 1, Order.by("id", Order.Direction.DESCENDING));

        Metrics.Multiple metrics = inventory.tenants().getAll().environments().getAll().feedlessMetrics().getAll();

        Page<Metric> ms = metrics.entities(firstPage);
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert ms.get(0).equals(allResults.get(0));

        ms = metrics.entities(firstPage.nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert ms.get(0).equals(allResults.get(1));

        ms = metrics.entities(firstPage.nextPage().nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert ms.get(0).equals(allResults.get(2));

        ms = metrics.entities(firstPage.nextPage().nextPage().nextPage());
        assert ms.getTotalSize() == 3;
        assert ms.size() == 0;

        //try the same with an unspecified order
        //the reason for checking this explicitly is that the order pipe implicitly loads
        //all elements before sending them on in the pipeline to the range filter.
        //If the order is not present, the elements might not be all loaded before the range
        //is overflown. The total still needs to match even in that case.
        firstPage = new Pager(0, 1, Order.unspecified());

        ms = metrics.entities(firstPage);
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(ms.get(0)); //i.e. we check that the result is in all results and remove it from there
        //so that subsequent checks for the same thing cannot get confused by the
        //existence of this metric in all the results.

        ms = metrics.entities(firstPage.nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(ms.get(0));

        ms = metrics.entities(firstPage.nextPage().nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(ms.get(0));

        ms = metrics.entities(firstPage.nextPage().nextPage().nextPage());
        assert ms.getTotalSize() == 3;
        assert ms.size() == 0;
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
    public void testFilterByPropertyValues() throws Exception {
        Assert.assertTrue(inventory.tenants().getAll(With.property("kachny")).anyExists());
        Assert.assertFalse(inventory.tenants().getAll(With.property("kachna")).anyExists());
        Assert.assertTrue(inventory.tenants().getAll(With.propertyValue("kachny", "moc")).anyExists());
        Assert.assertFalse(inventory.tenants().getAll(With.propertyValue("kachny", "malo")).anyExists());
        Assert.assertTrue(inventory.tenants().getAll(With.propertyValues("kachny", "moc", "malo")).anyExists());
        Assert.assertFalse(inventory.tenants().getAll(With.propertyValues("kachny", "hodne", "malo")).anyExists());

        Assert.assertTrue(inventory.tenants().get("com.example.tenant").environments().get("test").relationships()
                .getAll(RelationWith.property("adult")).anyExists());
        Assert.assertFalse(inventory.tenants().get("com.example.tenant").environments().get("test").relationships()
                .getAll(RelationWith.property("infant")).anyExists());
        Assert.assertTrue(inventory.tenants().get("com.example.tenant").environments().get("test").relationships()
                .getAll(RelationWith.propertyValue("adult", true)).anyExists());
        Assert.assertFalse(inventory.tenants().get("com.example.tenant").environments().get("test").relationships()
                .getAll(RelationWith.propertyValue("adult", false)).anyExists());
        Assert.assertTrue(inventory.tenants().get("com.example.tenant").environments().get("test").relationships()
                .getAll(RelationWith.propertyValues("adult", false, true)).anyExists());
        Assert.assertFalse(inventory.tenants().get("com.example.tenant").environments().get("test").relationships()
                .getAll(RelationWith.propertyValues("adult", false, "true")).anyExists());
    }

    @Test
    public void testResourceHierarchy() throws Exception {
        Resources.Single res = inventory.tenants().get("com.example.tenant").environments().get("test")
                .feedlessResources().get("playroom1");

        Pager pager = Pager.unlimited(Order.unspecified());

        Page<Resource> children = res.containedChildren().getAll().entities(pager);
        Assert.assertEquals(2, children.size());

        children = res.allChildren().getAll().entities(pager);
        Assert.assertEquals(3, children.size());

        children = res.allChildren().getAll(Related.asTargetWith(res.entity().getPath(), isParentOf)).entities(pager);
        Assert.assertEquals(3, children.size());
    }

    @Test
    public void testResourceHierarchyNoLoopsPossible() throws Exception {
        //first, let's try a self-loop using generic relationships
        CanonicalPath t = CanonicalPath.of().tenant("com.example.tenant").get();

        try {
            inventory.inspect(t, Tenants.Single.class).relationships(outgoing).linkWith(isParentOf, t, null);
            Assert.fail("Should not be able to create self-loop in isParentOf using generic relationships");
        } catch (IllegalArgumentException e) {
            //good
        }

        //now let's try self-loop using association interface
        Resource r = inventory.inspect(t, Tenants.Single.class).environments().get("test").feedlessResources()
                .get("playroom1").entity();

        try {
            inventory.inspect(r).allChildren().associate(r.getPath());
            Assert.fail("Should not be able to create self-loop in isParentOf using resource association interface.");
        } catch (IllegalArgumentException e) {
            //good
        }

        //playroom1 -isParentOf> playroom2 exists. Let's try creating a loop
        Resource r2 = inventory.inspect(t, Tenants.Single.class).environments().get("test").feedlessResources()
                .get("playroom2").entity();

        try {
            inventory.inspect(r2).relationships(outgoing).linkWith(isParentOf, r.getPath(), null);
            Assert.fail("Should not be possible to create loops in isParentOf using generic relationships.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(r2).allChildren().associate(r.getPath());
            Assert.fail("Should not be possible to create loops in isParentOf using association interface.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testImpossibleToDeleteContainsRelationship() throws Exception {
        try {
            Set<Relationship> rels = inventory.tenants().get("com.example.tenant").relationships().named(contains)
                    .entities();

            inventory.tenants().get("com.example.tenant").relationships().delete(rels.iterator().next().getId());

            Assert.fail("Should not be able to delete contains relationship explicitly.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testImpossibleToDeleteIsParentOfWhenTheresContainsToo() throws Exception {
        Set<Relationship> rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .feedlessResources().get("playroom1").relationships().named(isParentOf).entities();

        for (Relationship r : rels) {
            if (r.getTarget().getSegment().getElementId().equals("playroom1.1")) {
                try {
                    inventory.tenants().get("com.example.tenant").environments().get("test")
                            .feedlessResources().get("playroom1").relationships().delete(r.getId());

                    Assert.fail("Should not be possible to delete isParentOf when there's contains relationship in" +
                            " the same direction, too.");
                } catch (IllegalArgumentException e) {
                    //good
                }
                break;
            }
        }
    }

    @Test
    public void testRelativePathHandlingDuringDisassociationWhenThereAreMultipleRels() throws Exception {
        Environments.Single env = inventory.inspect(CanonicalPath.fromString("/t;com.example.tenant/e;test"),
                Environments.Single.class);

        Feed f = null;
        Resource r = null;
        Metric m1 = null;
        Metric m2 = null;

        try {
            r = env.feedlessResources().create(Resource.Blueprint.builder().withId("assocR1")
                    .withResourceTypePath("/rt;Playroom").build()).entity();

            //notice that m1 and m2 have the same ID, only a different path

            m1 = env.feedlessMetrics().create(Metric.Blueprint.builder().withId("assocMetric")
                    .withMetricTypePath("/mt;Size").build()).entity();

            f = env.feeds().create(Feed.Blueprint.builder().withId("assocF").build()).entity();

            m2 = env.feeds().get("assocF").metrics().create(Metric.Blueprint.builder().withId("assocMetric")
                    .withMetricTypePath("/mt;Size").build()).entity();

            inventory.inspect(r).metrics().associate(m1.getPath());
            inventory.inspect(r).metrics().associate(m2.getPath());

            Assert.assertEquals(new HashSet<>(Arrays.asList(m1, m2)),
                    inventory.inspect(r).metrics().getAll().entities());

            inventory.inspect(r).metrics().disassociate(Path.fromString("../m;assocMetric"));

            Assert.assertEquals(Collections.singleton(m2), inventory.inspect(r).metrics().getAll().entities());
        } finally {
            if (r != null) {
                inventory.inspect(r).delete();
            }

            if (m1 != null) {
                inventory.inspect(m1).delete();
            }

            if (m2 != null) {
                inventory.inspect(m2).delete();
            }

            if (f != null) {
                inventory.inspect(f).delete();
            }
        }
    }

    @Test
    public void testObserveTenants() throws Exception {
        runObserverTest(Tenant.class, 0, 0, () -> {
            inventory.tenants().create(new Tenant.Blueprint("xxx"));
            inventory.tenants().update("xxx", Tenant.Update.builder().build());
            inventory.tenants().delete("xxx");
        });
    }

    @Test
    public void testObserveEnvironments() throws Exception {
        runObserverTest(Environment.class, 1, 1, () -> {
            inventory.tenants().create(Tenant.Blueprint.builder().withId("t").build());
            inventory.tenants().get("t").environments().create(Environment.Blueprint.builder().withId("xxx").build());
            inventory.tenants().get("t").environments().update("xxx", Environment.Update.builder().build());
            inventory.tenants().get("t").environments().delete("xxx");
            inventory.tenants().delete("t");
        });
    }

    @Test
    public void testObserveResourceTypes() throws Exception {
        runObserverTest(ResourceType.class, 3, 3, () -> {
            inventory.tenants().create(Tenant.Blueprint.builder().withId("t").build());

            inventory.tenants().get("t").resourceTypes().create(new ResourceType.Blueprint("rt"));
            inventory.tenants().get("t").resourceTypes().update("rt", new ResourceType.Update(null));

            MetricType mt = inventory.tenants().get("t").metricTypes().create(MetricType.Blueprint.builder()
                    .withId("mt").withUnit(MetricUnit.BYTE).build()).entity();

            List<Relationship> createdRelationships = new ArrayList<>();

            inventory.observable(Interest.in(Relationship.class).being(created())).subscribe(createdRelationships::add);

            inventory.tenants().get("t").resourceTypes().get("rt").metricTypes().associate(mt.getPath());

            inventory.tenants().get("t").metricTypes().delete("mt");

            Assert.assertEquals(1, createdRelationships.size());

            inventory.tenants().get("t").resourceTypes().delete("rt");

            inventory.tenants().delete("t");
        });
    }

    @Test
    public void testObserveMetricTypes() throws Exception {
        runObserverTest(MetricType.class, 1, 1, () -> {
            inventory.tenants().create(Tenant.Blueprint.builder().withId("t").build());

            inventory.tenants().get("t").metricTypes()
                    .create(new MetricType.Blueprint("mt", MetricUnit.BYTE));
            inventory.tenants().get("t").metricTypes().update("mt", MetricType.Update.builder()
                    .withUnit(MetricUnit.MILLI_SECOND).build());
            inventory.tenants().get("t").metricTypes().delete("mt");
        });
    }

    @Test
    public void testObserveMetrics() throws Exception {
        runObserverTest(Metric.class, 4, 2, () -> {
            inventory.tenants().create(Tenant.Blueprint.builder().withId("t").build());
            inventory.tenants().get("t").environments().create(Environment.Blueprint.builder().withId("e").build());
            inventory.tenants().get("t").metricTypes().create(new MetricType.Blueprint("mt", MetricUnit.BYTE));

            inventory.tenants().get("t").environments().get("e").feedlessMetrics()
                    .create(new Metric.Blueprint("/mt", "m"));
            inventory.tenants().get("t").environments().get("e").feedlessMetrics().update("m",
                    Metric.Update.builder().build());

            inventory.tenants().get("t").environments().get("e").feedlessMetrics().delete("m");
        });
    }

    @Test
    public void testObserveResources() throws Exception {
        runObserverTest(Resource.class, 8, 3, () -> {
            inventory.tenants().create(Tenant.Blueprint.builder().withId("t").build());
            inventory.tenants().get("t").environments().create(Environment.Blueprint.builder().withId("e").build());
            inventory.tenants().get("t").resourceTypes().create(ResourceType.Blueprint.builder().withId("rt").build());
            inventory.tenants().get("t").metricTypes().create(MetricType.Blueprint.builder().withId("mt")
                    .withUnit(MetricUnit.BYTE).build());

            inventory.tenants().get("t").environments().get("e").feedlessResources()
                    .create(new Resource.Blueprint("r", "/rt"));
            inventory.tenants().get("t").environments().get("e").feedlessResources().update("r",
                    Resource.Update.builder().build());

            Metric m = inventory.tenants().get("t").environments().get("e").feedlessMetrics()
                    .create(Metric.Blueprint.builder().withId("m").withMetricTypePath("/mt").build()).entity();

            List<Relationship> createdRelationships = new ArrayList<>();

            inventory.observable(Interest.in(Relationship.class).being(created())).subscribe(createdRelationships::add);

            inventory.tenants().get("t").environments().get("e").feedlessResources().get("r").metrics().associate(
                    m.getPath());

            Assert.assertEquals(1, createdRelationships.size());

            inventory.tenants().get("t").environments().get("e").feedlessResources().delete("r");
        });
    }

    @Test
    public void testBackendFind() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        CanonicalPath tenantPath = CanonicalPath.of().tenant("com.acme.tenant").get();

        E entity = backend.find(tenantPath);
        Tenant tenant = backend.convert(entity, Tenant.class);
        Assert.assertEquals("com.acme.tenant", tenant.getId());

        CanonicalPath envPath = tenantPath.extend(Environment.class, "production").get();
        entity = backend.find(envPath);
        Environment env = backend.convert(entity, Environment.class);
        Assert.assertEquals("com.acme.tenant", env.getTenantId());
        Assert.assertEquals("production", env.getId());

        entity = backend.find(envPath.extend(Resource.class, "host1").get());
        Resource r = backend.convert(entity, Resource.class);
        Assert.assertEquals("com.acme.tenant", r.getTenantId());
        Assert.assertEquals("production", r.getEnvironmentId());
        Assert.assertNull(r.getFeedId());
        Assert.assertEquals("host1", r.getId());

        entity = backend.find(envPath.extend(Metric.class, "host1_ping_response").get());
        Metric m = backend.convert(entity, Metric.class);
        Assert.assertEquals("com.acme.tenant", m.getTenantId());
        Assert.assertEquals("production", m.getEnvironmentId());
        Assert.assertNull(m.getFeedId());
        Assert.assertEquals("host1_ping_response", m.getId());

        CanonicalPath feedPath = envPath.extend(Feed.class, "feed1").get();
        entity = backend.find(feedPath);
        Feed f = backend.convert(entity, Feed.class);
        Assert.assertEquals("com.acme.tenant", f.getTenantId());
        Assert.assertEquals("production", f.getEnvironmentId());
        Assert.assertEquals("feed1", f.getId());

        entity = backend.find(feedPath.extend(Resource.class, "feedResource1").get());
        r = backend.convert(entity, Resource.class);
        Assert.assertEquals("com.acme.tenant", r.getTenantId());
        Assert.assertEquals("production", r.getEnvironmentId());
        Assert.assertEquals("feed1", r.getFeedId());
        Assert.assertEquals("feedResource1", r.getId());

        entity = backend.find(feedPath.extend(Metric.class, "feedMetric1").get());
        m = backend.convert(entity, Metric.class);
        Assert.assertEquals("com.acme.tenant", m.getTenantId());
        Assert.assertEquals("production", m.getEnvironmentId());
        Assert.assertEquals("feed1", m.getFeedId());
        Assert.assertEquals("feedMetric1", m.getId());

        entity = backend.find(tenantPath.extend(ResourceType.class, "URL").get());
        ResourceType rt = backend.convert(entity, ResourceType.class);
        Assert.assertEquals("com.acme.tenant", rt.getTenantId());
        Assert.assertEquals("URL", rt.getId());

        entity = backend.find(tenantPath.extend(MetricType.class, "ResponseTime").get());
        MetricType mt = backend.convert(entity, MetricType.class);
        Assert.assertEquals("com.acme.tenant", mt.getTenantId());
        Assert.assertEquals("ResponseTime", mt.getId());
    }

    @Test
    public void testBackendGetRelationship() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        E tenant = backend.find(CanonicalPath.of().tenant("com.acme.tenant").get());
        E environment = backend.find(CanonicalPath.of().tenant("com.acme.tenant").environment("production").get());
        E r = backend.getRelationship(tenant, environment, contains.name());

        Relationship rel = backend.convert(r, Relationship.class);

        Assert.assertEquals("com.acme.tenant", rel.getSource().getSegment().getElementId());
        Assert.assertEquals("production", rel.getTarget().getSegment().getElementId());
        Assert.assertEquals("contains", rel.getName());
    }

    @Test
    public void testBackendGetRelationships() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        E entity = backend.find(CanonicalPath.of().tenant("com.acme.tenant").get());
        Assert.assertEquals("com.acme.tenant", backend.extractId(entity));
        Set<E> rels = backend.getRelationships(entity, both);
        Assert.assertEquals(3, rels.size());

        Function<Set<E>, Stream<Relationship>> checks = (es) -> es.stream().map((e) -> backend.convert(e,
                Relationship.class));

        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "production".equals(r.getTarget().getSegment().getElementId())));
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "URL".equals(r.getTarget().getSegment().getElementId())));
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "ResponseTime".equals(r.getTarget().getSegment().getElementId())));

        rels = backend.getRelationships(entity, incoming);
        Assert.assertTrue(rels.isEmpty());

        rels = backend.getRelationships(entity, outgoing);
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "production".equals(r.getTarget().getSegment().getElementId())));
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "URL".equals(r.getTarget().getSegment().getElementId())));
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "ResponseTime".equals(r.getTarget().getSegment().getElementId())));

        entity = backend.find(CanonicalPath.of().tenant("com.example.tenant").environment("test").get());
        Assert.assertEquals("test", backend.extractId(entity));

        rels = backend.getRelationships(entity, incoming);
        Assert.assertEquals(2, rels.size());
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> "contains".equals(r.getName()) &&
                "com.example.tenant".equals(r.getSource().getSegment().getElementId())));
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> "yourMom".equals(r.getName()) &&
                "playroom2_size".equals(r.getSource().getSegment().getElementId())));

        rels = backend.getRelationships(entity, outgoing, "IamYourFather");
        Assert.assertEquals(1, rels.size());
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> "IamYourFather".equals(r.getName()) &&
                "playroom2_size".equals(r.getTarget().getSegment().getElementId())));

    }

    @Test
    public void testBackendGetTransitiveClosure() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        TriFunction<E, String, Relationships.Direction, Stream<? extends AbstractElement<?, ?>>> test =
                (start, name, direction) -> {
                    Iterator<E> transitiveClosure = backend.getTransitiveClosureOver(start, name, direction);
                    return StreamSupport.stream(Spliterators.spliterator(transitiveClosure, Integer.MAX_VALUE, 0),
                            false).map((e) -> backend.convert(e, backend.extractType(e)));
                };

        E env = backend.find(CanonicalPath.of().tenant("com.acme.tenant").environment("production").get());
        E feed = backend.find(CanonicalPath.of().tenant("com.acme.tenant").environment("production").feed("feed1")
                .get());

        Assert.assertEquals(4, test.apply(feed, "contains", outgoing).count());
        Assert.assertFalse(test.apply(feed, "contains", outgoing).anyMatch((e) -> e instanceof Feed &&
                "feed1".equals(e.getId())));
        Assert.assertTrue(test.apply(env, "contains", outgoing).anyMatch((e) -> e instanceof Resource &&
                "feedResource1".equals(e.getId())));
        Assert.assertTrue(test.apply(env, "contains", outgoing).anyMatch((e) -> e instanceof Resource &&
                "feedResource2".equals(e.getId())));
        Assert.assertTrue(test.apply(env, "contains", outgoing).anyMatch((e) -> e instanceof Resource &&
                "feedResource3".equals(e.getId())));
        Assert.assertTrue(test.apply(env, "contains", outgoing).anyMatch((e) -> e instanceof Metric &&
                "feedMetric1".equals(e.getId())));

        Assert.assertEquals(1, test.apply(env, "contains", incoming).count());
        Assert.assertTrue(test.apply(env, "contains", incoming).anyMatch((e) -> e instanceof Tenant &&
                "com.acme.tenant".equals(e.getId())));
    }

    @Test
    public void testBackendHasRelationship() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        E tenant = backend.find(CanonicalPath.of().tenant("com.example.tenant").get());

        Assert.assertTrue(backend.hasRelationship(tenant, outgoing, "contains"));
        Assert.assertFalse(backend.hasRelationship(tenant, incoming, "contains"));
        Assert.assertTrue(backend.hasRelationship(tenant, both, "contains"));

        E env = backend.find(CanonicalPath.of().tenant("com.example.tenant").environment("test").get());

        Assert.assertTrue(backend.hasRelationship(tenant, env, "contains"));
        Assert.assertFalse(backend.hasRelationship(tenant, env, "kachny"));
    }

    @Test
    public void testBackendExtractId() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        E tenant = backend.find(CanonicalPath.of().tenant("com.example.tenant").get());

        Assert.assertEquals("com.example.tenant", backend.extractId(tenant));
    }

    @Test
    public void testBackendExtractType() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        CanonicalPath tenantPath = CanonicalPath.of().tenant("com.acme.tenant").get();

        E entity = backend.find(tenantPath);
        Assert.assertEquals(Tenant.class, backend.extractType(entity));

        CanonicalPath envPath = tenantPath.extend(Environment.class, "production").get();
        entity = backend.find(envPath);
        Assert.assertEquals(Environment.class, backend.extractType(entity));

        entity = backend.find(envPath.extend(Resource.class, "host1").get());
        Assert.assertEquals(Resource.class, backend.extractType(entity));

        entity = backend.find(envPath.extend(Metric.class, "host1_ping_response").get());
        Assert.assertEquals(Metric.class, backend.extractType(entity));

        CanonicalPath feedPath = envPath.extend(Feed.class, "feed1").get();
        entity = backend.find(feedPath);
        Assert.assertEquals(Feed.class, backend.extractType(entity));

        entity = backend.find(feedPath.extend(Resource.class, "feedResource1").get());
        Assert.assertEquals(Resource.class, backend.extractType(entity));

        entity = backend.find(feedPath.extend(Metric.class, "feedMetric1").get());
        Assert.assertEquals(Metric.class, backend.extractType(entity));

        entity = backend.find(tenantPath.extend(ResourceType.class, "URL").get());
        Assert.assertEquals(ResourceType.class, backend.extractType(entity));

        entity = backend.find(tenantPath.extend(MetricType.class, "ResponseTime").get());
        Assert.assertEquals(MetricType.class, backend.extractType(entity));
    }

    @Test
    public void testBackendQuery() throws Exception {
        InventoryBackend<E> backend = inventory.getBackend();

        Pager unlimited = Pager.unlimited(Order.unspecified());

        Query q = Query.path().with(type(Tenant.class), id("com.acme.tenant")).get();
        Page<E> results = backend.query(q, unlimited);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("com.acme.tenant", backend.extractId(results.get(0)));

        q = Query.path().with(type(Tenant.class), id("com.acme.tenant"), Related.by("contains"),
                type(Environment.class), id("production")).get();
        results = backend.query(q, unlimited);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("production", backend.extractId(results.get(0)));

        // equivalent to inventory.tenants().getAll(Related.by("contains"), type(ResourceType.class, id("URL"))
        // .environments().getAll().entities();
        q = Query.path().with(type(Tenant.class)).filter().with(Related.by("contains"), type(ResourceType.class),
                id("URL")).path().with(Related.by("contains"), type(Environment.class)).get();
        results = backend.query(q, unlimited);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("production", backend.extractId(results.get(0)));

    }

    private <T extends AbstractElement<?, U>, U extends AbstractElement.Update>
    void runObserverTest(Class<T> entityClass, int nofCreatedRelationships, int nofDeletedRelationships,
            Runnable payload) {

        List<T> createdEntities = new ArrayList<>();
        List<Action.Update<T, U>> updatedEntities = new ArrayList<>();
        List<T> deletedEntities = new ArrayList<>();
        List<Relationship> createdRelationships = new ArrayList<>();
        List<Relationship> deletedRelationships = new ArrayList<>();

        Subscription s1 = inventory.observable(Interest.in(entityClass).being(created()))
                .subscribe(createdEntities::add);

        Subscription s2 = inventory.observable(Interest.in(entityClass).being(updated()))
                .subscribe(updatedEntities::add);

        Subscription s3 = inventory.observable(Interest.in(entityClass).being(deleted()))
                .subscribe(deletedEntities::add);

        inventory.observable(Interest.in(Relationship.class).being(created())).subscribe(createdRelationships::add);
        inventory.observable(Interest.in(Relationship.class).being(deleted())).subscribe(deletedRelationships::add);

        //dummy observer just to check that unsubscription works
        inventory.observable(Interest.in(entityClass).being(created())).subscribe((t) -> {
        });

        payload.run();

        Assert.assertEquals(1, createdEntities.size());
        Assert.assertEquals(1, updatedEntities.size());
        Assert.assertEquals(1, deletedEntities.size());
        Assert.assertEquals(nofCreatedRelationships, createdRelationships.size());
        Assert.assertEquals(nofDeletedRelationships, deletedRelationships.size());

        s1.unsubscribe();
        s2.unsubscribe();
        s3.unsubscribe();

        Assert.assertTrue(inventory.hasObservers(Interest.in(entityClass).being(created())));
        Assert.assertFalse(inventory.hasObservers(Interest.in(entityClass).being(updated())));
        Assert.assertFalse(inventory.hasObservers(Interest.in(entityClass).being(deleted())));
    }

    private interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    private interface TetraFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w);
    }
}
