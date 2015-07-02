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
package org.hawkular.inventory.rest.test;

import java.util.Collections;
import java.util.UUID;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.rest.Security;
import org.junit.Test;

/**
 * Very simple unit tests for the stable ids generation.
 *
 * @author Jirka Kremser
 * @since 0.1.0
 */
public class StableIdsTest {
    private final String tenantId = UUID.nameUUIDFromBytes("acme".getBytes()).toString();
    private final String environmentId = "test";
    private final String feedId = "smith";
    private final String metricId = "duration";
    private final String metricTypeId = "durationType";
    private final String resourceId = "res1";
    private final String resourceTypeId = "URL";

    private final Tenant tenant = new Tenant(CanonicalPath.of().tenant(tenantId).get());
    private final Environment environment = new Environment(CanonicalPath.of().tenant(tenantId)
            .environment(environmentId).get());
    private final Feed feed = new Feed(CanonicalPath.of().tenant(tenantId).environment(environmentId).feed(feedId)
            .get());
    private final MetricType metricType = new MetricType(CanonicalPath.of().tenant(tenantId).metricType(metricTypeId)
            .get(), MetricUnit.SECONDS);
    private final Metric metric = new Metric(CanonicalPath.of().tenant(tenantId).environment(environmentId).feed(feedId)
            .metric(metricId).get(), metricType);
    private final ResourceType resourceType = new ResourceType(CanonicalPath.of().tenant(tenantId)
            .resourceType(resourceTypeId).get());
    private final Resource resource = new Resource(CanonicalPath.of().tenant(tenantId).environment(environmentId)
            .feed(feedId).resource(resourceId).get(), resourceType);

    private final String longString = String.join("", Collections.nCopies(50, "trololo"));

    @Test
    public void testStableIdForTenant() throws Exception {
        assert ("tenants/" + tenantId).equals(Security.getStableId(tenant))
                : "tenants/" + tenantId + " should be equal to " + Security.getStableId(tenant);
        assert "tenants/foo".equals(Security.getStableId(new Tenant(CanonicalPath.of().tenant("foo").get())))
                : "tenants/foo should be equal to " + Security.getStableId(new Tenant(CanonicalPath.of().tenant("foo")
                .get()));
    }

    @Test
    public void testStableIdForEnvironment() throws Exception {
        assert (tenantId + "/environments/" + environmentId).equals(Security.getStableId(environment))
                : tenantId + "/environments/" + environmentId + " should be equal to " +
                Security.getStableId(environment);
    }

    @Test
    public void testStableIdForFeed() throws Exception {
        assert (tenantId + "/" + environmentId + "/feeds/" + feedId).equals(Security.getStableId(feed))
                : tenantId + "/" + environmentId + "/feeds/" + feedId + " should be equal to " +
                Security.getStableId(feed);
    }

    @Test
    public void testStableIdForMetric1() throws Exception {
        assert (tenantId + "/" + environmentId + "/" + feedId + "/metrics/" + metricId).equals(Security.getStableId
                (metric))
                : tenantId + "/" + environmentId + "/" + feedId + "/metrics/" + metricId + " should be equal to " +
                Security.getStableId(metric);
    }

    @Test
    public void testStableIdForMetric2() throws Exception {
        Metric feedless = new Metric(CanonicalPath.of().tenant(tenantId).environment(environmentId).metric(metricId)
                .get(), metricType);
        assert (tenantId + "/" + environmentId + "/metrics/" + metricId).equals(Security.getStableId(feedless))
                : tenantId + "/" + environmentId + "/metrics/" + metricId + " should be equal to " +
                Security.getStableId(feedless);
    }

    @Test
    public void testStableIdForResource1() throws Exception {
        assert (tenantId + "/" + environmentId + "/" + feedId + "/resources/" + resourceId).equals(Security.getStableId
                (resource))
                : tenantId + "/" + environmentId + "/" + feedId + "/resources/" + resourceId + " should be equal to " +
                Security.getStableId(resource);
    }

    @Test
    public void testStableIdForResource2() throws Exception {
        Resource feedless = new Resource(CanonicalPath.of().tenant(tenantId).environment(environmentId)
                .resource(resourceId).get(), resourceType);
        assert (tenantId + "/" + environmentId + "/resources/" + resourceId).equals(Security.getStableId(feedless))
                : tenantId + "/" + environmentId + "/resources/" + resourceId + " should be equal to " +
                Security.getStableId(feedless);
    }

    @Test
    public void testStableIdForResourceTypes() throws Exception {
        assert (tenantId + "/resourceTypes/" + resourceTypeId).equals(Security.getStableId(resourceType))
                : "'" + tenantId + "/resourceTypes/" + resourceTypeId + "' should be equal to '" +
                Security.getStableId(resourceType) + "'";
    }

    @Test
    public void testStableIdForMetricTypes() throws Exception {
        assert (tenantId + "/metricTypes/" + metricTypeId).equals(Security.getStableId(metricType)) :
                tenantId + "/metricTypes/" + metricTypeId + " should be equal to " + Security.getStableId(metricType);
    }

    @Test
    public void testStableIdForRelationships() throws Exception {
        Relationship rel = new Relationship("fooId", "label", tenant.getPath(), resource.getPath());
        assert "relationships/fooId".equals(Security.getStableId(Relationship.class, rel.getId()))
                : "relationships/fooId should be equal to " + Security.getStableId(Relationship.class, rel.getId());
    }

    @Test
    public void testStableIdForLongIds1() throws Exception {
        Metric metric = new Metric(CanonicalPath.of().tenant(tenantId).environment(environmentId).feed(feedId)
                .metric(longString).get(), metricType);
        String uuid = UUID.nameUUIDFromBytes((tenantId + "/" + environmentId + "/" + feedId + "/metrics/" + longString)
                .getBytes()).toString();
        assert (tenantId + "/" + uuid).equals(Security.getStableId(metric))
                : tenantId + "/" + uuid + " should be equal to " + Security.getStableId(metric);
    }

    @Test
    public void testStableIdForLongIds2() throws Exception {
        Resource resource = new Resource(CanonicalPath.of().tenant(tenantId).environment(environmentId).feed(feedId)
                .resource(longString).get(), resourceType);
        String uuid = UUID.nameUUIDFromBytes((tenantId + "/" + environmentId + "/" + feedId + "/resources/" +
                longString).getBytes()).toString();
        assert (tenantId + "/" + uuid).equals(Security.getStableId(resource))
                : tenantId + "/" + uuid + " should be equal to " + Security.getStableId(resource);
    }

}
