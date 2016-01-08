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

import static org.hawkular.inventory.api.Resources.DataRole.configuration;
import static org.hawkular.inventory.api.Resources.DataRole.connectionConfiguration;

import java.util.Iterator;

import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public class CanonicalPathTest {

    @Test
    public void testParse() throws Exception {
        CanonicalPath p = CanonicalPath.fromString("/t;t/f;f/r;r");
        checkPath(p, SegmentType.t, "t", SegmentType.f, "f", SegmentType.r, "r");

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
        checkPath(p, SegmentType.rl, "r");

        try {
            CanonicalPath.fromString("/rl;r/t/t");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            CanonicalPath.fromString("/t;t/f;f/m;m1/m;m2");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }

        try {
            CanonicalPath.fromString("/t;t/f;f/m1/m2//");
            Assert.fail("Invalid path parse should have failed");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    @Test
    public void testParseWithEscapedChars() throws Exception {
        CanonicalPath p = CanonicalPath.fromString("/t;te%2Fn;a%2fnt/f;f%2f%25eed/r;r;;%2fes");
        checkPath(p, SegmentType.t, "te/n;a/nt", SegmentType.f, "f/%eed", SegmentType.r, "r;;/es");

        p = CanonicalPath
                .fromString("/t;te%2Fn;a%2Fnt/f;f%2F%25eed/r;r;;%2Fes1/r;r;;%2Fes2/r;r;;%2Fes3");
        // not sure if this should pass
        checkPath(p, SegmentType.t, "te/n;a/nt", SegmentType.f, "f/%eed", SegmentType.r, "r;;/es1",
                SegmentType.r, "r;;/es2", SegmentType.r, "r;;/es3");
    }

    @Test
    public void testToString() throws Exception {
        Assert.assertEquals("/t;t/e;e/r;r", CanonicalPath.of().tenant("t").environment("e").resource("r").get()
                .toString());
        Assert.assertEquals("/t;t/f;f/r;r", CanonicalPath.of().tenant("t").feed("f").resource("r")
                .get().toString());
        Assert.assertEquals("/rl;r", CanonicalPath.of().relationship("r").get().toString());

        Assert.assertEquals("/t;t/e;e/r;r/d;configuration/blah/1/key", CanonicalPath.of().tenant("t").environment("e")
                .resource("r").data(configuration.name()).key("blah").index(1).key("key").get().toString());

        Assert.assertEquals("/t;t/e;e/r;r/d;connectionConfiguration/bl%2Fah", CanonicalPath.of().tenant("t")
                .environment("e").resource("r").data(connectionConfiguration.name()).key("bl/ah").get().toString());

        // escaped chars scenario
        Assert.assertEquals("/t;te%2Fnant/e;e;nv/r;r%25%2Fes;;", CanonicalPath.of().tenant("te/nant")
                .environment("e;nv").resource("r%/es;;").get().toString());

        Assert.assertEquals("/t;t/e;e/r;res%2F1;res%252/r;res;%203", CanonicalPath.of().tenant("t").environment("e")
                .resource("res/1;res%2").resource("res; 3").get().toString());
    }

    @Test
    public void testTraversals() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").feed("f").metric("m").get();

        Assert.assertEquals(2, p.getDepth());

        Assert.assertFalse(p.down().isDefined());

        CanonicalPath cp = p.getRoot();
        Assert.assertEquals("/t;t", cp.toString());

        Assert.assertEquals(p.toString(), cp.getLeaf().toString());

        Assert.assertFalse(cp.up().isDefined());

        Assert.assertEquals("/t;t/f;f", cp.down().toString());

        Assert.assertEquals("/t;t", p.up().up().toString());

        CanonicalPath p2 = CanonicalPath.of().tenant("t").feed("f").metric("m/e;t%r%|%C").get();
        Assert.assertEquals("/t;t/f;f/m;m%2Fe;t%25r%25%7C%25C", p2.down().up().toString());

        CanonicalPath p3 = CanonicalPath.of().tenant("t").feed("f").resource("res1").resource("res2").get();
        Assert.assertEquals("/t;t/f;f/r;res1/r;res2", p3.down().up().toString());
    }

    @Test
    public void testExtending() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").feed("f").get();

        CanonicalPath p2 = p.extend(SegmentType.m, "m").get();
        Assert.assertEquals("/t;t/f;f/m;m", p2.toString());

        p2 = p.getRoot().extend(MetricType.class, "mt").get();
        Assert.assertEquals("/t;t/mt;mt", p2.toString());
    }

    @Test
    public void testExtendingWithEscapedChars() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").feed("f").get();

        CanonicalPath p2 = p.extend(SegmentType.m, "m%et%/ric").get();
        Assert.assertEquals("/t;t/f;f/m;m%25et%25%2Fric", p2.toString());

        p2 = p.getRoot().extend(MetricType.class, "m%et%/ric;type").get();
        Assert.assertEquals("/t;t/mt;m%25et%25%2Fric;type", p2.toString());

        CanonicalPath p3 = p.extend(SegmentType.m, "/;/%").get();
        Assert.assertEquals("/t;t/f;f/m;%2F;%2F%25", p3.toString());
    }

    @Test
    public void testIdExtraction() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").resource("r").get();
        Assert.assertEquals("t", p.ids().getTenantId());
        Assert.assertEquals("e", p.ids().getEnvironmentId());
        Assert.assertEquals("r;r", p.ids().getResourcePath().toString());
    }

    @Test
    public void testIdExtractionWithEscapedChars() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t/e;n/%").environment("/;env/")
                .resource("/res/").get();
        Assert.assertEquals("t/e;n/%", p.ids().getTenantId());
        Assert.assertEquals("/;env/", p.ids().getEnvironmentId());
        Assert.assertEquals("/res/", p.ids().getResourcePath().getSegment().getElementId());
        Assert.assertEquals("f/;e", p.up().up().extend(SegmentType.f, "f/;e").get().ids().getFeedId());
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
        CanonicalPath cp = CanonicalPath.of().tenant("t").feed("f").metric("m").get();

        int i = 2;
        for (CanonicalPath p : cp) {
            Assert.assertEquals(cp.getPath().get(i--), p.getSegment());
        }

        i = 0;
        Iterator<CanonicalPath> it = cp.descendingIterator();
        while (it.hasNext()) {
            Assert.assertEquals(cp.getPath().get(i++), it.next().getSegment());
        }

        i = 2;
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
        CanonicalPath cp = CanonicalPath.of().tenant("t").feed("f").get();

        Path mp = Path.fromPartiallyUntypedString("g", cp, cp, SegmentType.r);
        Assert.assertEquals("r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("../g", cp, cp.extend(SegmentType.m, "m").get(), SegmentType.r);
        Assert.assertEquals("../r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("../f;x/g", cp, cp, SegmentType.r);
        Assert.assertEquals("../f;x/r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("../r;g/h/i", cp, cp.extend(SegmentType.m, "m").get(), SegmentType.r);
        Assert.assertEquals("../r;g/r;h/r;i", mp.toString());

        mp = Path.fromPartiallyUntypedString("../e;env/me", cp, cp, SegmentType.m);
        Assert.assertEquals("../e;env/m;me", mp.toString());

        mp = Path.fromPartiallyUntypedString("/g", cp, cp, SegmentType.r);
        Assert.assertEquals("/t;t/f;f/r;g", mp.toString());

        mp = Path.fromPartiallyUntypedString("/g/h", cp, cp, SegmentType.r);
        Assert.assertEquals("/t;t/f;f/r;g/r;h", mp.toString());

        mp = Path.fromPartiallyUntypedString("/g", cp, cp, SegmentType.m);
        Assert.assertEquals("/t;t/f;f/m;g", mp.toString());

        // escaped chars scenario
        mp = Path.fromPartiallyUntypedString("/res%2F1/res%2F2", cp, cp, SegmentType.r);
        Assert.assertEquals("/t;t/f;f/r;res%2F1/r;res%2F2", mp.toString());

        //testing robustness against letter case in escape sequences and trailing type delimiter, too
        mp = Path.fromPartiallyUntypedString("/%2fg;", cp, cp, SegmentType.m);
        Assert.assertEquals("/t;t/f;f/m;%2Fg;", mp.toString());
    }

    @Test
    public void testExtensionChecks() throws Exception {
        Assert.assertTrue(CanonicalPath.of().tenant("a").get().modified().canExtendTo(SegmentType.e));
        Assert.assertNull(CanonicalPath.of().tenant("a").get().modified().canExtendTo(SegmentType.t));
        Assert.assertFalse(CanonicalPath.of().tenant("a").get().modified().canExtendTo(SegmentType.m));
    }

    @Test
    public void testResourceIdsBackedByCanonicalPath() throws Exception {
        CanonicalPath p = CanonicalPath.of().tenant("t").environment("e").resource("1").resource("2").resource("3")
                .data(Resources.DataRole.configuration.name()).get();

        RelativePath rs = p.ids().getResourcePath();

        Assert.assertNotNull(rs);
        Assert.assertEquals(3, rs.getPath().size());
        Assert.assertEquals("3", rs.getSegment().getElementId());
        Assert.assertEquals("1", rs.getTop().getElementId());
        Assert.assertEquals("2", rs.up().getSegment().getElementId());
        Assert.assertEquals(new Path.Segment(SegmentType.e, "e"), rs.slide(-1, 0).getTop());
    }

    @Test
    public void testRelativePathSliding() throws Exception {
        RelativePath p =
                RelativePath.to().environment("e").resource("r").data(configuration.name()).index(1).key("a").get();

        Assert.assertEquals(p.up(), p.slide(0, -1));
        Assert.assertEquals(p.up().down(), p.slide(0, -1).slide(0, 1));

        Assert.assertFalse(p.slide(0, 1).isDefined());
        Assert.assertFalse(p.slide(-1, 0).isDefined());

        Assert.assertTrue(p.slide(-1, 0).getPath().isEmpty());

        Assert.assertEquals(new Path.Segment(SegmentType.r, "r"), p.slide(1, 0).getTop());
        Assert.assertEquals(new Path.Segment(DataEntity.class, "configuration"), p.slide(2, 0).getTop());
    }

    @SuppressWarnings("unchecked")
    private void checkPath(CanonicalPath path, Object... pathSpec) {
        Assert.assertEquals(pathSpec.length / 2, path.getPath().size());
        for (int i = 0; i < pathSpec.length; i += 2) {
            SegmentType t = (SegmentType) pathSpec[i];
            String id = (String) pathSpec[i + 1];

            CanonicalPath.Segment s = path.getPath().get(i / 2);

            //noinspection AssertEqualsBetweenInconvertibleTypes
            Assert.assertEquals(t, s.getElementType());
            Assert.assertEquals(id, s.getElementId());
        }
    }
}
