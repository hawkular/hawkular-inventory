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

import java.util.Arrays;
import java.util.HashSet;

import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
public class InventoryStructureOfflineBuilderTest {

    private InventoryStructure.Offline.Builder<Feed.Blueprint> structure;

    @Before
    public void setup() {
        structure = InventoryStructure.Offline
                .of(Feed.Blueprint.builder().withId("feed").build())
                .addChild(ResourceType.Blueprint.builder().withId("resourceType").build())
                .addChild(MetricType.Blueprint.builder(MetricDataType.GAUGE)
                        .withId("metricType").withInterval(0L).withUnit(MetricUnit.NONE).build())
                .startChild(Resource.Blueprint.builder().withId("resource").withResourceTypePath("resourceType")
                        .build())
                /**/.addChild(Resource.Blueprint.builder().withId("childResource")
                        .withResourceTypePath("../resourceType").build())
                /**/.addChild(Metric.Blueprint.builder().withId("metric").withInterval(0L)
                        .withMetricTypePath("../metricType").build())
                .end()
                .addChild(Metric.Blueprint.builder().withId("metric").withMetricTypePath("metricType")
                        .withInterval(0L).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCantAddInvalidChildren() throws Exception {
        structure.getChild(Path.Segment.from("rt;resourceType")).addChild(Resource.Blueprint.builder()
                .withResourceTypePath("../resoureType").withId("asdf").build()).end().build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCantReplaceWithInvalidBlueprint() throws Exception {
        structure.getChild(Path.Segment.from("r;resource")).getChild(Path.Segment.from("r;childResource"))
                .replace(ResourceType.Blueprint.builder().withId("id").build());
    }

    @Test
    public void testRecursiveChildrenRemoval() throws Exception {
        structure.getChild(Path.Segment.from("r;resource")).remove();
        Assert.assertEquals(new HashSet<>(Arrays.asList(Path.Segment.from("rt;resourceType"),
                Path.Segment.from("mt;metricType"), Path.Segment.from("m;metric"))), structure.getChildrenPaths());
    }

    @Test
    public void testAddChild() throws Exception {
        structure.getChild(Path.Segment.from("r;resource")).addChild(Resource.Blueprint.builder().withId("cr2")
                .withResourceTypePath("../resourceType").build());

        InventoryStructure s = structure.build();

        Assert.assertEquals(2, s.getChildren(RelativePath.to().resource("resource").get(), Resource.class).count());
    }

    @Test
    public void testReplaceChild() throws Exception {
        structure.getChild(Path.Segment.from("r;resource")).replace(Resource.Blueprint.builder().withId("r2")
                .withResourceTypePath("../resourceType").build());

        InventoryStructure s = structure.build();

        Blueprint b = s.get(RelativePath.to().resource("r2").get());

        Assert.assertNotNull(b);
        Assert.assertEquals("r2", ((Resource.Blueprint) b).getId());

        b = s.get(RelativePath.to().resource("resource").get());
        Assert.assertNull(b);

        b = s.get(RelativePath.to().resource("resource").resource("childResource").get());
        Assert.assertNull(b);

        b = s.get(RelativePath.to().resource("resource").metric("metric").get());
        Assert.assertNull(b);
    }

    @Test
    public void testRemoveChild() throws Exception {
        structure.removeChild(Path.Segment.from("r;resource"));

        InventoryStructure s = structure.build();

        Blueprint b = s.get(RelativePath.to().resource("resource").get());
        Assert.assertNull(b);

        b = s.get(RelativePath.to().resource("resource").resource("childResource").get());
        Assert.assertNull(b);

        b = s.get(RelativePath.to().resource("resource").metric("metric").get());
        Assert.assertNull(b);
    }
}
