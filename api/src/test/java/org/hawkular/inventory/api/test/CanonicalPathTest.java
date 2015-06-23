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

import java.util.Iterator;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.Tenant;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public class CanonicalPathTest {

    @Test
    public void testParse() throws Exception {
        CanonicalPath p = CanonicalPath.fromString("/t;t/e;e/f;f/r;r");
        checkPath(p, Tenant.class, "t", Environment.class, "e", Feed.class, "f", Resource.class, "r");

        try {
            CanonicalPath.fromString("/t;t/e;e/f;f/t;t");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            CanonicalPath.fromString("/e;e/f;f");
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

        p = CanonicalPath.fromString("/rl;r");
        checkPath(p, Relationship.class, "r");

        try {
            CanonicalPath.fromString("/rl;r/t/t");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testToString() throws Exception {
        Assert.assertEquals("/t;t/e;e/r;r", CanonicalPath.of().tenant("t").environment("e").resource("r").get()
                .toString());
        Assert.assertEquals("/t;t/e;e/f;f/r;r", CanonicalPath.of().tenant("t").environment("e").feed("f").resource("r")
                .get().toString());
        Assert.assertEquals("/rl;r", CanonicalPath.of().relationship("r").get().toString());
    }

    @Test
    public void testTraversals() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").feed("f").metric("m").get();

        Assert.assertEquals(3, p.getDepth());

        Assert.assertFalse(p.down().isDefined());

        CanonicalPath cp = p.getRoot();
        Assert.assertEquals("/t;t", cp.toString());

        Assert.assertEquals(p.toString(), cp.getLeaf().toString());

        Assert.assertFalse(cp.up().isDefined());

        Assert.assertEquals("/t;t/e;e", cp.down().toString());

        Assert.assertEquals("/t;t/e;e", p.up().up().toString());
    }

    @Test
    public void testExtending() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").feed("f").get();

        CanonicalPath p2 = p.extend(Metric.class, "m").get();
        Assert.assertEquals("/t;t/e;e/f;f/m;m", p2.toString());

        p2 = p.getRoot().extend(MetricType.class, "mt").get();
        Assert.assertEquals("/t;t/mt;mt", p2.toString());
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

    @Test
    public void testIteration() throws Exception {
        CanonicalPath cp = CanonicalPath.of().tenant("t").environment("e").feed("f").metric("m").get();

        int i = 3;
        for (CanonicalPath p : cp) {
            Assert.assertEquals(cp.getPath().get(i--), p.getSegment());
        }

        i = 0;
        Iterator<CanonicalPath> it = cp.descendingIterator();
        while (it.hasNext()) {
            Assert.assertEquals(cp.getPath().get(i++), it.next().getSegment());
        }

        i = 3;
        it = cp.ascendingIterator();
        while (it.hasNext()) {
            Assert.assertEquals(cp.getPath().get(i--), it.next().getSegment());
        }
    }

    @Test
    public void testRelativePathConstruction() throws Exception {
        RelativePath rp = RelativePath.to().up().metric("kachna").get();

        Assert.assertEquals("../m;kachna", rp.toString());

        rp = RelativePath.fromString("../r;r/../t;t");

        Assert.assertEquals("../r;r/../t;t", rp.toString());

        try {
            RelativePath.fromString("../r");
            Assert.fail("Invalid relative path should have failed to parse.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            RelativePath.fromString("../r;");
            Assert.fail("Invalid relative path should have failed to parse.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            RelativePath.fromString("/../t;t");
            Assert.fail("Invalid relative path should have failed to parse.");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            RelativePath.fromString("..;boom/r;r");
            Assert.fail("Invalid relative path should have failed to parse.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testRelativePathApplication() throws Exception {
        CanonicalPath cp = CanonicalPath.of().tenant("t").environment("e").resource("r").get();

        RelativePath rp = RelativePath.to().up().metric("m").get();

        cp = rp.applyTo(cp);
        Assert.assertEquals(CanonicalPath.of().tenant("t").environment("e").metric("m").get(), cp);

        try {
            cp = RelativePath.to().up().get().applyTo(cp);

            rp.applyTo(cp);
            Assert.fail("Should not be possible to construct invalid canonical path by applying a relative one.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @SuppressWarnings("unchecked")
    private void checkPath(CanonicalPath path, Object... pathSpec) {
        Assert.assertEquals(pathSpec.length / 2, path.getPath().size());
        for (int i = 0; i < pathSpec.length; i += 2) {
            Class<? extends AbstractElement<?, ?>> t = (Class<? extends AbstractElement<?, ?>>) pathSpec[i];
            String id = (String) pathSpec[i + 1];

            CanonicalPath.Segment s = path.getPath().get(i / 2);

            //noinspection AssertEqualsBetweenInconvertibleTypes
            Assert.assertEquals(t, s.getElementType());
            Assert.assertEquals(id, s.getElementId());
        }
    }
}
