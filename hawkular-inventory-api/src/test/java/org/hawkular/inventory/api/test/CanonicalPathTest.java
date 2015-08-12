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
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.Tenant;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.2.0
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

        try {
            CanonicalPath.fromString("/t;t/e;e/f;f/m;m1/m;m2");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            CanonicalPath.fromString("/t;t/e;e/f;f/m1/m2//");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testParseWithEscapedChars() throws Exception {
        CanonicalPath p = CanonicalPath.fromString("/t;te%2Fn;a%2fnt/e;e%2fnv/f;f%2f%25eed/r;r;;%2fes");
        checkPath(p, Tenant.class, "te/n;a/nt", Environment.class, "e/nv", Feed.class, "f/%eed",
                Resource.class, "r;;/es");

        p = CanonicalPath
                .fromString("/t;te%2Fn;a%2Fnt/e;e%2Fnv/f;f%2F%25eed/r;r;;%2Fes1/r;r;;%2Fes2/r;r;;%2Fes3");
        // not sure if this should pass
        checkPath(p, Tenant.class, "te/n;a/nt", Environment.class, "e/nv", Feed.class, "f/%eed",
                Resource.class, "r;;/es1", Resource.class, "r;;/es2", Resource.class, "r;;/es3");
    }

    @Test
    public void testToString() throws Exception {
        Assert.assertEquals("/t;t/e;e/r;r", CanonicalPath.of().tenant("t").environment("e").resource("r").get()
                .toString());
        Assert.assertEquals("/t;t/e;e/f;f/r;r", CanonicalPath.of().tenant("t").environment("e").feed("f").resource("r")
                .get().toString());
        Assert.assertEquals("/rl;r", CanonicalPath.of().relationship("r").get().toString());

        Assert.assertEquals("/t;t/e;e/r;r/d;configuration/blah/1/key", CanonicalPath.of().tenant("t").environment("e")
                .resource("r").configuration().key("blah").index(1).key("key").get().toString());

        Assert.assertEquals("/t;t/e;e/r;r/d;connectionConfiguration/bl%2Fah", CanonicalPath.of().tenant("t")
                .environment("e").resource("r").connectionConfiguration().key("bl/ah").get().toString());

        // escaped chars scenario
        Assert.assertEquals("/t;te%2Fnant/e;e;nv/r;r%25%2Fes;;", CanonicalPath.of().tenant("te/nant")
                .environment("e;nv").resource("r%/es;;").get().toString());

        Assert.assertEquals("/t;t/e;e/r;res%2F1;res%252/r;res;%203", CanonicalPath.of().tenant("t").environment("e")
                .resource("res/1;res%2").resource("res; 3").get().toString());
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

        CanonicalPath p2 = CanonicalPath.of().tenant("t").environment("e").feed("f").metric(
                "m/e;t%r%|%C").get();
        Assert.assertEquals("/t;t/e;e/f;f/m;m%2Fe;t%25r%25%7C%25C", p2.down().up().toString());

        CanonicalPath p3 = CanonicalPath.of().tenant("t").environment("e").feed("f").resource(
            "res1").resource("res2").get();
        Assert.assertEquals("/t;t/e;e/f;f/r;res1/r;res2", p3.down().up().toString());
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
    public void testExtendingWithEscapedChars() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").feed("f").get();

        CanonicalPath p2 = p.extend(Metric.class, "m%et%/ric").get();
        Assert.assertEquals("/t;t/e;e/f;f/m;m%25et%25%2Fric", p2.toString());

        p2 = p.getRoot().extend(MetricType.class, "m%et%/ric;type").get();
        Assert.assertEquals("/t;t/mt;m%25et%25%2Fric;type", p2.toString());

        CanonicalPath p3 = p.extend(Metric.class, "/;/%").get();
        Assert.assertEquals("/t;t/e;e/f;f/m;%2F;%2F%25", p3.toString());
    }

    @Test
    public void testIdExtraction() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").resource("r").get();
        Assert.assertEquals("t", p.ids().getTenantId());
        Assert.assertEquals("e", p.ids().getEnvironmentId());
        Assert.assertEquals("r;r", p.ids().getResourcePath().toString());

        Assert.assertEquals("f", p.up().extend(Feed.class, "f").get().ids().getFeedId());
        Assert.assertEquals("r;r", p.up().extend(Feed.class, "f").extend(Resource.class, "r").get().ids()
                .getResourcePath().toString());
    }

    @Test
    public void testIdExtractionWithEscapedChars() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t/e;n/%").environment("/;env/")
                .resource("/res/").get();
        Assert.assertEquals("t/e;n/%", p.ids().getTenantId());
        Assert.assertEquals("/;env/", p.ids().getEnvironmentId());
        Assert.assertEquals("/res/", p.ids().getResourcePath().getSegment().getElementId());
        Assert.assertEquals("f/;e", p.up().extend(Feed.class, "f/;e").get().ids().getFeedId());
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

        //the '..' lead us up to the "unknown" before trying to go down to the tenant, so this is valid
        rp = RelativePath.fromString("../e;e/../t;t");
        Assert.assertEquals("../e;e/../t;t", rp.toString());

        // escaped chars scenario
        rp = RelativePath.fromString("../e;e%2f;nv/../t;t%2fenant");
        Assert.assertEquals("../e;e%2F;nv/../t;t%2Fenant", rp.toString());

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

        try {
            RelativePath.fromString("f;f/r;r/../e;e"); //environments cannot be inside feeds
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

        // escaped chars scenario
        CanonicalPath cp2 = CanonicalPath.of().tenant("t\\/enant").environment("e\\;nv").resource("r").get();
        RelativePath rp2 = RelativePath.to().up().metric("m\\/\\;etric").get();
        cp2 = rp2.applyTo(cp2);
        Assert.assertEquals(CanonicalPath.of().tenant("t\\/enant").environment("e\\;nv").metric("m\\/\\;etric").get(),
            cp2);

        try {
            cp = RelativePath.to().up().get().applyTo(cp);

            rp.applyTo(cp);
            Assert.fail("Should not be possible to construct invalid canonical path by applying a relative one.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testUntypedRelativePath() throws Exception {
        CanonicalPath cp = CanonicalPath.of().tenant("t").environment("e").feed("f").get();

        Path mp = Path.fromPartiallyUntypedString("g", cp, cp, Resource.class);
        Assert.assertEquals("r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("../g", cp, cp, Resource.class);
        Assert.assertEquals("../r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("../f;x/g", cp, cp, Resource.class);
        Assert.assertEquals("../f;x/r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("../r;g/h/i", cp, cp, Resource.class);
        Assert.assertEquals("../r;g/r;h/r;i", mp.toString());

        mp = Path.fromPartiallyUntypedString("../../env/me", cp, cp, Metric.class);
        Assert.assertEquals("../../e;env/m;me", mp.toString());

        mp = Path.fromPartiallyUntypedString("/g", cp, cp, Resource.class);
        Assert.assertEquals("/t;t/e;e/f;f/r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("/g/h", cp, cp, Resource.class);
        Assert.assertEquals("/t;t/e;e/f;f/r;g/r;h", mp.toString());

        mp = Path.fromPartiallyUntypedString("/g", cp, cp, Metric.class);
        Assert.assertEquals("/t;t/e;e/f;f/m;g", mp.toString());

        // escaped chars scenario
        mp = Path.fromPartiallyUntypedString("/res%2F1/res%2F2", cp, cp, Resource.class);
        Assert.assertEquals("/t;t/e;e/f;f/r;res%2F1/r;res%2F2", mp.toString());

        //testing robustness against letter case in escape sequences and trailing type delimiter, too
        mp = Path.fromPartiallyUntypedString("/%2fg;", cp, cp, Metric.class);
        Assert.assertEquals("/t;t/e;e/f;f/m;%2Fg;", mp.toString());
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
