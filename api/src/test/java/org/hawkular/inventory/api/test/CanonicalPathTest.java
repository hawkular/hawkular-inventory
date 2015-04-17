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

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.ElementType;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.junit.Assert;
import org.junit.Test;
import static org.hawkular.inventory.api.model.ElementType.ENVIRONMENT;
import static org.hawkular.inventory.api.model.ElementType.FEED;
import static org.hawkular.inventory.api.model.ElementType.RELATIONSHIP;
import static org.hawkular.inventory.api.model.ElementType.RESOURCE;
import static org.hawkular.inventory.api.model.ElementType.TENANT;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public class CanonicalPathTest {

    @Test
    public void testParse() throws Exception {
        CanonicalPath p = CanonicalPath.fromString("t|t/e|e/f|f/r|r");
        checkPath(p, TENANT, "t", ENVIRONMENT, "e", FEED, "f", RESOURCE, "r");

        try {
            CanonicalPath.fromString("t|t/e|e/f|f/t|t");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            CanonicalPath.fromString("e|e/f|f");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            CanonicalPath.fromString("");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            CanonicalPath.fromString("t|t/e|e/f|f/");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        p = CanonicalPath.fromString("rl|r");
        checkPath(p, RELATIONSHIP, "r");

        try {
            CanonicalPath.fromString("rl|r/t/t");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testToString() throws Exception {
        Assert.assertEquals("t|t/e|e/r|r", CanonicalPath.of().tenant("t").environment("e").resource("r").get()
                .toString());
        Assert.assertEquals("t|t/e|e/f|f/r|r", CanonicalPath.of().tenant("t").environment("e").feed("f").resource("r")
                .get().toString());
        Assert.assertEquals("rl|r", CanonicalPath.of().relationship("r").get().toString());
    }

    @Test
    public void testTraversals() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").feed("f").metric("m").get();

        Assert.assertFalse(p.down().isDefined());

        CanonicalPath cp = p.getRoot();
        Assert.assertEquals("t|t", cp.toString());

        Assert.assertEquals(p.toString(), cp.getLeaf().toString());

        Assert.assertFalse(cp.up().isDefined());

        Assert.assertEquals("t|t/e|e", cp.down().toString());

        Assert.assertEquals("t|t/e|e", p.up().up().toString());
    }

    @Test
    public void testExtending() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").feed("f").get();

        CanonicalPath p2 = p.extend(Metric.class, "m").get();
        Assert.assertEquals("t|t/e|e/f|f/m|m", p2.toString());

        p2 = p.getRoot().extend(MetricType.class, "mt").get();
        Assert.assertEquals("t|t/mt|mt", p2.toString());
    }

    @Test
    public void testIdExtraction() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").resource("r").get();
        Assert.assertEquals("t", p.ids().getTenantId());
        Assert.assertEquals("e", p.ids().getEnvironmentId());
        Assert.assertEquals("r", p.ids().getResourceId());

        Assert.assertEquals("f", p.up().extend(Feed.class, "f").get().ids().getFeedId());
        Assert.assertEquals("r", p.up().extend(Feed.class, "f").extend(Resource.class, "r").get().ids()
                .getResourceId());
    }

    @Test
    public void testEntityInstantiationWithWrongPath() throws Exception {
        try {
            new Environment(CanonicalPath.of().tenant("t").get());
            Assert.fail("Creating an entity with a path pointing to a different entity type should not be possible.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    private void checkPath(CanonicalPath path, Object... pathSpec) {
        Assert.assertEquals(pathSpec.length / 2, path.getPath().size());
        for (int i = 0; i < pathSpec.length; i += 2) {
            ElementType t = (ElementType) pathSpec[i];
            String id = (String) pathSpec[i + 1];

            CanonicalPath.Segment s = path.getPath().get(i / 2);

            Assert.assertEquals(t, s.getElementType());
            Assert.assertEquals(id, s.getElementId());
        }
    }
}
