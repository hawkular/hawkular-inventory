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
package org.hawkular.inventory.rest;

import static org.hawkular.inventory.api.Query.path;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.name;
import static org.hawkular.inventory.api.filters.With.type;
import static org.hawkular.inventory.paths.SegmentType.m;
import static org.hawkular.inventory.paths.SegmentType.r;
import static org.junit.Assert.assertEquals;

import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.filters.RecurseFilter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.SwitchElementType;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.16.0.Final
 */
public class TraverserTest {

    @Test
    public void testStartByRelationship_simpleUri() throws Exception {
        testVariant("/rl;id/relationships", path().with(RelationWith.id("id")).get());
    }

    @Test
    public void testStartByRelationship_ignoredDirectionIfNotNeeded() throws Exception {
        testVariant("/rl;id;in/relationships", path().with(RelationWith.id("id")).get());
    }

    @Test
    public void testStartByRelationship_propertyExistence() throws Exception {
        testVariant("/rl;id;propertyName=kachna/relationships",
                path().with(RelationWith.id("id"), RelationWith.property("kachna")).get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartByRelationship_unmatchedPropertyValueSimple() throws Exception {
        testVariant("/rl;id;propertyValue=kachna/relationship", null);
    }

    @Test
    public void testStartByRelationship_propertyValue() throws Exception {
        testVariant("/rl;id;propertyName=kachna;propertyValue=duck/relationships",
                path().with(RelationWith.id("id"), RelationWith.propertyValue("kachna", "duck")).get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartByRelationship_umatchedPropertyValueComplex() throws Exception {
        testVariant("/rl;id;propertyName=kachna;propertyValue=duck;propertyValue=d/relationship",
                null);
    }

    @Test
    public void testStartByRelationship_jumpToTargets_implicitDirection() throws Exception {
        testVariant("/rl;id/entities",
                path().with(RelationWith.id("id"), SwitchElementType.targetEntities()).get());
    }

    @Test
    public void testStartByRelationship_jumpToTargets_out() throws Exception {
        testVariant("/rl;id;out/entities",
                path().with(RelationWith.id("id"), SwitchElementType.targetEntities()).get());
    }

    @Test
    public void testStartByRelationship_jumpToTargets_in() throws Exception {
        testVariant("/rl;id;in/entities",
                path().with(RelationWith.id("id"), SwitchElementType.sourceEntities()).get());
    }

    @Test
    public void testStartByRelationship_traverseToEntities() throws Exception {
        testVariant("/rl;id/r;id/entities", path().with(RelationWith.id("id"), SwitchElementType.targetEntities())
                .path().with(type(Resource.class), id("id")).get());
    }

    @Test
    public void testStartByRelationship_traverseToFilter() throws Exception {
        testVariant("/rl;id;in/type=rt/entities", path().with(RelationWith.id("id"), SwitchElementType.sourceEntities())
                .path().with(type(ResourceType.class)).get());
    }

    @Test
    public void testQueryPrefixUsedForURIsStartingWithEntity() throws Exception {
        //this query is invalid, but that doesn't matter here... Query is untyped and doesn't enforce correctness
        //The real REST API prefixes it with something meaningful
        testVariant("/r;id/entities", path().with(id("eee"))
                .path().with(type(Resource.class), id("id")).get(), Query.builder().path().with(id("eee")));
    }

    @Test
    public void testSimpleEntityQuery() throws Exception {
        testVariant("/r;id", path().with(type(Resource.class), id("id")).get());
        testVariant("/r;id/entities", path().with(type(Resource.class), id("id")).get());
        testVariant("/r;id%20with%20escapes", path().with(type(Resource.class), id("id with escapes")).get());
    }

    @Test
    public void testSimpleEntityQuery_filters() throws Exception {
        testVariant("/r;id;name=kachna/entities", path().with(type(Resource.class), id("id"))
                .filter().with(name("kachna")).get());
    }

    @Test
    public void testSimpleEntityQuery_returnRelationships_implicitDirection() throws Exception {
        testVariant("/r;id/relationships", path().with(type(Resource.class), id("id"))
                .path().with(SwitchElementType.outgoingRelationships()).get());
    }

    @Test
    public void testSimpleEntityQuery_returnRelationships_in() throws Exception {
        testVariant("/r;id/relationships;in", path().with(type(Resource.class), id("id"))
                .path().with(SwitchElementType.incomingRelationships()).get());

    }

    @Test
    public void testSimpleEntityQuery_returnRelationships_filterRels() throws Exception {
        testVariant("/r;id/relationships;name=kachna;propertyName=alois", path().with(type(Resource.class), id("id"))
                .path().with(SwitchElementType.outgoingRelationships(), RelationWith.property("alois"),
                        RelationWith.name("kachna")).get());
    }

    @Test
    public void testSimpleEntityQuery_returnRelationships_out() throws Exception {
        testVariant("/r;id/relationships;out", path().with(type(Resource.class), id("id"))
                .path().with(SwitchElementType.outgoingRelationships()).get());
    }
    @Test
    public void testEntityQuery_implicitContains() throws Exception {
        testVariant("/r;id/rt;id", path().with(type(Resource.class), id("id"))
                .path().with(Related.by(contains))
                .path().with(type(ResourceType.class), id("id")).get());
    }

    @Test
    public void testEntityQuery_implicitContains_cpOptimize() throws Exception {
        testVariant("/t;id/rt;id/entities",
                path().with(With.path(CanonicalPath.of().tenant("id").resourceType("id").get())).get());
    }

    @Test
    public void testEntityQuery_implicitContains_cpOptimize_byPrefix() throws Exception {
        testVariant("/rt;id/entities",
                Query.path().with(With.path(CanonicalPath.of().tenant("id").resourceType("id").get())).get(),
                Query.builder().path().with(With.path(CanonicalPath.of().tenant("id").get()))
                        .with(Related.by(contains)));
    }

    @Test
    public void testEntityQuery_implicitContainsAndFilters() throws Exception {
        testVariant("/t;id;type=metric/rt;id;name=kachna/entities",
                path().with(type(Tenant.class), id("id"), type(Metric.class))
                .path().with(Related.by(contains))
                .path().with(type(ResourceType.class), id("id"))
                .filter().with(name("kachna")).get());
    }

    @Test
    public void testEntityQuery_jumpOverExplicitRelationship() throws Exception {
        testVariant("/t;id/rl;my-rel/rt;id/entities",
                path().with(type(Tenant.class), id("id"))
                .path().with(Related.by("my-rel"))
                .path().with(type(ResourceType.class), id("id")).get());
    }

    @Test
    public void testEntityQuery_recursive_implicitRel() throws Exception {
        testVariant("/t;id/recursive;type=r/name=Kacer",
                path().with(type(Tenant.class), id("id"))
                        .path()
                        .with(RecurseFilter.builder().addChain(Related.by(contains), type(Resource.class)).build())
                        .filter().with(With.name("Kacer")).get());
    }

    @Test
    public void testEntityQuery_recursive_explicitRel() throws Exception {
        testVariant("/t;id/recursive;over=kachna;type=r/entities;name=Kacer",
                path().with(type(Tenant.class), id("id"))
                        .path()
                        .with(RecurseFilter.builder().addChain(Related.by("kachna"), type(Resource.class)).build())
                        .filter().with(With.name("Kacer")).get());
    }

    @Test
    public void testEntityQuery_recursive_noType() throws Exception {
        testVariant("/t;id/recursive;over=kachna/type=m",
                path().with(type(Tenant.class), id("id"))
                        .path()
                        .with(RecurseFilter.builder().addChain(Related.by("kachna")).build())
                        .path().with(type(Metric.class)).get());
    }

    @Test
    public void testEntityQuery_recursive_noType_implicitRel() throws Exception {
        testVariant("/t;id/recursive/type=m",
                path().with(type(Tenant.class), id("id"))
                        .path()
                        .with(RecurseFilter.builder().addChain(Related.by(contains)).build())
                        .path().with(type(Metric.class)).get());
    }

    @Test
    public void testFilterQuery_simple() throws Exception {
        testVariant("/r;id/type=rt/entities",
                path().with(type(Resource.class), id("id"))
                .path().with(Related.by(contains))
                .path().with(type(ResourceType.class)).get());
    }

    @Test
    public void testFilterQuery_multipleFilters() throws Exception {
        testVariant("/r;id/type=resource;name=kachna/entities", path().with(type(Resource.class), id("id"))
                .path().with(Related.by(contains)).path().with(type(Resource.class)).filter().with(name("kachna"))
                .get());
    }

    @Test
    public void testFilterQuery_implicitContains() throws Exception {
        testVariant("/r;id/type=r/type=m/entities",
                path().with(type(Resource.class), id("id"))
                        .path().with(Related.by("contains"))
                        .path().with(type(Resource.class))
                        .path().with(Related.by(contains))
                        .path().with(type(Metric.class)).get());
    }

    @Test
    public void testFilterQuery_relatedByToPairs() throws Exception {
        testVariant("/e;f;relatedTo=%2Ft%3Bid;relatedBy=kachna",
                path().with(type(SegmentType.e), id("f"))
                .filter().with(Related.with(CanonicalPath.of().tenant("id").get(), "kachna"))
                        .get());
    }

    @Test
    public void testFilterQuery_relatedByToWithPairs() throws Exception {
        testVariant("/e;f;relatedTo=%2Ft%3Bid;relatedBy=kachna;relatedWith=%2Ft%3Bid;relatedBy=drachma",
                path().with(type(SegmentType.e), id("f"))
                        .filter().with(Related.with(CanonicalPath.of().tenant("id").get(), "kachna"),
                            Related.asTargetWith(CanonicalPath.of().tenant("id").get(), "drachma"))
                        .get());
    }

    @Test
    public void testPrefixDoesntApplyToStartWithRelationship() throws Exception {
        testVariant("/rl;id/relationships",
                path().with(RelationWith.id("id")).get(),
                Query.builder().with(id("eee")));
    }

    @Test
    public void testPathEndQuery_entity() throws Exception {
        testVariant("/entities", Query.empty());
    }

    @Test
    public void testPathEndQuery_relationships() throws Exception {
        testVariant("/relationships", Query.path().with(SwitchElementType.outgoingRelationships()).get());
    }

    @Test
    public void testPathEndQuery_relationships_explicitDirection() throws Exception {
        testVariant("/relationships;in", Query.path().with(SwitchElementType.incomingRelationships()).get());
    }

    @Test
    public void testPathEndQuery_relationships_filters() throws Exception {
        testVariant("/relationships;name=kachna", Query.path().with(SwitchElementType.outgoingRelationships(),
                RelationWith.name("kachna")).get());
    }

    @Test
    public void testEscapingInFilterNamesAndValues() throws Exception {
        testVariant("/name=A%20B", Query.path().filter().with(name("A B")).get());
    }

    @Test
    public void testIdenticalProgression() throws Exception {
        testVariant("/r;id/identical/name=Kachna/rl;contains/m;id", Query.path()
                .with(type(r), id("id"), With.sameIdentityHash())
                .filter().with(name("Kachna")).path().with(Related.by(contains), type(m), id("id")).get());
    }

    private void testVariant(String uri, Query expected) throws Exception {
        testVariant(uri, expected, Query.builder());
    }

    private void testVariant(String uri, Query expected, Query.Builder prefix) throws Exception {
        Traverser traverser = new Traverser(0, prefix, CanonicalPath::fromString);

        Query actual = traverser.navigate(uri);
        assertEquals(expected, actual);
    }
}
