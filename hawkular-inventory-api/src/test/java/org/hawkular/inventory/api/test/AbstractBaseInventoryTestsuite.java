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
package org.hawkular.inventory.api.test;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.Action.updated;
import static org.hawkular.inventory.api.OperationTypes.DataRole.returnType;
import static org.hawkular.inventory.api.Relationships.Direction.both;
import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.Relationships.WellKnown.isParentOf;
import static org.hawkular.inventory.api.ResourceTypes.DataRole.configurationSchema;
import static org.hawkular.inventory.api.ResourceTypes.DataRole.connectionConfigurationSchema;
import static org.hawkular.inventory.api.Resources.DataRole.configuration;
import static org.hawkular.inventory.api.Resources.DataRole.connectionConfiguration;
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
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.FeedAlreadyRegisteredException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.IdentityHash;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.Parents;
import org.hawkular.inventory.api.PathFragment;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.ValidationException;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.BaseInventory;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import rx.Subscription;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public abstract class AbstractBaseInventoryTestsuite<E> {
    protected BaseInventory<E> inventory;

    protected abstract BaseInventory<E> getInventoryForTest();

    @Before
    public final void setupData() throws Exception {
        inventory = getInventoryForTest();
        setupData(inventory);
    }

    @After
    public final void teardownData() throws Exception {
        teardownData(inventory);
    }

    protected static <E> void setupNewInventory(BaseInventory<E> inventory) throws Exception {
        Properties ps = new Properties();
        try (FileInputStream f = new FileInputStream(System.getProperty("graph.config"))) {
            ps.load(f);
        }

        Configuration config = Configuration.builder().withFeedIdStrategy(
                new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
                .withConfiguration(ps)
                .build();

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
    }

    private static <E> void setupData(BaseInventory<E> inventory) throws Exception {
        //noinspection AssertWithSideEffects
        assert inventory.tenants()
                .create(Tenant.Blueprint.builder().withId("com.acme.tenant").withProperty("kachny", "moc").build())
                .entity().getId().equals("com.acme.tenant");
        assert inventory.tenants().get("com.acme.tenant").environments().create(new Environment.Blueprint("production"))
                .entity().getId().equals("production");
        assert inventory.tenants().get("com.acme.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("URL")).entity().getId().equals("URL");
        assert inventory.tenants().get("com.acme.tenant").metricTypes()
                .create(new MetricType.Blueprint("ResponseTime",
                     MetricUnit.MILLISECONDS, MetricDataType.COUNTER, 0L)).entity().getId().equals("ResponseTime");

        inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL").metricTypes()
                .associate(CanonicalPath.of().tenant("com.acme.tenant").metricType("ResponseTime").get());

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").metrics()
                .create(new Metric.Blueprint("/ResponseTime", "host1_ping_response")).entity().getId()
                .equals("host1_ping_response");
        assert inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .create(new Resource.Blueprint("host1", "/URL")).entity()
                .getId().equals("host1");
        inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .get("host1").allMetrics().associate(RelativePath.fromString("../m;host1_ping_response"));

        assert inventory.tenants().get("com.acme.tenant").feeds()
                .create(new Feed.Blueprint("feed1", null)).entity().getId().equals("feed1");
        inventory.tenants().get("com.acme.tenant").environments().get("production").feeds()
                .associate(RelativePath.fromString("../f;feed1"));

        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource1", "/URL")).entity().getId()
                .equals("feedResource1");

        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource2", "/URL")).entity().getId()
                .equals("feedResource2");

        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource3", "/URL")).entity().getId()
                .equals("feedResource3");

        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
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
                .create(new MetricType.Blueprint("Size", MetricUnit.BYTES, MetricDataType.COUNTER, 0L))
                    .entity().getId().equals("Size");
        inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").metricTypes()
                .associate(RelativePath.to().up().metricType("Size").get());

        assert inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .create(new Metric.Blueprint("/Size", "playroom1_size")).entity().getId().equals("playroom1_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .create(new Metric.Blueprint("/Size", "playroom2_size")).entity().getId().equals("playroom2_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .create(new Resource.Blueprint("playroom1", "/Playroom")).entity().getId()
                .equals("playroom1");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .create(new Resource.Blueprint("playroom2", "/Playroom")).entity().getId()
                .equals("playroom2");

        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom1").allMetrics().associate(RelativePath.to().up().metric("playroom1_size").get());
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom2").allMetrics().associate(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").metric("playroom2_size").get());

        assert inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom1").resources()
                .create(new Resource.Blueprint("playroom1.1", "/Playroom")).entity().getId()
                .equals("playroom1.1");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom1").resources()
                .create(new Resource.Blueprint("playroom1.2", "/Playroom")).entity().getId()
                .equals("playroom1.2");
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom1").allResources().associate(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").resource("playroom2").get());
        assert inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom2").resources()
                .create(new Resource.Blueprint("playroom2.1", "/Playroom")).entity().getId()
                .equals("playroom2.1");

        // some ad-hoc relationships
        Environment test = inventory.tenants().get("com.example.tenant").environments().get("test").entity();
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom2").allMetrics().get(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").metric("playroom2_size").get()).relationships(outgoing)
                .linkWith("yourMom", CanonicalPath.of().tenant("com.example.tenant").environment("test").get(), null);

        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom2").allMetrics().get(CanonicalPath.of().tenant("com.example.tenant")
                .environment("test").metric("playroom2_size").get()).relationships(incoming)
                .linkWith("IamYourFather", CanonicalPath.of().tenant("com.example.tenant").environment("test").get(),
                        new HashMap<String, Object>() {{
                            put("adult", true);
                        }});

        // setup configs
        StructuredData config = StructuredData.get().map()
                .putBool("yes", true)
                .putBool("no", false)
                .putIntegral("answer", 42L)
                .putFloatingPoint("approximateAnswer", 42.0)
                .putString("kachna", "moc")
                .putUndefined("nothingToSeeHere")
                .putList("primitives")
                /**/.addBool(true)
                /**/.addIntegral(1L)
                /**/.addFloatingPoint(2.0)
                /**/.addString("str")
                /**/.addUndefined()
                .closeList()
                .putMap("primitiveMap")
                /**/.putBool("bool", true)
                /**/.putFloatingPoint("float", 1.0)
                /**/.putIntegral("int", 2L)
                /**/.putString("str", "kachna")
                /**/.putUndefined("undef")
                .closeMap()
                .putList("listOfMaps")
                /**/.addMap()
                /**//**/.putBool("listOfMaps_bool", true)
                /**//**/.putString("listOfMaps_string", "kachny")
                /**/.closeMap()
                /**/.addMap()
                /**//**/.putFloatingPoint("listOfMaps_float", 1.0)
                /**/.closeMap()
                .closeList()
                .putList("listOfLists")
                /**/.addList()
                /**//**/.addBool(true)
                /**//**/.addIntegral(1L)
                /**/.closeList()
                .closeList()
                .putMap("mapOfLists")
                /**/.putList("list1")
                /**//**/.addString("ducks")
                /**//**/.addUndefined()
                /**/.closeList()
                /**/.putList("list2")
                /**//**/.addBool(false)
                /**//**/.addFloatingPoint(2.0)
                /**/.closeList()
                .closeMap()
                .putMap("mapOfMaps")
                /**/.putMap("map1")
                /**//**/.putIntegral("int", 42L)
                /**//**/.putString("answer", "probably")
                /**/.closeMap()
                /**/.putMap("map2")
                /**//**/.putFloatingPoint("float", 2.0)
                /**/.closeMap()
                .closeMap()
                .build();

        assert inventory.tenants().get("com.example.tenant").environments().get("test")
                .resources().get("playroom1").data().create(DataEntity.Blueprint.<Resources.DataRole>builder()
                        .withRole(configuration).withValue(config).build()).entity().getValue().equals(config);

        //create some config definitions...
        ResourceTypes.Single personType = inventory.tenants().get("com.acme.tenant").resourceTypes()
                .create(ResourceType.Blueprint.builder().withId("Person").build());

        personType.data().create(DataEntity.Blueprint
                .<ResourceTypes.DataRole>builder().withRole(configurationSchema).withValue(StructuredData.get().map()
                        .putString("title", "Person")
                        .putString("description", "Utterly complete description of a human.")
                        .putString("type", "object")
                        .putMap("properties")
                        /**/.putMap("firstName")
                        /**//**/.putString("type", "string")
                        /**/.closeMap()
                        /**/.putMap("lastName")
                        /**//**/.putString("type", "string")
                        /**/.closeMap()
                        /**/.putMap("age")
                        /**//**/.putString("description", "Age in years")
                        /**//**/.putString("type", "integer")
                        /**//**/.putIntegral("minimum", 0)
                        /**/.closeMap()
                        .closeMap()
                        .putList("required")
                        /**/.addString("firstName")
                        /**/.addString("lastName")
                        .closeList()
                        .build()).build());

        OperationTypes.Single startOp = personType.operationTypes().create(OperationType.Blueprint.builder().withId
                ("start").build());
        startOp.data().create(DataEntity.Blueprint.<OperationTypes.DataRole>builder()
                .withRole(returnType).withValue(StructuredData.get().map()
                        .putString("title", "start_returnType")
                        .putString("description", "start operation result")
                        .putString("type", "boolean")
                        .build()).build());
        startOp.data().create(DataEntity.Blueprint.<OperationTypes.DataRole>builder()
                .withRole(OperationTypes.DataRole.parameterTypes).withValue(StructuredData.get().map()
                        .putString("title", "start_paramTypes")
                        .putString("description", "start operation parameter types")
                        .putString("type", "object")
                        .putMap("properties")
                        /**/.putMap("quick")
                        /**//**/.putString("type", "boolean")
                        /**/.closeMap()
                        .closeMap()
                        .build()).build());


        //now create some resources with configs
        Resources.Single people = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .resources()
                .create(Resource.Blueprint.builder().withId("people").withResourceTypePath("/Person").build());

        people.resources().create(Resource.Blueprint.builder().withId("Alois").withResourceTypePath("/Person")
                .build()).data().create(DataEntity.Blueprint.<Resources.DataRole>builder().withRole(configuration)
                .withValue(StructuredData.get().map().putString("firstName", "Alois").putString("lastName", "Jirasek")
                        .build()).build());

        people.resources().create(Resource.Blueprint.builder().withId("Hynek").withResourceTypePath("/Person")
                .build()).data().create(DataEntity.Blueprint.<Resources.DataRole>builder().withRole(configuration)
                .withValue(StructuredData.get().map().putString("firstName", "Hynek").putString("lastName", "Macha")
                        .build()).build());
        people.resources().get("Hynek").resources().create(Resource.Blueprint.builder().withResourceTypePath("/Person")
                .withId("Vilem").build());
        people.resources().get("Hynek").resources().create(Resource.Blueprint.builder().withResourceTypePath("/Person")
                .withId("Jarmila").build());

        //create a metadata pack
        inventory.tenants().get("com.acme.tenant").resourceTypes().create(ResourceType.Blueprint.builder()
                .withId("mpRt").withName("Resource Type in a metadata pack").build());
        inventory.tenants().get("com.acme.tenant").resourceTypes().get("mpRt").data().create(
                DataEntity.Blueprint.<ResourceTypes.DataRole>builder()
                        .withRole(ResourceTypes.DataRole.configurationSchema)
                        .withValue(StructuredData.get().map().putString("title", "mpRtCs").putString("type",
                                "string").build()).build());
        inventory.tenants().get("com.acme.tenant").resourceTypes().get("mpRt").operationTypes().create(
                OperationType.Blueprint.builder().withId("mpRtOt").build());

        inventory.tenants().get("com.acme.tenant").resourceTypes().get("mpRt").operationTypes().get("mpRtOt")
                .data().create(DataEntity.Blueprint.<OperationTypes.DataRole>builder().withRole(returnType)
                .withValue(StructuredData.get().map().putString("title", "mpRtOtRet")
                        .putString("type", "string").build()).build());

        inventory.tenants().get("com.acme.tenant").metricTypes().create(
                MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("mpMt").withUnit(MetricUnit.NONE)
                        .withInterval(0L).build());

        inventory.tenants().get("com.acme.tenant").metadataPacks().create(MetadataPack.Blueprint.builder()
                .withMembers(CanonicalPath.fromString("/t;com.acme.tenant/rt;mpRt"),
                        CanonicalPath.fromString("/t;com.acme.tenant/mt;mpMt")).build());

        //resource-owned metrics
        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
                .resources().get("feedResource3").metrics().create(Metric.Blueprint.builder()
                        .withId("feedResource3-metric").withMetricTypePath("/ResponseTime").build()).entity().getId()
                .equals("feedResource3-metric");

        //feed-owned resource types and metric types
        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1").resourceTypes().create(
                ResourceType.Blueprint.builder().withId("feed1-resourceType").build()).entity().getId()
                .equals("feed1-resourceType");

        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1").metricTypes().create(
                MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("feed1-metricType").withUnit(MetricUnit.NONE)
                .withInterval(0L).build()).entity().getId().equals("feed1-metricType");

        assert inventory.tenants().get("com.acme.tenant").feeds().get("feed1").resources().get("feedResource1")
                .resources().create(Resource.Blueprint.builder().withId("feedChildResource").withResourceTypePath
                        ("/URL").build()).entity().getId().equals("feedChildResource");
    }

    private static <E> void teardownData(BaseInventory<E> inventory) throws Exception {
        CanonicalPath tenantPath = CanonicalPath.of().tenant("com.example.tenant").get();
        CanonicalPath environmentPath = tenantPath.extend(Environment.class, "test").get();

        try {
            Tenant t = new Tenant(tenantPath);
            Environment e = new Environment(environmentPath);
            MetricType sizeType = new MetricType(tenantPath.extend(MetricType.class, "Size").get());
            ResourceType playRoomType = new ResourceType(tenantPath.extend(ResourceType.class, "Playroom").get(), null);
            ResourceType kachnaType = new ResourceType(tenantPath.extend(ResourceType.class, "Kachna").get(), null);
            Resource playroom1 = new Resource(environmentPath.extend(Resource.class, "playroom1").get(),
                    playRoomType);
            Resource playroom2 = new Resource(environmentPath.extend(Resource.class, "playroom2").get(),
                    playRoomType);
            Metric playroom1Size = new Metric(environmentPath.extend(Metric.class, "playroom1_size").get(),
                    sizeType);
            Metric playroom2Size = new Metric(environmentPath.extend(Metric.class, "playroom2_size").get(),
                    sizeType);

            //when an association is deleted, it should not be possible to access the target entity through the same
            //traversal again
            inventory.inspect(playroom2).allMetrics().disassociate(playroom2Size.getPath());
            Assert.assertFalse(inventory.inspect(playroom2).allMetrics().get(playroom2Size.getPath()).exists());
            Assert.assertFalse(inventory.inspect(playroom2).allMetrics().get(
                    RelativePath.to().up().metric(playroom2Size.getId()).get()).exists());

            inventory.inspect(playroom2Size).delete();

            assertDoesNotExist(inventory, playroom2Size);
            assertExists(inventory, t, e, sizeType, playRoomType, kachnaType, playroom1, playroom2, playroom1Size);

            //disassociation using a relative path should work, too
            inventory.inspect(playroom1).allMetrics().disassociate(RelativePath.to().up().metric(playroom1Size.getId())
                    .get());
            Assert.assertFalse(inventory.inspect(playroom1).allMetrics().get(playroom1Size.getPath()).exists());
            Assert.assertFalse(inventory.inspect(playroom1).allMetrics().get(
                    RelativePath.to().up().metric(playroom1Size.getId()).get()).exists());

            inventory.inspect(t).resourceTypes().delete(kachnaType.getId());
            assertDoesNotExist(inventory, kachnaType);
            assertExists(inventory, t, e, sizeType, playRoomType, playroom1, playroom2, playroom1Size);

            try {
                inventory.inspect(t).metricTypes().delete(sizeType.getId());
                Assert.fail("Deleting a metric type which references some metrics should not be possible.");
            } catch (IllegalArgumentException ignored) {
                //good
            }

            inventory.inspect(e).metrics().delete(playroom1Size.getId());
            assertDoesNotExist(inventory, playroom1Size);
            assertExists(inventory, t, e, sizeType, playRoomType, playroom1, playroom2);

            inventory.inspect(t).metricTypes().delete(sizeType.getId());
            assertDoesNotExist(inventory, sizeType);
            assertExists(inventory, t, e, playRoomType, playroom1, playroom2);

            try {
                inventory.inspect(t).resourceTypes().delete(playRoomType.getId());
                Assert.fail("Deleting a resource type which references some resources should not be possible.");
            } catch (IllegalArgumentException ignored) {
                //good
            }

            inventory.inspect(e).resources().delete(playroom1.getId());
            assertDoesNotExist(inventory, playroom1);
            assertExists(inventory, t, e, playRoomType, playroom2);

            inventory.tenants().delete(t.getId());
            assertDoesNotExist(inventory, t);
            assertDoesNotExist(inventory, e);
            assertDoesNotExist(inventory, playRoomType);
            assertDoesNotExist(inventory, playroom2);
        } finally {
            Tenants.Single tenant = inventory.inspect(tenantPath, Tenants.Single.class);
            if (tenant.exists()) {
                tenant.delete();
            }

            inventory.tenants().delete("com.acme.tenant");
        }
    }

    private static void assertDoesNotExist(BaseInventory<?> inventory, Entity e) {
        try {
            inventory.inspect(e, ResolvableToSingle.class).entity();
            Assert.fail(e + " should have been deleted");
        } catch (EntityNotFoundException ignored) {
            //good
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertExists(BaseInventory<?> inventory, Entity<?, ?> e) {
        Assert.assertTrue("Entity should exist: " + e, inventory.inspect(e, ResolvableToSingle.class).exists());
    }

    private static void assertExists(BaseInventory<?> inventory, Entity... es) {
        Stream.of(es).forEach(e -> assertExists(inventory, e));
    }

    @Test
    public void testTenants() throws Exception {
        Function<String, Void> test = (id) -> {
            Query query = Query.empty().asBuilder()
                    .with(PathFragment.from(type(Tenant.class), id(id))).build();

            Page<E> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

            Assert.assertTrue(results.hasNext());

            E tenant = results.next();

            Assert.assertTrue(!results.hasNext());

            String eid = inventory.getBackend().extractId(tenant);

            Assert.assertEquals(id, eid);

            return null;
        };

        test.apply("com.acme.tenant");
        test.apply("com.example.tenant");

        Query query = Query.empty().asBuilder()
                .with(PathFragment.from(type(Tenant.class))).build();

        Page<E> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

        Assert.assertTrue(results.hasNext());
        results.next();
        Assert.assertTrue(results.hasNext());
        results.next();
        Assert.assertTrue(!results.hasNext());
    }

    @Test
    public void testEntitiesByRelationships() throws Exception {
        Function<Integer, Function<Class<? extends Entity<?, ?>>, Function<String, Function<Integer,
                Function<Class<? extends Entity<?, ?>>, Function<ResolvableToMany<?>,
                        Consumer<ResolvableToMany<?>>>>>>>>
                testHelper = (numberOfParents -> parentType -> edgeLabel -> numberOfKids -> childType ->
                multipleParents -> multipleChildren -> {
                    InventoryBackend<E> backend = inventory.getBackend();

                    Page<E> parents = backend.query(Query.path().with(type(parentType)).get(),
                            Pager.unlimited(Order.unspecified()));
                    List<E> parentsList = parents.toList();

                    Page<E> children = backend.query(Query.path().with(type(parentType),
                            by(edgeLabel), type(childType)).get(), Pager.unlimited(Order.unspecified()));
                    List<E> childrenList = children.toList();

                    Assert.assertEquals("There must be exactly " + numberOfParents + " " + parentType + "s " + "that " +
                            "have outgoing edge labeled with " + edgeLabel + ". Backend query returned only " +
                            parentsList.size(), (int) numberOfParents, parentsList.size());

                    Assert.assertEquals("There must be exactly " +
                            numberOfParents + " " + parentType + "s that have outgoing edge labeled with " + edgeLabel +
                            ". Tested API returned only " + multipleParents.entities().size(), (int) numberOfParents,
                            multipleParents.entities().size());

                    Assert.assertEquals("There must be exactly " + numberOfKids + " " + childType +
                            "s that are directly under " + parentType + " connected with " + edgeLabel +
                            ". Gremlin query returned only " + childrenList.size(), (int) numberOfKids,
                            childrenList.size());

                    Assert.assertEquals((int) numberOfKids, multipleChildren.entities().size());
                });

        ResolvableToMany parents = inventory.tenants().getAll(by("contains"));
        ResolvableToMany kids = inventory.tenants().getAll().environments().getAll(asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(2).apply(Environment.class).apply(parents)
                .accept(kids);

        kids = inventory.tenants().getAll().resourceTypes().getAll(asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(5).apply(ResourceType.class).apply(parents)
                .accept(kids);

        kids = inventory.tenants().getAll().metricTypes().getAll(asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(3).apply(MetricType.class).apply(parents)
                .accept(kids);

        parents = inventory.tenants().getAll().environments().getAll(by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().metrics().getAll(
                asTargetBy("contains"));
        testHelper.apply(2).apply(Environment.class).apply("contains").apply(3).apply(Metric.class).apply(parents).
                accept(kids);

        kids = inventory.tenants().getAll().environments().getAll().resources().getAll(
                asTargetBy("contains"));
        testHelper.apply(2).apply(Environment.class).apply("contains").apply(4).apply(Resource.class).apply(parents)
                .accept(kids);

        parents = inventory.tenants().getAll().metricTypesUnder(Parents.any()).getAll();
        kids = inventory.tenants().getAll().environments().getAll().metricsUnder(Parents.any()).getAll();
        testHelper.apply(4).apply(MetricType.class).apply("defines").apply(5).apply(Metric.class).apply(parents)
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
                .metrics().get("playroom2_size").relationships(outgoing).named("yourMom").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getTarget().getSegment().getElementId())
                : "Target of relationship 'yourMom' should be the 'test' environment";

        rels = inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .get("playroom2_size").relationships(both).named("IamYourFather").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getSource().getSegment().getElementId())
                : "Source of relationship 'IamYourFather' should be the 'test' environment";
    }

    @Test
    public void testRelationshipServiceLinkedWithAndDelete() throws Exception {
        Relationship link = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .resources().get("host1").relationships(incoming)
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
        Relationship rel1 = inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .get("playroom2_size").relationships(outgoing).named("yourMom").entities().iterator().next();
        assert null == rel1.getProperties().get(someKey) : "There should not be any property with key 'k3y'";

        Relationship rel2 = inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .get("playroom2_size").relationships(outgoing).named("yourMom").entities().iterator().next();
        assert rel1.getId().equals(rel2.getId()) && null == rel2.getProperties().get(someKey) : "There should not be" +
                " any property with key 'k3y'";

        // persist the change
        inventory.tenants().get("com.example.tenant").environments().get("test").metrics().get("playroom2_size")
                .relationships(outgoing).update(rel1.getId(), Relationship.Update.builder()
                .withProperty(someKey, someValue).build());

        Relationship rel3 = inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
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
        assert rels != null && rels.size() == 7 : "There should be 6 relationships conforming the filters";
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
        assert resources.stream().allMatch(res -> Arrays.asList("playroom1", "playroom2", "playroom1.1", "playroom1.2",
                "playroom2.1").contains(res.getId())) : "ResourceType[Playroom] -defines-> resources called playroom*";

        resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named("incorporates").resources().getAll().entities(); // empty
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

            Assert.assertTrue(envs.hasNext());


            //query, we should get the same results
            Environment env = inventory.tenants().get(tenantId).environments().get(id).entity();
            Assert.assertEquals(id, env.getId());

            env = inventory.getBackend().convert(envs.next(), Environment.class);
            Assert.assertTrue(!envs.hasNext());
            Assert.assertEquals(id, env.getId());

            return null;
        };

        test.apply("com.acme.tenant", "production");
        test.apply("com.example.tenant", "test");

        Query q = Query.empty().asBuilder()
                .with(PathFragment.from(type(Environment.class))).build();

        Assert.assertEquals(2, inventory.getBackend().query(q, Pager.unlimited(Order.unspecified())).toList().size());
    }

    @Test
    public void testResourceTypes() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            Query query = Query.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(ResourceType.class), id(id)).get();

            Page<?> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

            Assert.assertTrue(results.hasNext());

            ResourceType rt = inventory.tenants().get(tenantId).resourceTypes().get(id).entity();
            assert rt.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL");
        test.apply("com.example.tenant", "Kachna");
        test.apply("com.example.tenant", "Playroom");

        Query query = Query.path().with(type(ResourceType.class)).get();
        Assert.assertEquals(6, inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).toList()
                .size());
    }

    @Test
    public void testMetricDefinitions() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            Query query = Query.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(MetricType.class), id(id)).get();

            assert inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).hasNext();

            MetricType md = inventory.tenants().get(tenantId).metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "ResponseTime");
        test.apply("com.example.tenant", "Size");

        Query query = Query.path().with(type(MetricType.class)).get();
        Assert.assertEquals(4, inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).toList()
                .size());
    }

    @Test
    public void testMetricTypesLinkedToResourceTypes() throws Exception {
        TetraFunction<String, String, String, CanonicalPath, Void> test = (tenantId, resourceTypeId, id, path) -> {

            Query q = Query.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(ResourceType.class), id(resourceTypeId), by(incorporates),
                    type(MetricType.class), id(id)).get();

            assert inventory.getBackend().query(q, Pager.unlimited(Order.unspecified())).hasNext();

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

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).metrics()
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

        Assert.assertEquals(5, inventory.getBackend().query(Query.path().with(type(Metric.class)).get(),
                Pager.unlimited(Order.unspecified())).toList().size());
    }

    @Test
    public void testResources() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceTypeId, id) -> {
            Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
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


        Assert.assertEquals(15, inventory.getBackend().query(Query.path().with(type(Resource.class)).get(),
                Pager.unlimited(Order.unspecified())).toList().size());
    }

    @Test
    public void testResourcesFilteredByTypeProperty() throws Exception {
        Set<Resource> resources = inventory.tenants().get("com.example.tenant").environments().get("test")
                .resources().getAll(new Filter[][]{
                        {Defined.by(CanonicalPath.of().tenant("com.example.tenant").resourceType("Playroom").get()),
                                With.propertyValue("ownedByDepartment", "Facilities")},
                }).entities();
        Assert.assertEquals(2, resources.size());
        Assert.assertEquals(new HashSet<>(asList("playroom1", "playroom2")),
                resources.stream().map(AbstractElement::getId).collect(toSet()));

        resources = inventory.tenants().get("com.example.tenant").environments().get("test")
                .resources().getAll(new Filter[][]{
                        {Defined.by(CanonicalPath.of().tenant("com.example.tenant").resourceType("Playroom").get()),
                                With.propertyValue("ownedByDepartment", "kachny")},
                }).entities();
        Assert.assertTrue(resources.isEmpty());

        resources = inventory.tenants().get("com.example.tenant").environments().get("test")
                .resources().getAll(new Filter[][]{
                        {Defined.by(CanonicalPath.of().tenant("com.example.tenant").resourceType("Playroom").get()),
                                With.propertyValue("ownedByDepartment", "Facilities")},
                        {With.id("playroom1")}
                }).entities();
        Assert.assertEquals(1, resources.size());
        Assert.assertEquals("playroom1", resources.iterator().next().getId());

        resources = inventory.tenants().get("com.example.tenant").environments().get("test")
                .resources().getAll(
                        Defined.by(CanonicalPath.of().tenant("com.example.tenant").resourceType("Playroom").get()),
                        With.id("playroom1")).entities();
        Assert.assertEquals(1, resources.size());
        Assert.assertEquals("playroom1", resources.iterator().next().getId());
    }

    @Test
    public void testAssociateMetricWithResource() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceId, metricId) -> {
            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                    .get(resourceId).allMetrics().getAll(id(metricId)).entities().iterator().next();
            assert metricId.equals(m.getId());

            return null;
        };

        test.apply("com.acme.tenant", "production", "host1", "host1_ping_response");
        test.apply("com.example.tenant", "test", "playroom1", "playroom1_size");
        test.apply("com.example.tenant", "test", "playroom2", "playroom2_size");
    }

    @Test
    public void testOperationTypes() throws Exception {
        OperationTypes.Single ots = inventory.tenants().get("com.acme.tenant").resourceTypes()
                .get("Person").operationTypes().get("start");

        Assert.assertNotNull(ots.entity());

        //also test the inspect path
        ots = inventory.inspect(CanonicalPath.of().tenant("com.acme.tenant").resourceType("Person").operationType
                ("start").get(), OperationTypes.Single.class);

        Assert.assertEquals("start", ots.entity().getId());

        StructuredData returnTypeSchema = ots.data().get(returnType).entity().getValue();
        StructuredData parametersSchema = ots.data().get(OperationTypes.DataRole.parameterTypes).entity().getValue();

        Assert.assertEquals("start_returnType", returnTypeSchema.map().get("title").string());
        Assert.assertEquals("boolean", returnTypeSchema.map().get("type").string());

        Assert.assertEquals("start_paramTypes", parametersSchema.map().get("title").string());
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
        Assert.assertEquals(5, types.size());
    }

    @Test
    public void queryMultipleMetricDefs() throws Exception {
        Set<MetricType> types = inventory.tenants().getAll().metricTypes().getAll().entities();
        Assert.assertEquals(3, types.size());
    }

    @Test
    public void queryMultipleResources() throws Exception {
        Set<Resource> rs = inventory.tenants().getAll().environments().getAll().resources().getAll().entities();
        assert rs.size() == 4;
    }

    @Test
    public void queryMultipleMetrics() throws Exception {
        Set<Metric> ms = inventory.tenants().getAll().environments().getAll().metrics().getAll().entities();
        assert ms.size() == 3;
    }

    @Test
    public void testNoTwoFeedsWithSameID() throws Exception {
        Feeds.ReadWrite feeds = inventory.tenants().get("com.acme.tenant").feeds();

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
            inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
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

        Assert.assertEquals(2, r.getProperties().size());
    }

    @Test
    public void testPropertiesUpdateOnRelationshipsToEmpty() throws Exception {
        final String tenantName = "com.acme.tenant";
        Relationship r = inventory.tenants().get(tenantName).relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        inventory.tenants().get(tenantName).relationships().update(r.getId(),
                Relationship.Update.builder().withProperty("ducks", "many").withProperty("hammer", "nails").build());

        r = inventory.tenants().get(tenantName).relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        Assert.assertEquals(2, r.getProperties().size());
        Assert.assertEquals("many", r.getProperties().get("ducks"));
        Assert.assertEquals("nails", r.getProperties().get("hammer"));

        //reset the change we made back...
        inventory.tenants().get(tenantName).relationships().update(r.getId(),
                new Relationship.Update(new HashMap<String, Object>()));

        r = inventory.tenants().get(tenantName).relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        Assert.assertEquals(0, r.getProperties().size());
    }

    @Test
    public void testCreationMetricTypeWithProperties() {
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("p1", "1");
        properties.put("p2", "2");
        inventory.tenants().create(Tenant.Blueprint.builder().withId("testCreationMetricTypeWithProperties").build())
                .metricTypes().create(
                new MetricType.Blueprint("test", MetricUnit.BYTES, MetricDataType.COUNTER, properties, 0L));

        Assert.assertThat(inventory.tenants().get("testCreationMetricTypeWithProperties").metricTypes().get("test")
                .entity().getProperties().size(), equalTo(properties.size()));

        inventory.tenants().delete("testCreationMetricTypeWithProperties");
    }

    @Test
    public void testPaging() throws Exception {
        //the page is not modifiable but we'll need to modify this later on in the tests
        List<Metric> allResults = inventory.tenants().getAll().environments().getAll().metrics()
                .getAll().entities(Pager.unlimited(Order.by("id", Order.Direction.DESCENDING))).toList();

        assert allResults.size() == 3;

        Pager firstPage = new Pager(0, 1, Order.by("id", Order.Direction.DESCENDING));

        Metrics.Multiple metrics = inventory.tenants().getAll().environments().getAll().metrics().getAll();

        Page<Metric> ms = metrics.entities(firstPage);
        List<Metric> msList = ms.toList();
        assert msList.size() == 1;
        assert ms.getTotalSize() == 3;
        assert msList.get(0).equals(allResults.get(0));

        ms = metrics.entities(firstPage.nextPage());
        msList = ms.toList();
        assert msList.size() == 1;
        assert ms.getTotalSize() == 3;
        assert msList.get(0).equals(allResults.get(1));

        ms = metrics.entities(firstPage.nextPage().nextPage());
        msList = ms.toList();
        assert msList.size() == 1;
        assert ms.getTotalSize() == 3;
        assert msList.get(0).equals(allResults.get(2));

        ms = metrics.entities(firstPage.nextPage().nextPage().nextPage());
        msList = ms.toList();
        assert ms.getTotalSize() == 3;
        assert msList.size() == 0;

        //try the same with an unspecified order
        //the reason for checking this explicitly is that the order pipe implicitly loads
        //all elements before sending them on in the pipeline to the range filter.
        //If the order is not present, the elements might not be all loaded before the range
        //is overflown. The total still needs to match even in that case.
        firstPage = new Pager(0, 1, Order.unspecified());

        ms = metrics.entities(firstPage);
        msList = ms.toList();
        assert msList.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults
                .remove(msList.get(0)); //i.e. we check that the result is in all results and remove it from there
        //so that subsequent checks for the same thing cannot get confused by the
        //existence of this metric in all the results.

        ms = metrics.entities(firstPage.nextPage());
        msList = ms.toList();
        assert msList.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(msList.get(0));

        ms = metrics.entities(firstPage.nextPage().nextPage());
        msList = ms.toList();
        assert msList.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(msList.get(0));

        ms = metrics.entities(firstPage.nextPage().nextPage().nextPage());
        msList = ms.toList();
        assert ms.getTotalSize() == 3;
        assert msList.size() == 0;
    }

    @Test
    public void testGettingResourcesFromFeedsUsingEnvironments() throws Exception {
        Set<Resource> rs = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .resourcesUnder(Environments.ResourceParents.FEED).getAll().entities();

        Assert.assertEquals(3, rs.size());
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource1".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource2".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource3".equals(r.getId())));
    }

    @Test
    public void testGettingMetricsFromFeedsUsingEnvironments() throws Exception {
        Set<Metric> rs = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .metricsUnder(Parents.any()).getAll().entities();

        Assert.assertTrue(rs.stream().anyMatch((r) -> "host1_ping_response".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedMetric1".equals(r.getId())));
    }

    @Test
    public void testFilterByPropertyValues() throws Exception {
        Assert.assertTrue(inventory.tenants().getAll(With.property("kachny")).anyExists());
        Assert.assertFalse(inventory.tenants().getAll(With.property("v-kachna")).anyExists());
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
                .resources().get("playroom1");

        Pager pager = Pager.unlimited(Order.unspecified());

        Page<Resource> children = res.resources().getAll().entities(pager);
        Assert.assertEquals(2, children.toList().size());

        children = res.allResources().getAll().entities(pager);
        Assert.assertEquals(3, children.toList().size());

        children = res.allResources().getAll(Related.asTargetWith(res.entity().getPath(), isParentOf)).entities(pager);
        Assert.assertEquals(3, children.toList().size());
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
        Resource r = inventory.inspect(t, Tenants.Single.class).environments().get("test").resources()
                .get("playroom1").entity();

        try {
            inventory.inspect(r).allResources().associate(r.getPath());
            Assert.fail("Should not be able to create self-loop in isParentOf using resource association interface.");
        } catch (IllegalArgumentException e) {
            //good
        }

        //playroom1 -isParentOf> playroom2 exists. Let's try creating a loop
        Resource r2 = inventory.inspect(t, Tenants.Single.class).environments().get("test").resources()
                .get("playroom2").entity();

        try {
            inventory.inspect(r2).relationships(outgoing).linkWith(isParentOf, r.getPath(), null);
            Assert.fail("Should not be possible to create loops in isParentOf using generic relationships.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(r2).allResources().associate(r.getPath());
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
                .resources().get("playroom1").relationships().named(isParentOf).entities();

        for (Relationship r : rels) {
            if (r.getTarget().getSegment().getElementId().equals("playroom1.1")) {
                try {
                    inventory.tenants().get("com.example.tenant").environments().get("test")
                            .resources().get("playroom1").relationships().delete(r.getId());

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
        CanonicalPath envPath = CanonicalPath.fromString("/t;com.example.tenant/e;test");

        Environments.Single env = inventory.inspect(envPath, Environments.Single.class);
        Tenants.Single tenant = inventory.inspect(envPath.up(), Tenants.Single.class);

        Feed f = null;
        Resource r = null;
        Metric m1 = null;
        Metric m2 = null;

        try {
            r = env.resources().create(Resource.Blueprint.builder().withId("assocR1")
                    .withResourceTypePath("/rt;Playroom").build()).entity();

            //notice that m1 and m2 have the same ID, only a different path

            m1 = env.metrics().create(Metric.Blueprint.builder().withId("assocMetric")
                    .withMetricTypePath("/mt;Size").build()).entity();

            f = tenant.feeds().create(Feed.Blueprint.builder().withId("assocF").build()).entity();

            m2 = tenant.feeds().get("assocF").metrics().create(Metric.Blueprint.builder().withId("assocMetric")
                    .withMetricTypePath("/mt;Size").build()).entity();

            inventory.inspect(r).allMetrics().associate(m1.getPath());
            inventory.inspect(r).allMetrics().associate(m2.getPath());

            Assert.assertEquals(new HashSet<>(Arrays.asList(m1, m2)),
                    inventory.inspect(r).allMetrics().getAll().entities());

            inventory.inspect(r).allMetrics().disassociate(Path.fromString("../m;assocMetric"));

            Assert.assertEquals(Collections.singleton(m2), inventory.inspect(r).allMetrics().getAll().entities());
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
    public void testCannotCreateOrDeleteHasDataRelationship() throws Exception {
        Relationships.Multiple rels = inventory.relationships()
                .getAll(RelationWith.sourceOfType(DataEntity.class),
                        RelationWith.name(Relationships.WellKnown.hasData.name()));

        try {
            Relationship rel = rels.entities().iterator().next();

            //we actually shouldn't be able to get here, because hasData relationship's target is a structured data
            //which is not a legal target for a relationship.

            inventory.relationships().get(rel.getId()).delete();

            Assert.fail("Explicitly deleting hasData relationship shouldn't be possible.");
        } catch (IllegalArgumentException e) {
            // good
        }

        try {
            inventory.tenants().get("com.example.tenant").relationships()
                    .linkWith(hasData, CanonicalPath.of().tenant("com.example.tenant").resourceType("Playroom").get(),
                            null);
            Assert.fail("Explicitly creating hasData relationship shouldn't be possible.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testInspectChildResource() throws Exception {
        Resources.Single access = inventory.inspect(CanonicalPath.of().tenant("com.example.tenant").environment("test")
                .resource("playroom1").resource("playroom1.1").get(), Resources.Single.class);

        Assert.assertEquals("playroom1.1", access.entity().getId());
    }

    @Test
    public void testDescendChildResource() throws Exception {

        Resources.Single r = inventory.tenants().get("com.example.tenant").environments().get("test")
                .resources().descend("playroom1", RelativePath.fromString("../r;playroom2"))
                .allResources().get(RelativePath.fromString("r;playroom2.1"));

        Assert.assertEquals("playroom2.1", r.entity().getId());
    }

    @Test
    public void testAssociateEnvironmentWithFeeds() throws Exception {
        Assert.assertTrue(inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get(
                RelativePath.to().up().feed("feed1").get()).exists());

        inventory.tenants().get("com.acme.tenant").environments().create(
                Environment.Blueprint.builder().withId("staging").build());

        try {
            inventory.tenants().get("com.acme.tenant").environments().get("staging").feeds()
                    .associate(RelativePath.to().up().feed("feed1").get());
            Assert.fail("It should not be possible to associate a feed with more than 1 environment.");
        } catch (IllegalArgumentException e) {
            //good
        } finally {
            inventory.tenants().get("com.acme.tenant").environments().delete("staging");
        }
    }

    @Test
    public void testCreationUnderNonExistentParentThrowsEntityNotFoundException() throws Exception {
        try {
            inventory.tenants().get("com.acme.tenant").feeds().get("no-feed")
                    .resources().create(Resource.Blueprint.builder().withId("blah").withResourceTypePath("../../URL")
                    .build());
            Assert.fail("Should not be able to traverse over non-existent entity.");
        } catch (EntityNotFoundException e) {
            //good
        }
    }

    @Test
    public void testNamePersisted() throws Exception {
        Tenant t = null;
        try {
            t = inventory.tenants().create(Tenant.Blueprint.builder().withId("named-tenant").withName("tenant")
                    .build()).entity();
            Assert.assertEquals("tenant", t.getName());

            Environment e =
                    inventory.inspect(t).environments().create(Environment.Blueprint.builder().withId("named-env")
                            .withName("env").build()).entity();
            Assert.assertEquals("env", e.getName());

            Feed f = inventory.inspect(t).feeds().create(Feed.Blueprint.builder().withId("named-feed").withName("feed")
                    .build()).entity();
            Assert.assertEquals("feed", f.getName());

            ResourceType rt = inventory.inspect(t).resourceTypes().create(ResourceType.Blueprint.builder()
                    .withId("named-resourceType").withName("resourceType").build()).entity();
            Assert.assertEquals("resourceType", rt.getName());

            MetricType mt = inventory.inspect(t).metricTypes().create(MetricType.Blueprint
                    .builder(MetricDataType.GAUGE).withId("named-metricType").withUnit(MetricUnit.BITS)
                    .withName("metricType").withInterval(0L).build()).entity();
            Assert.assertEquals("metricType", mt.getName());

            OperationType ot = inventory.inspect(rt).operationTypes().create(OperationType.Blueprint.builder()
                    .withId("named-operationType").withName("operationType").build()).entity();
            Assert.assertEquals("operationType", ot.getName());

            Resource r = inventory.inspect(f).resources().create(Resource.Blueprint.builder().withId("named-resource")
                    .withName("resource").withResourceTypePath(rt.getPath().toString()).build()).entity();
            Assert.assertEquals("resource", r.getName());

            Metric m = inventory.inspect(f).metrics().create(Metric.Blueprint.builder().withId("named-metric")
                    .withName("metric").withMetricTypePath(mt.getPath().toString()).build()).entity();
            Assert.assertEquals("metric", m.getName());

            try {
                inventory.inspect(t).environments().create(Environment.Blueprint.builder().withId("failing-env")
                        .withProperty("name", "invalid-property").build());
                Assert.fail("Should not be possible to create an entity with a property called \"name\".");
            } catch (IllegalArgumentException ex) {
                //ok
            }
        } finally {
            if (t != null) {
                inventory.inspect(t).delete();
            }
        }
    }

    @Test
    public void testNameUpdatable() throws Exception {
        Tenant t = null;
        try {
            t = inventory.tenants().create(Tenant.Blueprint.builder().withId("named-tenant").withName("tenant")
                    .build()).entity();
            testUpdate(t, Tenant.Update.builder());

            Environment e = inventory.inspect(t).environments().create(Environment.Blueprint.builder()
                    .withId("named-env").withName("env").build()).entity();
            testUpdate(e, Environment.Update.builder());

            Feed f = inventory.inspect(t).feeds().create(Feed.Blueprint.builder().withId("named-feed").withName("feed")
                    .build()).entity();
            testUpdate(f, Feed.Update.builder());

            ResourceType rt = inventory.inspect(t).resourceTypes().create(ResourceType.Blueprint.builder()
                    .withId("named-resourceType").withName("resourceType").build()).entity();
            testUpdate(rt, ResourceType.Update.builder());

            MetricType mt = inventory.inspect(t).metricTypes().create(MetricType.Blueprint
                    .builder(MetricDataType.GAUGE).withId("named-metricType").withUnit(MetricUnit.BITS)
                    .withName("metricType").withInterval(0L).build()).entity();
            testUpdate(mt, MetricType.Update.builder());

            OperationType ot = inventory.inspect(rt).operationTypes().create(OperationType.Blueprint.builder()
                    .withId("named-operationType").withName("operationType").build()).entity();
            testUpdate(ot, OperationType.Update.builder());

            Resource r = inventory.inspect(f).resources().create(Resource.Blueprint.builder().withId("named-resource")
                    .withName("resource").withResourceTypePath(rt.getPath().toString()).build()).entity();
            testUpdate(r, Resource.Update.builder());

            Metric m = inventory.inspect(f).metrics().create(Metric.Blueprint.builder().withId("named-metric")
                    .withName("metric").withMetricTypePath(mt.getPath().toString()).build()).entity();
            testUpdate(m, Metric.Update.builder());

            try {
                inventory.inspect(m).update(Metric.Update.builder().withProperty("name", "disallowed").build());
                Assert.fail("A property called \"name\" should not be allowed in updates.");
            } catch (IllegalArgumentException ex) {
                //ok
            }
        } finally {
            if (t != null) {
                inventory.inspect(t).delete();
            }
        }
    }

    @Test
    public void testMetadataPackMembershipNonUpdatable() throws Exception {
        CanonicalPath tenantPath = CanonicalPath.of().tenant("com.acme.tenant").get();
        CanonicalPath rtPath = tenantPath.extend(ResourceType.class, "Person").get();

        MetadataPack pack = inventory.inspect(tenantPath, Tenants.Single.class).metadataPacks().getAll().entities()
                .iterator().next();

        try {
            inventory.inspect(pack).relationships().linkWith(incorporates, rtPath, null);
            Assert.fail("Adding a resource type to a metadatapack should not be possible after the pack has been " +
                    "created.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rtPath, ResourceTypes.Single.class).relationships(incoming).linkWith(incorporates,
                    pack.getPath(), null);
            Assert.fail("Adding a resource type to a metadatapack should not be possible after the pack has been " +
                    "created.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testModificationsOfEntitiesInMetadataPacksImpossible() throws Exception {
        Tenant t = inventory.tenants().get("com.acme.tenant").entity();
        MetricType mt = inventory.inspect(t).metricTypes().get("mpMt").entity();
        ResourceType rt = inventory.inspect(t).resourceTypes().get("mpRt").entity();

        try {
            inventory.inspect(mt).update(MetricType.Update.builder().withUnit(MetricUnit.PER_DAY).build());
            Assert.fail("Updating a metric type that is a part of metadata pack should not be possible.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).operationTypes().create(OperationType.Blueprint.builder().withId("asfd").build());
            Assert.fail("It should not be possible to add an operation type to a resource type in metadata pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).operationTypes().delete("mpRtOt");
            Assert.fail("It should not be possible to delete an operation type from a resource type in metadata pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).operationTypes().get("mpRtOt").delete();
            Assert.fail("It should not be possible to delete an operation type from a resource type in metadata pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).operationTypes().get("mpRtOt").data().create(DataEntity.Blueprint.<OperationTypes
                    .DataRole>builder().withRole(OperationTypes.DataRole.parameterTypes).withValue(StructuredData.get()
                    .undefined()).build());
            Assert.fail("It should not be possible to add data to operation type from a resource type in metadata " +
                    "pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).operationTypes().get("mpRtOt").data().update(returnType, DataEntity.Update.builder
                    ().withValue(StructuredData.get().bool(true)).build());
            Assert.fail("It should not be possible to modify data of operation type from a resource type in metadata " +
                    "pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).operationTypes().get("mpRtOt").data().delete(returnType);
            Assert.fail("It should not be possible to delete data of operation type from a resource type in metadata " +
                    "pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).data().create(
                    DataEntity.Blueprint.<ResourceTypes.DataRole>builder().withRole(connectionConfigurationSchema)
                            .withValue(StructuredData.get().bool(true)).build());

            Assert.fail("It should not be possible to add an data to a resource type in metadata pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).data().get(configurationSchema).delete();
            Assert.fail("It should not be possible to remove data from a resource type in metadata pack.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            inventory.inspect(rt).data().get(configurationSchema).update(DataEntity.Update.builder().withValue
                    (StructuredData.get().undefined()).build());
            Assert.fail("It should not be possible to modify data of a resource type in metadata pack.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testMetadataPackIdEqualToContentHash() throws Exception {
        MetricType mt = inventory.tenants().get("com.acme.tenant").metricTypes().get("mpMt").entity();
        ResourceType rt = inventory.tenants().get("com.acme.tenant").resourceTypes().get("mpRt").entity();
        OperationType ot = inventory.inspect(rt).operationTypes().get("mpRtOt").entity();

        StructuredData configSchema = inventory.inspect(rt).data().get(configurationSchema).entity().getValue();
        StructuredData retType = inventory.inspect(ot).data().get(returnType).entity().getValue();

        String expectedContentHash = IdentityHash.of(MetadataPack.Members.builder()
                .with(MetricType.Blueprint
                        .builder(mt.getType())
                        .withUnit(mt.getUnit())
                        .withInterval(0L)
                        .withId(mt.getId()).build())
                .with(ResourceType.Blueprint.builder().withId(rt.getId()).build())
                .with(DataEntity.Blueprint.<ResourceTypes.DataRole>builder()
                        .withRole(configurationSchema).withValue(configSchema).build())
                .with(OperationType.Blueprint.builder().withId(ot.getId()).build())
                .with(DataEntity.Blueprint.<OperationTypes.DataRole>builder()
                        .withRole(returnType).withValue(retType).build())
                .done()
                .done()
                .build());

        MetadataPack mp = inventory.tenants().get("com.acme.tenant").metadataPacks().get(expectedContentHash).entity();

        Assert.assertEquals(expectedContentHash, mp.getId());
    }

    private <T extends Entity<B, U>, B extends Blueprint, U extends Entity.Update>
    void testUpdate(T entity, Entity.Update.Builder<U, ?> update) {
        @SuppressWarnings("unchecked")
        Class<ResolvableToSingle<T, U>> cls = (Class<ResolvableToSingle<T, U>>) (Class) ResolvableToSingle.class;
        inventory.inspect(entity, cls).update(update.withName("updated").build());
        entity = inventory.inspect(entity, cls).entity();
        Assert.assertEquals("updated", entity.getName());
    }

    @Test
    public void testIdenticalResourceTypesFound() throws Exception {
        Tenant t = inventory.tenants().create(Tenant.Blueprint.builder().withId(UUID.randomUUID().toString()).build())
                .entity();
        try {
            Feed f1 = inventory.inspect(t).feeds().create(Feed.Blueprint.builder().withId("f1").build()).entity();
            Feed f2 = inventory.inspect(t).feeds().create(Feed.Blueprint.builder().withId("f2").build()).entity();

            ResourceType rt1 = inventory.inspect(f1).resourceTypes().create(ResourceType.Blueprint.builder().withId
                    ("rt").build()).entity();

            ResourceType rt2 = inventory.inspect(f2).resourceTypes().create(ResourceType.Blueprint.builder().withId
                    ("rt").build()).entity();

            Set<ResourceType> identicals = inventory.inspect(rt1).identical().getAll().entities();

            Assert.assertEquals(2, identicals.size());
            Assert.assertTrue(identicals.contains(rt1));
            Assert.assertTrue(identicals.contains(rt2));

            String hash = inventory.inspect(rt1).identityHash();
            Assert.assertEquals(hash, inventory.inspect(rt2).identityHash());

            DataEntity.Blueprint<ResourceTypes.DataRole> bl = DataEntity.Blueprint.<ResourceTypes.DataRole>builder()
                    .withRole(ResourceTypes.DataRole.configurationSchema).withValue(StructuredData.get().map()
                            .putString("title", "blah")
                            .putString("type", "string").build()).build();

            inventory.inspect(rt1).data().create(bl);

            Assert.assertNotEquals(hash, inventory.inspect(rt1).identityHash());

            identicals = inventory.inspect(rt1).identical().getAll().entities();
            Assert.assertEquals(1, identicals.size());
            Assert.assertTrue(identicals.contains(rt1));

            inventory.inspect(rt2).data().create(bl);

            identicals = inventory.inspect(rt1).identical().getAll().entities();
            Assert.assertEquals(2, identicals.size());
            Assert.assertTrue(identicals.contains(rt1));
            Assert.assertTrue(identicals.contains(rt2));
        } finally {
            inventory.tenants().delete(t.getId());
        }
    }

    @Test
    public void testIdenticalMetricTypesFound() throws Exception {
        Tenant t = inventory.tenants().create(Tenant.Blueprint.builder().withId(UUID.randomUUID().toString()).build())
                .entity();
        try {
            Feed f1 = inventory.inspect(t).feeds().create(Feed.Blueprint.builder().withId("f1").build()).entity();
            Feed f2 = inventory.inspect(t).feeds().create(Feed.Blueprint.builder().withId("f2").build()).entity();

            MetricType mt1 = inventory.inspect(f1).metricTypes().create(MetricType.Blueprint.builder(MetricDataType
                    .GAUGE).withId("mt").withInterval(0L).withUnit(MetricUnit.NONE).build()).entity();

            MetricType mt2 = inventory.inspect(f2).metricTypes().create(MetricType.Blueprint.builder(MetricDataType
                    .GAUGE).withId("mt").withInterval(0L).withUnit(MetricUnit.NONE).build()).entity();

            Set<MetricType> identicals = inventory.inspect(mt1).identical().getAll().entities();

            Assert.assertEquals(2, identicals.size());
            Assert.assertTrue(identicals.contains(mt1));
            Assert.assertTrue(identicals.contains(mt2));

            String hash = inventory.inspect(mt1).identityHash();
            Assert.assertEquals(hash, inventory.inspect(mt2).identityHash());

            inventory.inspect(mt1).update(MetricType.Update.builder().withUnit(MetricUnit.MILLISECONDS).build());

            Assert.assertNotEquals(hash, inventory.inspect(mt1).identityHash());

            identicals = inventory.inspect(mt1).identical().getAll().entities();
            Assert.assertEquals(1, identicals.size());
            Assert.assertTrue(identicals.contains(mt1));

            inventory.inspect(mt2).update(MetricType.Update.builder().withUnit(MetricUnit.MILLISECONDS).build());

            identicals = inventory.inspect(mt1).identical().getAll().entities();
            Assert.assertEquals(2, identicals.size());
            Assert.assertTrue(identicals.contains(mt1));
            Assert.assertTrue(identicals.contains(mt2));
        } finally {
            inventory.tenants().delete(t.getId());
        }
    }

    @Test
    public void testObserveTenants() throws Exception {
        String tid = "testObserveTenants";
        runObserverTest(Tenant.class, 0, 0, () -> {
            inventory.tenants().create(new Tenant.Blueprint(tid));
            inventory.tenants().update(tid, Tenant.Update.builder().build());
            inventory.tenants().delete(tid);
        });
    }

    @Test
    public void testObserveEnvironments() throws Exception {
        String tid = "testObserveEnvironments";
        String eid = "env";
        runObserverTest(Environment.class, 1, 1, () -> {
            inventory.tenants().create(Tenant.Blueprint.builder().withId(tid).build());
            inventory.tenants().get(tid).environments().create(Environment.Blueprint.builder().withId(eid).build());
            inventory.tenants().get(tid).environments().update(eid, Environment.Update.builder().build());
            inventory.tenants().get(tid).environments().delete(eid);
            inventory.tenants().delete(tid);
        });
    }

    @Test
    public void testObserveResourceTypes() throws Exception {
        String tid = "testObserveResourceTypes";
        String rtid = "rtype";
        String mtid = "mtype";
        runObserverTest(ResourceType.class, 3, 3, () -> {
            inventory.tenants().create(Tenant.Blueprint.builder().withId(tid).build());

            inventory.tenants().get(tid).resourceTypes().create(new ResourceType.Blueprint(rtid));
            inventory.tenants().get(tid).resourceTypes().update(rtid, new ResourceType.Update(null));

            MetricType mt = inventory.tenants().get(tid).metricTypes()
                    .create(MetricType.Blueprint.builder(MetricDataType.COUNTER)
                                    .withId(mtid).withUnit(MetricUnit.BYTES).withInterval(0L).build()).entity();

            List<Relationship> createdRelationships = new ArrayList<>();

            inventory.observable(Interest.in(Relationship.class).being(created())).subscribe(createdRelationships::add);

            inventory.tenants().get(tid).resourceTypes().get(rtid).metricTypes().associate(mt.getPath());

            inventory.tenants().get(tid).metricTypes().delete(mtid);

            Assert.assertEquals(1, createdRelationships.size());

            inventory.tenants().get(tid).resourceTypes().delete(rtid);

            inventory.tenants().delete(tid);
        });
    }

    @Test
    public void testObserveMetricTypes() throws Exception {
        String tid = "testObserveMetricTypes";
        String mtid = "mtype";
        try {
            runObserverTest(MetricType.class, 1, 1, () -> {
                inventory.tenants().create(Tenant.Blueprint.builder().withId(tid).build());

                inventory.tenants().get(tid).metricTypes()
                        .create(new MetricType.Blueprint(mtid, MetricUnit.BYTES, MetricDataType.COUNTER, 0L));
                inventory.tenants().get(tid).metricTypes().update(mtid, MetricType.Update.builder()
                        .withUnit(MetricUnit.MILLISECONDS).build());
                inventory.tenants().get(tid).metricTypes().delete(mtid);
            });
        } finally {
            inventory.tenants().delete(tid);
        }
    }

    @Test
    public void testObserveMetrics() throws Exception {
        String tid = "testObserveMetrics";
        String eid = "env";
        String mtid = "mtype";
        String mid = "met";
        try {
            runObserverTest(Metric.class, 4, 2, () -> {
                inventory.tenants().create(Tenant.Blueprint.builder().withId(tid).build());
                inventory.tenants().get(tid).environments().create(Environment.Blueprint.builder().withId(eid).build());
                inventory.tenants().get(tid).metricTypes()
                        .create(new MetricType.Blueprint(mtid, MetricUnit.BYTES, MetricDataType.COUNTER, 0L));

                inventory.tenants().get(tid).environments().get(eid).metrics()
                        .create(new Metric.Blueprint("/" + mtid, mid));
                inventory.tenants().get(tid).environments().get(eid).metrics().update(mid,
                        Metric.Update.builder().build());

                inventory.tenants().get(tid).environments().get(eid).metrics().delete(mid);
            });
        } finally {
            inventory.tenants().delete(tid);
        }
    }

    @Test
    public void testObserveResources() throws Exception {
        String tid = "testObserveResources";
        String eid = "env";
        String rtid = "rtype";
        String mtid = "mtype";
        String mid = "met";
        String rid = "res";
        try {
            runObserverTest(Resource.class, 8, 3, () -> {
                inventory.tenants().create(Tenant.Blueprint.builder().withId(tid).build());
                inventory.tenants().get(tid).environments().create(Environment.Blueprint.builder().withId(eid).build());
                inventory.tenants().get(tid).resourceTypes().create(ResourceType.Blueprint.builder().withId(rtid)
                        .build());
                inventory.tenants().get(tid).metricTypes()
                        .create(MetricType.Blueprint.builder(MetricDataType.COUNTER).withId(mtid)
                                .withUnit(MetricUnit.BYTES).withInterval(0L).build());

                inventory.tenants().get(tid).environments().get(eid).resources()
                        .create(new Resource.Blueprint(rid, "/" + rtid));
                inventory.tenants().get(tid).environments().get(eid).resources().update(rid,
                        Resource.Update.builder().build());

                Metric m = inventory.tenants().get(tid).environments().get(eid).metrics()
                        .create(Metric.Blueprint.builder().withId(mid).withMetricTypePath("/" + mtid).build()).entity();

                List<Relationship> createdRelationships = new ArrayList<>();

                inventory.observable(Interest.in(Relationship.class).being(created()))
                        .subscribe(createdRelationships::add);

                inventory.tenants().get(tid).environments().get(eid).resources().get(rid).allMetrics().associate(
                        m.getPath());

                Assert.assertEquals(1, createdRelationships.size());

                inventory.tenants().get(tid).environments().get(eid).resources().delete(rid);
            });
        } finally {
            inventory.tenants().delete(tid);
        }
    }

    @Test
    public void testObserveDataEntities() throws Exception {
        String tid = "testObserveDataEntities";
        String eid = "env";
        String rtid = "rtype";
        String rid = "res";
        try {
            runObserverTest(DataEntity.class, 5, 1, () -> {
                inventory.tenants().create(Tenant.Blueprint.builder().withId(tid).build());
                inventory.tenants().get(tid).environments().create(Environment.Blueprint.builder().withId(eid).build());
                inventory.tenants().get(tid).resourceTypes().create(ResourceType.Blueprint.builder().withId(rtid)
                        .build());
                inventory.tenants().get(tid).environments().get(eid).resources()
                        .create(new Resource.Blueprint(rid, "/" + rtid));

                Data.ReadWrite<Resources.DataRole> dataAccess = inventory.tenants().get(tid).environments().get(eid)
                        .resources().get(rid).data();

                dataAccess.create(DataEntity.Blueprint.<Resources.DataRole>builder()
                        .withRole(configuration).build());

                dataAccess.update(configuration, DataEntity.Update.builder().build());

                dataAccess.delete(configuration);
            });
        } finally {
            inventory.tenants().delete(tid);
        }
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
        Assert.assertEquals("com.acme.tenant", env.getPath().ids().getTenantId());
        Assert.assertEquals("production", env.getId());

        entity = backend.find(envPath.extend(Resource.class, "host1").get());
        Resource r = backend.convert(entity, Resource.class);
        Assert.assertEquals("com.acme.tenant", r.getPath().ids().getTenantId());
        Assert.assertEquals("production", r.getPath().ids().getEnvironmentId());
        Assert.assertNull(r.getPath().ids().getFeedId());
        Assert.assertEquals("host1", r.getId());

        entity = backend.find(envPath.extend(Metric.class, "host1_ping_response").get());
        Metric m = backend.convert(entity, Metric.class);
        Assert.assertEquals("com.acme.tenant", m.getPath().ids().getTenantId());
        Assert.assertEquals("production", m.getPath().ids().getEnvironmentId());
        Assert.assertNull(m.getPath().ids().getFeedId());
        Assert.assertEquals("host1_ping_response", m.getId());

        CanonicalPath feedPath = tenantPath.extend(Feed.class, "feed1").get();
        entity = backend.find(feedPath);
        Feed f = backend.convert(entity, Feed.class);
        Assert.assertEquals("com.acme.tenant", f.getPath().ids().getTenantId());
        Assert.assertEquals("feed1", f.getId());

        entity = backend.find(feedPath.extend(Resource.class, "feedResource1").get());
        r = backend.convert(entity, Resource.class);
        Assert.assertEquals("com.acme.tenant", r.getPath().ids().getTenantId());
        Assert.assertEquals("feed1", r.getPath().ids().getFeedId());
        Assert.assertEquals("feedResource1", r.getId());

        entity = backend.find(feedPath.extend(Metric.class, "feedMetric1").get());
        m = backend.convert(entity, Metric.class);
        Assert.assertEquals("com.acme.tenant", m.getPath().ids().getTenantId());
        Assert.assertEquals("feed1", m.getPath().ids().getFeedId());
        Assert.assertEquals("feedMetric1", m.getId());

        entity = backend.find(tenantPath.extend(ResourceType.class, "URL").get());
        ResourceType rt = backend.convert(entity, ResourceType.class);
        Assert.assertEquals("com.acme.tenant", rt.getPath().ids().getTenantId());
        Assert.assertEquals("URL", rt.getId());

        entity = backend.find(tenantPath.extend(MetricType.class, "ResponseTime").get());
        MetricType mt = backend.convert(entity, MetricType.class);
        Assert.assertEquals("com.acme.tenant", mt.getPath().ids().getTenantId());
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
        Assert.assertEquals(8, rels.size());

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
                "Person".equals(r.getTarget().getSegment().getElementId())));
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "ResponseTime".equals(r.getTarget().getSegment().getElementId())));
        Assert.assertTrue(checks.apply(rels).anyMatch((r) -> contains.name().equals(r.getName()) &&
                "com.acme.tenant".equals(r.getSource().getSegment().getElementId()) &&
                "feed1".equals(r.getTarget().getSegment().getElementId())));

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

        TriFunction<E, Relationships.Direction, String[], Stream<? extends Entity<?, ?>>> test =
                (start, direction, name) -> {
                    Iterator<E> transitiveClosure = backend.getTransitiveClosureOver(start, direction, name);
                    return StreamSupport.stream(Spliterators.spliterator(transitiveClosure, Integer.MAX_VALUE, 0),
                            false).map((e) -> (Entity<?, ?>) backend.convert(e, backend.extractType(e)));
                };

        E env = backend.find(CanonicalPath.of().tenant("com.acme.tenant").environment("production").get());
        E feed = backend.find(CanonicalPath.of().tenant("com.acme.tenant").feed("feed1")
                .get());

        Assert.assertEquals(8, test.apply(feed, outgoing, new String[]{"contains"}).count());
        Assert.assertFalse(test.apply(feed, outgoing, new String[]{"contains"}).anyMatch((e) -> e instanceof Feed &&
                "feed1".equals(e.getId())));
        Assert.assertTrue(test.apply(env, outgoing, new String[]{"contains", "incorporates"}).anyMatch((e) -> e
                instanceof Resource && "feedResource1".equals(e.getId())));
        Assert.assertTrue(test.apply(env, outgoing, new String[]{"contains", "incorporates"}).anyMatch((e) -> e
                instanceof Resource && "feedResource2".equals(e.getId())));
        Assert.assertTrue(test.apply(env, outgoing, new String[]{"contains", "incorporates"}).anyMatch((e) -> e
                instanceof Resource && "feedResource3".equals(e.getId())));
        Assert.assertTrue(test.apply(env, outgoing, new String[]{"contains", "incorporates"}).anyMatch((e) -> e
                instanceof Metric && "feedMetric1".equals(e.getId())));
        Assert.assertTrue(test.apply(feed, outgoing, new String[]{"contains"}).anyMatch((e) -> e
                instanceof ResourceType && "feed1-resourceType".equals(e.getId())));
        Assert.assertTrue(test.apply(feed, outgoing, new String[]{"contains"}).anyMatch((e) -> e
                instanceof MetricType && "feed1-metricType".equals(e.getId())));

        Assert.assertEquals(1, test.apply(env, incoming, new String[]{"contains"}).count());
        Assert.assertTrue(test.apply(env, incoming, new String[]{"contains"}).anyMatch((e) -> e instanceof Tenant &&
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
        Assert.assertFalse(backend.hasRelationship(tenant, env, "e-kachny"));
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

        CanonicalPath feedPath = tenantPath.extend(Feed.class, "feed1").get();
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
        Assert.assertTrue(results.hasNext());
        Assert.assertEquals("com.acme.tenant", backend.extractId(results.next()));
        Assert.assertTrue(!results.hasNext());

        q = Query.path().with(type(Tenant.class), id("com.acme.tenant"), Related.by("contains"),
                type(Environment.class), id("production")).get();
        results = backend.query(q, unlimited);
        Assert.assertTrue(results.hasNext());
        Assert.assertEquals("production", backend.extractId(results.next()));
        Assert.assertTrue(!results.hasNext());

        // equivalent to inventory.tenants().getAll(Related.by("contains"), type(ResourceType.class, id("URL"))
        // .environments().getAll().entities();
        q = Query.path().with(type(Tenant.class)).filter().with(Related.by("contains"), type(ResourceType.class),
                id("URL")).path().with(Related.by("contains"), type(Environment.class)).get();
        results = backend.query(q, unlimited);
        Assert.assertTrue(results.hasNext());
        Assert.assertEquals("production", backend.extractId(results.next()));
        Assert.assertTrue(!results.hasNext());

    }

    @Test
    public void testCreateConfiguration() throws Exception {
        Resources.Single res = inventory.tenants().get("com.example.tenant").environments().get("test")
                .resources().get("playroom2");

        StructuredData orig = StructuredData.get().map().putBool("yes", true).putBool("no", false).build();

        res.data().create(DataEntity.Blueprint.<Resources.DataRole>builder().withRole(connectionConfiguration)
                .withValue(orig).build());

        StructuredData retrieved = res.data().get(connectionConfiguration).entity().getValue();

        Assert.assertEquals(orig, retrieved);

        Assert.assertFalse(res.data().get(configuration).exists());

        res.data().delete(connectionConfiguration);

        Assert.assertFalse(res.data().get(connectionConfiguration).exists());
    }

    @Test
    public void testUpdateStructuredDataSimpleValue() throws Exception {

        Data.Single dataAccess = inventory.inspect(
                CanonicalPath.fromString("/t;com.example.tenant/e;test/r;playroom1/d;configuration"),
                Data.Single.class);

        StructuredData origData = dataAccess.entity().getValue();

        Assert.assertNotNull(origData);

        StructuredData modified = origData.update().toMap().putBool("answer", true).build();
        dataAccess.update(DataEntity.Update.builder().withValue(modified).build());
        StructuredData persisted = dataAccess.entity().getValue();
        Assert.assertEquals(modified, persisted);

        modified = modified.update().toMap().updateList("primitives").setBool(0, false).closeList().build();
        dataAccess.update(DataEntity.Update.builder().withValue(modified).build());
        persisted = dataAccess.entity().getValue();
        Assert.assertEquals(modified, persisted);

        modified = modified.update().toMap().remove("answer").build();
        dataAccess.update(DataEntity.Update.builder().withValue(modified).build());
        persisted = dataAccess.entity().getValue();
        Assert.assertEquals(modified, persisted);

        // restore the original value
        dataAccess.update(DataEntity.Update.builder().withValue(origData).build());
    }

    @Test
    public void testFilteringByData() throws Exception {
        Data.Read<Resources.DataRole> configs = inventory.tenants().getAll().environments().getAll().resources()
                .getAll().data();

        Assert.assertEquals(1, configs.getAll(With.dataAt(RelativePath.to().structuredData().key("primitives").index(0)
                .get())).entities().size());

        Assert.assertEquals(0, configs.getAll(new Filter[][]{{
                With.dataAt(RelativePath.to().structuredData().key("primitives").index(0).get()),
                With.dataValue(false)}}).entities().size());

        Assert.assertEquals(1, configs.getAll(new Filter[][]{{
                With.dataAt(RelativePath.to().structuredData().key("primitives").index(0).get()),
                With.dataValue(true)}}).entities().size());

        Assert.assertEquals(1, configs.getAll(new Filter[][]{{
                With.dataAt(RelativePath.to().structuredData().key("primitives").get()),
                With.dataOfTypes(StructuredData.Type.list)}}).entities().size());

        Assert.assertEquals(1, configs.getAll(new Filter[][]{{
                With.dataAt(RelativePath.to().structuredData().key("primitives").index(0).get()),
                With.dataOfTypes(StructuredData.Type.bool)}}).entities().size());

        Assert.assertEquals(0, configs.getAll(new Filter[][]{{
                With.dataAt(RelativePath.to().structuredData().key("primitives").get()),
                With.dataOfTypes(StructuredData.Type.map)}}).entities().size());

        Assert.assertEquals(0, configs.getAll(new Filter[][]{{
                With.dataAt(RelativePath.to().structuredData().key("primitives").index(0).get()),
                With.dataOfTypes(StructuredData.Type.integral)}}).entities().size());
    }

    @Test
    public void testRetrievingDataPortions() throws Exception {
        Data.Single config = inventory.inspect(
                CanonicalPath.fromString("/t;com.example.tenant/e;test/r;playroom1/d;configuration"),
                Data.Single.class);

        StructuredData allData = config.entity().getValue();

        StructuredData portion = config.data(RelativePath.to().structuredData().key("primitives").index(0).get());
        Assert.assertEquals(allData.map().get("primitives").list().get(0), portion);

        portion = config.data(RelativePath.empty().get());
        Assert.assertEquals(allData, portion);

        portion = config.flatData(RelativePath.to().structuredData().key("primitives").index(0).get());
        Assert.assertEquals(allData.map().get("primitives").list().get(0), portion);

        portion = config.flatData(RelativePath.empty().get());
        //noinspection AssertEqualsBetweenInconvertibleTypes
        Assert.assertEquals(Collections.emptyMap(), portion.getValue());

        portion = config.flatData(RelativePath.to().structuredData().key("primitives").get());
        //noinspection AssertEqualsBetweenInconvertibleTypes
        Assert.assertEquals(Collections.emptyList(), portion.getValue());
    }

    @Test
    public void testCreateInvalidConfiguration() throws Exception {
        Resources.Single res = inventory.inspect(CanonicalPath.fromString("/t;com.acme.tenant/e;production/r;people"),
                Resources.Single.class);

        try {
            res.data().create(DataEntity.Blueprint.<Resources.DataRole>builder()
                    .withRole(configuration).withValue(StructuredData.get().map()
                            .putBool("firstName", false).build()).build());
            Assert.fail("Creating a config that doesn't conform to the schema shouldn't be possible.");
        } catch (ValidationException e) {
            //good, we should get 2 errors - missing last name and invalid value type on the firstName
            Assert.assertEquals(2, e.getMessages().size());
            Assert.assertEquals(CanonicalPath.fromString("/t;com.acme.tenant/e;production/r;people/d;configuration"),
                    e.getDataPath());
            Assert.assertEquals("ERROR", e.getMessages().get(0).getSeverity());
            Assert.assertEquals("ERROR", e.getMessages().get(1).getSeverity());
        }
    }

    @Test
    public void testUpdateWithInvalidConfiguration() throws Exception {
        Resources.Single res = inventory.inspect(
                CanonicalPath.fromString("/t;com.acme.tenant/e;production/r;people/r;Alois"), Resources.Single.class);

        try {
            res.data().get(configuration).update(DataEntity.Update.builder().withValue(StructuredData.get().map()
                    .putBool("firstName", false).build()).build());
            Assert.fail("Updating a config that doesn't conform to the schema shouldn't be possible.");
        } catch (ValidationException e) {
            //good, we should get 2 errors - missing last name and invalid value type on the firstName
            Assert.assertEquals(2, e.getMessages().size());
            Assert.assertEquals(CanonicalPath
                            .fromString("/t;com.acme.tenant/e;production/r;people/r;Alois/d;configuration"),
                    e.getDataPath());
            Assert.assertEquals("ERROR", e.getMessages().get(0).getSeverity());
            Assert.assertEquals("ERROR", e.getMessages().get(1).getSeverity());
        }
    }

    @Test
    public void testCreateWithRelationships() throws Exception {
        inventory.tenants().get("com.acme.tenant").environments().create(Environment.Blueprint.builder()
                .withId("env-with-rel")
                .addOutgoingRelationship("kachna", CanonicalPath.of().tenant("com.acme.tenant").get())
                .addIncomingRelationship("duck", CanonicalPath.of().tenant("com.acme.tenant").get())
                .build());
        Assert.assertTrue(inventory.tenants().get("com.acme.tenant").relationships(incoming).named("kachna")
                .anyExists());
        Assert.assertTrue(inventory.tenants().get("com.acme.tenant").relationships(outgoing).named("duck")
                .anyExists());

        //clean up
        inventory.tenants().get("com.acme.tenant").environments().delete("env-with-rel");
    }

    @Test
    public void testResourceOwnedMetrics() throws Exception {
        inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
                .resources().get("feedResource3").metrics().get("feedResource3-metric").exists();

        inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
                .resources().get("feedResource3").allMetrics().get(RelativePath.to().metric("feedResource3-metric")
                .get()).exists();
    }

    @Test
    public void testGettingResourceTypesFromTenants() throws Exception {
        Set<ResourceType> rts = inventory.tenants().get("com.acme.tenant").resourceTypes().getAll().entities();
        Assert.assertEquals(3, rts.size());
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("URL")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("Person")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("mpRt")));

        rts = inventory.tenants().get("com.acme.tenant").resourceTypesUnder(Parents.any()).getAll()
                .entities();
        Assert.assertEquals(4, rts.size());
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("URL")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("Person")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("mpRt")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("feed1-resourceType")));

        rts = inventory.tenants().get("com.acme.tenant").resourceTypesUnder().getAll().entities();
        Assert.assertEquals(3, rts.size());
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("URL")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("Person")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("mpRt")));

        rts = inventory.tenants().get("com.acme.tenant").resourceTypesUnder(Tenants.ResourceTypeParents.TENANT).getAll()
                .entities();
        Assert.assertEquals(3, rts.size());
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("URL")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("Person")));
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("mpRt")));

        rts = inventory.tenants().get("com.acme.tenant").resourceTypesUnder(Tenants.ResourceTypeParents.FEED).getAll()
                .entities();
        Assert.assertEquals(1, rts.size());
        Assert.assertTrue(rts.stream().anyMatch(rt -> rt.getId().equals("feed1-resourceType")));
    }

    @Test
    public void testGettingMetricTypesFromTenants() throws Exception {
        Set<MetricType> mts = inventory.tenants().get("com.acme.tenant").metricTypes().getAll().entities();
        Assert.assertEquals(2, mts.size());
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("ResponseTime")));
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("mpMt")));

        mts = inventory.tenants().get("com.acme.tenant").metricTypesUnder().getAll().entities();
        Assert.assertEquals(2, mts.size());
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("ResponseTime")));
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("mpMt")));

        mts = inventory.tenants().get("com.acme.tenant").metricTypesUnder(Parents.any()).getAll().entities();
        Assert.assertEquals(3, mts.size());
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("ResponseTime")));
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("mpMt")));
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("feed1-metricType")));

        mts = inventory.tenants().get("com.acme.tenant").metricTypesUnder(Tenants.MetricTypeParents.TENANT).getAll()
                .entities();
        Assert.assertEquals(2, mts.size());
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("ResponseTime")));
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("mpMt")));

        mts = inventory.tenants().get("com.acme.tenant").metricTypesUnder(Tenants.MetricTypeParents.FEED).getAll()
                .entities();
        Assert.assertEquals(1, mts.size());
        Assert.assertTrue(mts.stream().anyMatch(rt -> rt.getId().equals("feed1-metricType")));
    }

    @Test
    public void testGettingResourcesFromFeeds() throws Exception {
        Set<Resource> rs = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").resources().getAll()
                .entities();
        Assert.assertEquals(3, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource2")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource3")));

        rs = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").resourcesUnder(Parents.any()).getAll()
                .entities();
        Assert.assertEquals(4, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource2")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource3")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedChildResource")));

        rs = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").resourcesUnder(Feeds.ResourceParents.FEED)
                .getAll().entities();
        Assert.assertEquals(3, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource2")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource3")));

        rs = inventory.tenants().get("com.acme.tenant").feeds().get("feed1")
                .resourcesUnder(Feeds.ResourceParents.RESOURCE).getAll().entities();
        Assert.assertEquals(1, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedChildResource")));
    }

    @Test
    public void testGettingMetricsFromFeeds() throws Exception {
        Set<Metric> ms = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").metrics().getAll().entities();
        Assert.assertEquals(1, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedMetric1")));

        ms = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").metricsUnder(Feeds.MetricParents.FEED)
                .getAll().entities();
        Assert.assertEquals(1, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedMetric1")));

        ms = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").metricsUnder(Feeds.MetricParents.FEED,
                Feeds.MetricParents.RESOURCE).getAll().entities();
        Assert.assertEquals(2, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedMetric1")));
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedResource3-metric")));

        ms = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").metricsUnder(Parents.any()).getAll()
                .entities();
        Assert.assertEquals(2, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedMetric1")));
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedResource3-metric")));

        ms = inventory.tenants().get("com.acme.tenant").feeds().get("feed1").metricsUnder(Feeds.MetricParents.RESOURCE)
                .getAll().entities();
        Assert.assertEquals(1, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedResource3-metric")));
    }

    @Test
    public void testGettingResourcesFromEnvironments() throws Exception {
        Set<Resource> rs = inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .getAll().entities();
        Assert.assertEquals(2, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("host1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("people")));

        rs = inventory.tenants().get("com.acme.tenant").environments().get("production").resourcesUnder(Parents.any())
                .getAll().entities();
        Assert.assertEquals(10, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("host1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource2")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource3")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedChildResource")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("people")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Hynek")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Alois")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Vilem")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Jarmila")));

        rs = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .resourcesUnder(Environments.ResourceParents.ENVIRONMENT).getAll().entities();
        Assert.assertEquals(2, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("host1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("people")));

        rs = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .resourcesUnder(Environments.ResourceParents.FEED).getAll().entities();
        Assert.assertEquals(3, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource2")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedResource3")));

        rs = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .resourcesUnder(Environments.ResourceParents.RESOURCE).getAll().entities();
        Assert.assertEquals(5, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Hynek")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Alois")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Vilem")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Jarmila")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedChildResource")));

        rs = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .resourcesUnder(Environments.ResourceParents.ENVIRONMENT, Environments.ResourceParents.RESOURCE)
                .getAll().entities();
        Assert.assertEquals(7, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("host1")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("people")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Hynek")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Alois")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Vilem")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Jarmila")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("feedChildResource")));
    }

    @Test
    public void testGettingMetricsFromEnvironments() throws Exception {
        Set<Metric> ms = inventory.tenants().get("com.acme.tenant").environments().get("production").metrics().getAll()
                .entities();
        Assert.assertEquals(1, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("host1_ping_response")));

        ms = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .metricsUnder(Environments.MetricParents.ENVIRONMENT).getAll().entities();
        Assert.assertEquals(1, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("host1_ping_response")));

        ms = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .metricsUnder(Environments.MetricParents.FEED).getAll().entities();
        Assert.assertEquals(1, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedMetric1")));

        ms = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .metricsUnder(Environments.MetricParents.RESOURCE).getAll().entities();
        Assert.assertEquals(1, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedResource3-metric")));

        ms = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .metricsUnder(Environments.MetricParents.FEED, Environments.MetricParents.ENVIRONMENT).getAll()
                .entities();
        Assert.assertEquals(2, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedMetric1")));
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("host1_ping_response")));

        ms = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .metricsUnder(Parents.any()).getAll().entities();
        Assert.assertEquals(3, ms.size());
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedMetric1")));
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("host1_ping_response")));
        Assert.assertTrue(ms.stream().anyMatch(m -> m.getId().equals("feedResource3-metric")));
    }

    @Test
    public void testGettingResourcesFromResources() throws Exception {
        Set<Resource> rs = inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .get("people").resources().getAll().entities();
        Assert.assertEquals(2, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Alois")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Hynek")));

        rs = inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .get("people").recursiveResources().getAll().entities();
        Assert.assertEquals(4, rs.size());
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Alois")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Hynek")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Vilem")));
        Assert.assertTrue(rs.stream().anyMatch(r -> r.getId().equals("Jarmila")));
    }

    @Test
    public void testAssociationsNotDuplicated() throws Exception {
        Tenant t = inventory.tenants().create(Tenant.Blueprint.builder().withId("testAssociationsNotDuplicated")
                .build()).entity();

        ResourceType rt = inventory.inspect(t).resourceTypes()
                .create(ResourceType.Blueprint.builder().withId("rtype").build()).entity();

        MetricType mt = inventory.inspect(t).metricTypes()
                .create(MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("mtype").withUnit(MetricUnit.NONE)
                        .withInterval(0L).build()).entity();

        Environment env = inventory.inspect(t).environments().create(Environment.Blueprint.builder().withId("env")
                .build())
                .entity();

        Resource r = inventory.inspect(env).resources().create(Resource.Blueprint.builder().withId("res")
                .withResourceTypePath(rt.getPath().toString()).build()).entity();

        Metric m = inventory.inspect(env).metrics().create(Metric.Blueprint.builder().withId("met")
                .withMetricTypePath(mt.getPath().toString()).build()).entity();

        try {
            Relationship rel = inventory.inspect(r).allMetrics().associate(m.getPath());
            Assert.assertNotNull(rel);

            try {
                Relationship rel2 = inventory.inspect(r).allMetrics().associate(m.getPath());
                Assert.fail("Should not be possible to create an association twice.");
            } catch (RelationAlreadyExistsException e) {
                //expected
            }
        } finally {
            inventory.inspect(t).delete();
        }
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
