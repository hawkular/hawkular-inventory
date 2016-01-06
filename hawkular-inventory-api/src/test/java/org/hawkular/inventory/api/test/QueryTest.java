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

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.FilterFragment;
import org.hawkular.inventory.api.PathFragment;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.SegmentType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.base.spi.NoopFilter;
import org.hawkular.inventory.base.spi.SwitchElementType;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.6.0
 */
public class QueryTest {

    @Test
    public void testCanonicalPathFilterUsed() throws Exception {
        Query q = Query.to(CanonicalPath.of().tenant("t").environment("e").get());
        Assert.assertEquals(1, q.getFragments().length);
        Assert.assertTrue(q.getFragments()[0].getFilter() instanceof With.CanonicalPaths);
    }

    @Test
    public void testCanonicalPathOptimizationOnJustTenant() throws Exception {
        Query q = Query.path().with(type(Tenant.class), id("id")).get();

        Assert.assertEquals(1, q.getFragments().length);
        Assert.assertTrue(q.getFragments()[0].getFilter() instanceof With.CanonicalPaths);

        CanonicalPath[] cps = ((With.CanonicalPaths) q.getFragments()[0].getFilter()).getPaths();
        Assert.assertEquals(1, cps.length);
        Assert.assertEquals(SegmentType.t, cps[0].getSegment().getElementType());
        Assert.assertEquals("id", cps[0].getSegment().getElementId());
    }

    @Test
    public void testCanonicalPathOnLongPath() throws Exception {
        Query q = Query.path().with(type(Tenant.class)).with(id("t")).with(by(contains)).with(type(Environment.class))
                .with(id("e")).with(by(contains)).with(type(Resource.class)).with(id("r")).get();

        Assert.assertEquals(1, q.getFragments().length);
        Assert.assertTrue(q.getFragments()[0].getFilter() instanceof With.CanonicalPaths);

        CanonicalPath[] cps = ((With.CanonicalPaths) q.getFragments()[0].getFilter()).getPaths();
        Assert.assertEquals(1, cps.length);
        Assert.assertEquals(CanonicalPath.of().tenant("t").environment("e").resource("r").get(), cps[0]);
    }

    @Test
    public void testCanonicalPathRobustAgainstInvalidPaths() throws Exception {
        Query q = Query.path().with(type(Tenant.class)).with(id("t")).with(by(contains)).with(type(Environment.class))
                .with(id("e")).with(by(contains)).with(type(Tenant.class)).with(id("t2")).get();

        Assert.assertEquals(3, q.getFragments().length);
        Assert.assertTrue(q.getFragments()[0].getFilter() instanceof With.CanonicalPaths);
        Assert.assertTrue(q.getFragments()[1].getFilter() instanceof Related);
        Assert.assertTrue(q.getFragments()[2].getFilter() instanceof With.CanonicalPaths);
    }

    @Test
    public void testQueryFragmentTypeChangeInterruptsOptimization() throws Exception {
        Query q = Query.path().with(type(Tenant.class)).with(id("t")).filter().with(by(contains)).with(type
                (Environment.class)).with(id("e")).with(by(contains)).with(type(Tenant.class)).with(id("t2")).get();

        Assert.assertEquals(6, q.getFragments().length);
        Assert.assertTrue(q.getFragments()[0].getFilter() instanceof With.CanonicalPaths);
        Assert.assertTrue(q.getFragments()[1].getFilter() instanceof Related);
        Assert.assertTrue(q.getFragments()[2].getFilter() instanceof With.Types);
        Assert.assertTrue(q.getFragments()[3].getFilter() instanceof With.Ids);
        Assert.assertTrue(q.getFragments()[4].getFilter() instanceof Related);
        Assert.assertTrue(q.getFragments()[5].getFilter() instanceof With.CanonicalPaths);
        Assert.assertTrue(q.getFragments()[0] instanceof PathFragment);
        Assert.assertTrue(q.getFragments()[1] instanceof FilterFragment);
        Assert.assertTrue(q.getFragments()[2] instanceof FilterFragment);
        Assert.assertTrue(q.getFragments()[3] instanceof FilterFragment);
        Assert.assertTrue(q.getFragments()[4] instanceof FilterFragment);
        Assert.assertTrue(q.getFragments()[5] instanceof FilterFragment);
    }

    @Test
    public void testOptimizationRessurectsAfterNonCanonicalSection() throws Exception {
        Query q = Query.path().with(type(Tenant.class)).with(id("t")).with(by(contains)).with(type
                (Environment.class)).with(id("e")).with(by("kachna")).with(type(Tenant.class)).with(id("t2")).get();

        Assert.assertEquals(3, q.getFragments().length);
        Assert.assertTrue(q.getFragments()[0].getFilter() instanceof With.CanonicalPaths);
        Assert.assertTrue(q.getFragments()[1].getFilter() instanceof Related);
        Assert.assertTrue(q.getFragments()[2].getFilter() instanceof With.CanonicalPaths);
    }

    @Test
    public void testQueryBuilder() {
        CanonicalPath cp1 = CanonicalPath.fromString("/t;tenant1");
        CanonicalPath cp2 = CanonicalPath.fromString("/t;tenant2");

        // old query
        Query qMetricsRelationships = Query.path().with(
                With.path(cp1),
                SwitchElementType.incomingRelationships(),
                RelationWith.name("name")).get();
        Query qTypesRelationships = Query.path().with(
                With.path(cp2),
                SwitchElementType.incomingRelationships(),
                RelationWith.name("name")).get();
        Query oldQuery = new Query.Builder().with(new PathFragment(new NoopFilter()))
                .branch().with(qMetricsRelationships).done()
                .branch().with(qTypesRelationships).done().build();

        // new query
        Query.Builder builder = new Query.Builder()
                .branch().path().with(With.path(cp1), SwitchElementType.incomingRelationships(),
                        RelationWith.name("name")).done()
                .branch().path().with(With.path(cp2), SwitchElementType.incomingRelationships(),
                        RelationWith.name("name")).done();

        Query newQuery = builder.build();

        Assert.assertTrue(newQuery.equals(oldQuery));
    }
}
