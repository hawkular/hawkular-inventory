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
package org.hawkular.inventory.impl.cassandra;

import java.util.Collections;
import java.util.Iterator;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.test.AbstractBaseInventoryTestsuite;
import org.hawkular.inventory.base.spi.InventoryBackend;
import org.hawkular.inventory.paths.CanonicalPath;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.datastax.driver.core.Row;

/**
 * Basic DB tests with simple data set for minimal test cases
 * @author Joel Takvorian
 * @since 2.0.0
 */
public class CassandraInventoryDBTest {
    private static InventoryBackend<Row> BACKEND;

    @BeforeClass
    public static void init() throws Exception {
        CassandraController.start();
        CassandraInventory inventory = new CassandraInventory();
        AbstractBaseInventoryTestsuite.initializeInventoryForTest(inventory);
        BACKEND = inventory.getBackend();
    }

    @AfterClass
    public static void shutdown() throws Exception {
        BACKEND.close();
        CassandraController.stop();
    }

    @Test
    public void shouldHaveRelationship() {
        Row apple = BACKEND.persist(
                CanonicalPath.fromString("/t;apple"),
                Tenant.Blueprint.builder().withId("apple").build());
        Row appleTree = BACKEND.persist(
                CanonicalPath.fromString("/t;apple_tree"),
                Tenant.Blueprint.builder().withId("apple_tree").build());
        BACKEND.persist(
                CanonicalPath.fromString("/t;peer"),
                Tenant.Blueprint.builder().withId("peer").build());
        BACKEND.relate(appleTree, apple, "contains", Collections.emptyMap());

        Assert.assertTrue(BACKEND.hasRelationship(appleTree, apple, "contains"));
        Assert.assertTrue(BACKEND.hasRelationship(appleTree, Relationships.Direction.outgoing, "contains"));
        Assert.assertTrue(BACKEND.hasRelationship(apple, Relationships.Direction.incoming, "contains"));
        Assert.assertTrue(BACKEND.hasRelationship(apple, Relationships.Direction.both, "contains"));
    }

    @Test
    public void shouldNotHaveRelationship() {
        Row apple = BACKEND.persist(
                CanonicalPath.fromString("/t;apple"),
                Tenant.Blueprint.builder().withId("apple").build());
        Row appleTree = BACKEND.persist(
                CanonicalPath.fromString("/t;apple_tree"),
                Tenant.Blueprint.builder().withId("apple_tree").build());
        Row peer = BACKEND.persist(
                CanonicalPath.fromString("/t;peer"),
                Tenant.Blueprint.builder().withId("peer").build());
        BACKEND.relate(appleTree, apple, "contains", Collections.emptyMap());

        Assert.assertFalse(BACKEND.hasRelationship(apple, appleTree, "contains"));
        Assert.assertFalse(BACKEND.hasRelationship(appleTree, apple, "likes"));
        Assert.assertFalse(BACKEND.hasRelationship(appleTree, Relationships.Direction.incoming, "contains"));
        Assert.assertFalse(BACKEND.hasRelationship(apple, Relationships.Direction.outgoing, "contains"));
        Assert.assertFalse(BACKEND.hasRelationship(peer, Relationships.Direction.both, "contains"));
    }

    @Test
    public void shouldGetTransitiveClosure() {
        Row apple = BACKEND.persist(
                CanonicalPath.fromString("/t;apple"),
                Tenant.Blueprint.builder().withId("apple").build());
        Row appleTree = BACKEND.persist(
                CanonicalPath.fromString("/t;apple_tree"),
                Tenant.Blueprint.builder().withId("apple_tree").build());
        Row peer = BACKEND.persist(
                CanonicalPath.fromString("/t;peer"),
                Tenant.Blueprint.builder().withId("peer").build());
        BACKEND.relate(appleTree, apple, "contains", Collections.emptyMap());

        // !! Test failing here; wrong order, to investigate
        Iterator<Row> closure = BACKEND.getTransitiveClosureOver(appleTree, Relationships.Direction.outgoing, "contains");
        Assert.assertTrue(closure.hasNext());
        Row item = closure.next();
        Assert.assertEquals("apple_tree", item.getString(Statements.ID));
        Assert.assertTrue(closure.hasNext());
        item = closure.next();
        Assert.assertEquals("apple", item.getString(Statements.ID));
        Assert.assertFalse(closure.hasNext());

        // Single item
        closure = BACKEND.getTransitiveClosureOver(peer, Relationships.Direction.outgoing, "contains");
        Assert.assertTrue(closure.hasNext());
        item = closure.next();
        Assert.assertEquals("peer", item.getString(Statements.ID));
        Assert.assertFalse(closure.hasNext());
    }

    @Test
    public void shouldGetTransitiveClosureBackward() {
        Row apple = BACKEND.persist(
                CanonicalPath.fromString("/t;apple"),
                Tenant.Blueprint.builder().withId("apple").build());
        Row appleTree = BACKEND.persist(
                CanonicalPath.fromString("/t;apple_tree"),
                Tenant.Blueprint.builder().withId("apple_tree").build());
        BACKEND.persist(
                CanonicalPath.fromString("/t;peer"),
                Tenant.Blueprint.builder().withId("peer").build());
        BACKEND.relate(appleTree, apple, "contains", Collections.emptyMap());

        Iterator<Row> closure = BACKEND.getTransitiveClosureOver(apple, Relationships.Direction.incoming, "contains");
        Assert.assertTrue(closure.hasNext());
        Row item = closure.next();
        Assert.assertEquals("apple", item.getString(Statements.ID));
        Assert.assertTrue(closure.hasNext());
        item = closure.next();
        Assert.assertEquals("apple_tree", item.getString(Statements.ID));
        Assert.assertFalse(closure.hasNext());

        // Single item
        closure = BACKEND.getTransitiveClosureOver(appleTree, Relationships.Direction.incoming, "contains");
        Assert.assertTrue(closure.hasNext());
        item = closure.next();
        Assert.assertEquals("apple_tree", item.getString(Statements.ID));
        Assert.assertFalse(closure.hasNext());
    }
}
