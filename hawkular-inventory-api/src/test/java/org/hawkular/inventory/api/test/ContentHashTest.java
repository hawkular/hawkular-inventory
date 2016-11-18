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

import static org.hawkular.inventory.paths.ElementTypeVisitor.accept;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.hawkular.inventory.api.model.ContentHash;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.ElementTypeVisitor;
import org.hawkular.inventory.paths.SegmentType;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.18.0
 */
public class ContentHashTest {

    @Test
    public void testComputesForEveryEntityType() throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("a", "b");
        props.put("b", "c");
        String name = "name";

        ElementTypeVisitor<Entity.Blueprint, Void> generator = new ElementTypeVisitor<Entity.Blueprint, Void>() {
            @Override public Entity.Blueprint visitTenant(Void parameter) {
                return Tenant.Blueprint.builder().withId("id").withName(name).withProperties(props).build();
            }

            @Override public Entity.Blueprint visitEnvironment(Void parameter) {
                return Environment.Blueprint.builder().withId("id").withName(name).withProperties(props).build();
            }

            @Override public Entity.Blueprint visitFeed(Void parameter) {
                return Feed.Blueprint.builder().withId("id").withName(name).withProperties(props).build();
            }

            @Override public Entity.Blueprint visitMetric(Void parameter) {
                return Metric.Blueprint.builder().withId("id").withName(name).withProperties(props)
                        .withMetricTypePath("../../metricType").withInterval(1L).build();
            }

            @Override public Entity.Blueprint visitMetricType(Void parameter) {
                return MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("id").withName(name)
                        .withProperties(props).withInterval(1L).withUnit(MetricUnit.BYTES).build();
            }

            @Override public Entity.Blueprint visitResource(Void parameter) {
                return Resource.Blueprint.builder().withId("id").withName(name).withProperties(props)
                        .withResourceTypePath("../../resourceType").build();
            }

            @Override public Entity.Blueprint visitResourceType(Void parameter) {
                return ResourceType.Blueprint.builder().withId("id").withName(name).withProperties(props).build();
            }

            @Override public Entity.Blueprint visitRelationship(Void parameter) {
                return null;
            }

            @Override public Entity.Blueprint visitData(Void parameter) {
                return DataEntity.Blueprint.builder().withId("configuration").withName(name).withProperties(props)
                        .withValue(StructuredData.get().bool(true)).build();
            }

            @Override public Entity.Blueprint visitOperationType(Void parameter) {
                return OperationType.Blueprint.builder().withId("id").withName(name).withProperties(props).build();
            }

            @Override public Entity.Blueprint visitMetadataPack(Void parameter) {
                return null;
            }

            @Override public Entity.Blueprint visitUnknown(Void parameter) {
                return null;
            }
        };

        String commonHash = hash(name, props);
        String metricHash = hash("../../mt;metricType1" + name, props); //relative metric type path followed by the
                                                                        //collection interval, followed by the name
                                                                        //and props
        String resourceHash = hash("../../rt;resourceType" + name, props);

        String metricTypeHash = hash("" + MetricDataType.GAUGE + MetricUnit.BYTES + "1" + name, props);

        String dataHash = hash(StructuredData.get().bool(true).toJSON() + "configuration", props);

        for (SegmentType t : SegmentType.values()) {
            Entity.Blueprint bl = accept(t, generator, null);

            if (bl != null) {
                //no other entity type but the resource and metric needs the canonical path to compute the content hash.
                CanonicalPath cp;
                if (bl instanceof Resource.Blueprint) {
                    cp = CanonicalPath.of().tenant("tnt").feed("fd").resource("id").get();
                } else if (bl instanceof Metric.Blueprint) {
                    cp = CanonicalPath.of().tenant("tnt").feed("fd").metric("id").get();
                } else {
                    cp = null;
                }
                String contentHash = ContentHash.of(bl, cp);

                ElementTypeVisitor.accept(t, new ElementTypeVisitor.Simple<Void, Void>() {
                    @Override protected Void defaultAction(SegmentType elementType, Void parameter) {
                        Assert.assertEquals("Unexpected content hash for " + elementType.getSimpleName(),
                                commonHash, contentHash);
                        return null;
                    }

                    @Override public Void visitMetric(Void parameter) {
                        Assert.assertEquals("Unexpected content hash for Metric", metricHash, contentHash);
                        return null;
                    }

                    @Override public Void visitMetricType(Void parameter) {
                        Assert.assertEquals("Unexpected content hash for MetricType", metricTypeHash, contentHash);
                        return null;
                    }

                    @Override public Void visitResource(Void parameter) {
                        Assert.assertEquals("Unexpected content hash for Resource", resourceHash, contentHash);
                        return null;
                    }

                    @Override public Void visitData(Void parameter) {
                        Assert.assertEquals("Unexpected content hash for DataEntity", dataHash, contentHash);
                        return null;
                    }
                }, null);
            }
        }
    }


    private String digest(String content) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(content.getBytes(Charset.forName("UTF-8")));

        StringBuilder bld = new StringBuilder();
        for (byte b : digest) {
            bld.append(Integer.toHexString(Byte.toUnsignedInt(b)));
        }

        return bld.toString();
    }

    private String hash(String name, Map<String, Object> properties) throws NoSuchAlgorithmException {
        Map<String, Object> sorted = new TreeMap<>(Comparator.naturalOrder());
        sorted.putAll(properties);

        StringBuilder content = new StringBuilder(name);
        sorted.forEach((k, v) -> content.append(k).append(v));

        return digest(content.toString());
    }
}
