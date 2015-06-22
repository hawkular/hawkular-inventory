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
package org.hawkular.inventory.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A relative path is used in the API to refer to other entities during association. Its precise meaning is
 * context-sensitive but the basic idea is that given a position in the graph, you want to refer to other entities that
 * are "near" without needing to provide their full canonical path.
 *
 * <p>I.e. it is quite usual only associate resources and metrics from a single environment. It would be cumbersome to
 * require the full canonical path for every metric one wants to associate with a resource. Therefore a partial path is
 * used to refer to the metric.
 *
 * <p>The relative path contains one special segment type - encoded as ".." and represented using the
 * {@link org.hawkular.inventory.api.model.RelativePath.Up} class that can be used to go up in the relative path.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class RelativePath extends AbstractPath<RelativePath> {

    public static final Map<String, Class<?>> SHORT_NAME_TYPES = new HashMap<>();
    public static final Map<Class<?>, String> SHORT_TYPE_NAMES = new HashMap<>();
    private static final Map<Class<?>, List<Class<?>>> VALID_PROGRESSIONS = new HashMap<>();

    static {

        SHORT_NAME_TYPES.putAll(CanonicalPath.SHORT_NAME_TYPES);
        SHORT_NAME_TYPES.put("..", Up.class);

        SHORT_TYPE_NAMES.putAll(CanonicalPath.SHORT_TYPE_NAMES);
        SHORT_TYPE_NAMES.put(Up.class, "..");

        List<Class<?>> justUp = Collections.singletonList(Up.class);

        VALID_PROGRESSIONS.put(Tenant.class, Arrays.asList(Environment.class, MetricType.class, ResourceType.class,
                Up.class));
        VALID_PROGRESSIONS.put(Environment.class, Arrays.asList(Metric.class, Resource.class, Feed.class, Up.class));
        VALID_PROGRESSIONS.put(Feed.class, Arrays.asList(Metric.class, Resource.class, Up.class));
        VALID_PROGRESSIONS.put(Resource.class, Arrays.asList(Resource.class, Up.class));
        VALID_PROGRESSIONS.put(null, Arrays.asList(Tenant.class, Relationship.class, Up.class));
        VALID_PROGRESSIONS.put(Metric.class, justUp);
        VALID_PROGRESSIONS.put(ResourceType.class, justUp);
        VALID_PROGRESSIONS.put(MetricType.class, justUp);
        VALID_PROGRESSIONS.put(Up.class, Arrays.asList(Tenant.class, Environment.class, ResourceType.class,
                MetricType.class, Feed.class, Resource.class, Metric.class));
    }

    private RelativePath(int start, int end, List<Segment> segments) {
        super(start, end, segments, RelativePath::new);
    }

    @JsonCreator
    public static RelativePath fromString(String path) {
        return AbstractPath.fromString(path, VALID_PROGRESSIONS, SHORT_NAME_TYPES, (c) -> !Up.class.equals(c),
                RelativePath::new);
    }

    /**
     * @return an empty canonical path to be extended
     */
    public static Extender<RelativePath> empty() {
        return new Extender<>(0, new ArrayList<>(), VALID_PROGRESSIONS, RelativePath::new);
    }

    public static Builder to() {
        return new Builder(new ArrayList<>());
    }

    /**
     * Applies this relative path on the provided canonical path.
     *
     * @param path
     */
    public CanonicalPath applyTo(CanonicalPath path) {
        Extender<CanonicalPath> extender = new Extender<CanonicalPath>(0, new ArrayList<>(path.getPath()),
                VALID_PROGRESSIONS, CanonicalPath::new) {
            @Override
            public Extender<CanonicalPath> extend(Segment segment) {
                super.extend(segment);
                if (Up.class.equals(segment.getElementType())) {
                    segments.remove(segments.size() - 1);
                    segments.remove(segments.size() - 1);
                }

                return this;
            }
        };

        for (Segment s : getPath()) {
            extender.extend(s);
        }

        return extender.get();
    }

    @JsonValue
    @Override
    public String toString() {
        return new Encoder(SHORT_TYPE_NAMES).encode(this);
    }

    public static final class Up {
        private Up() {
        }
    }

    public static class Builder extends AbstractPath.Builder<RelativePath> {

        Builder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }

        public TenantBuilder tenant(String id) {
            segments.add(new Segment(Tenant.class, id));
            return new TenantBuilder(segments);
        }

        public RelationshipBuilder relationship(String id) {
            segments.add(new Segment(Relationship.class, id));
            return new RelationshipBuilder(segments);
        }

        public EnvironmentBuilder environment(String id) {
            segments.add(new Segment(Environment.class, id));
            return new EnvironmentBuilder(segments);
        }

        public ResourceTypeBuilder resourceType(String id) {
            segments.add(new Segment(ResourceType.class, id));
            return new ResourceTypeBuilder(segments);
        }

        public MetricTypeBuilder metricType(String id) {
            segments.add(new Segment(MetricType.class, id));
            return new MetricTypeBuilder(segments);
        }

        public FeedBuilder feed(String id) {
            segments.add(new Segment(Feed.class, id));
            return new FeedBuilder(segments);
        }

        public ResourceBuilder resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return new ResourceBuilder(segments);
        }

        public MetricBuilder metric(String id) {
            segments.add(new Segment(Metric.class, id));
            return new MetricBuilder(segments);
        }

        public UpBuilder up() {
            segments.add(new Segment(Up.class, null));
            return new UpBuilder(segments);
        }
    }

    public static class RelationshipBuilder extends AbstractPath.RelationshipBuilder<RelativePath> {
        RelationshipBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }
    }

    public static class TenantBuilder extends AbstractPath.TenantBuilder<RelativePath> {

        TenantBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }

        public EnvironmentBuilder environment(String id) {
            segments.add(new Segment(Environment.class, id));
            return new EnvironmentBuilder(segments);
        }

        public ResourceTypeBuilder resourceType(String id) {
            segments.add(new Segment(ResourceType.class, id));
            return new ResourceTypeBuilder(segments);
        }

        public MetricTypeBuilder metricType(String id) {
            segments.add(new Segment(MetricType.class, id));
            return new MetricTypeBuilder(segments);
        }

        public UpBuilder up() {
            segments.add(new Segment(Up.class, null));
            return new UpBuilder(segments);
        }
    }

    public static class ResourceTypeBuilder extends AbstractPath.ResourceTypeBuilder<RelativePath> {
        ResourceTypeBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }
    }

    public static class MetricTypeBuilder extends AbstractPath.MetricTypeBuilder<RelativePath> {
        MetricTypeBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }
    }

    public static class EnvironmentBuilder extends AbstractPath.EnvironmentBuilder<RelativePath> {
        EnvironmentBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }

        public FeedBuilder feed(String id) {
            segments.add(new Segment(Feed.class, id));
            return new FeedBuilder(segments);
        }

        public ResourceBuilder resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return new ResourceBuilder(segments);
        }

        public MetricBuilder metric(String id) {
            segments.add(new Segment(Metric.class, id));
            return new MetricBuilder(segments);
        }
    }

    public static class FeedBuilder extends AbstractPath.FeedBuilder<RelativePath> {

        FeedBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }

        public ResourceBuilder resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return new ResourceBuilder(segments);
        }

        public MetricBuilder metric(String id) {
            segments.add(new Segment(Metric.class, id));
            return new MetricBuilder(segments);
        }
    }

    public static class ResourceBuilder extends AbstractPath.ResourceBuilder<RelativePath> {

        ResourceBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }

        public ResourceBuilder resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return this;
        }
    }

    public static class MetricBuilder extends AbstractPath.MetricBuilder<RelativePath> {

        MetricBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }
    }

    public static class UpBuilder extends AbstractBuilder<RelativePath> {
        UpBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }

        public TenantBuilder tenant(String id) {
            segments.add(new Segment(Tenant.class, id));
            return new TenantBuilder(segments);
        }

        public EnvironmentBuilder environment(String id) {
            segments.add(new Segment(Environment.class, id));
            return new EnvironmentBuilder(segments);
        }

        public ResourceTypeBuilder resourceType(String id) {
            segments.add(new Segment(ResourceType.class, id));
            return new ResourceTypeBuilder(segments);
        }

        public MetricTypeBuilder metricType(String id) {
            segments.add(new Segment(MetricType.class, id));
            return new MetricTypeBuilder(segments);
        }

        public FeedBuilder feed(String id) {
            segments.add(new Segment(Feed.class, id));
            return new FeedBuilder(segments);
        }

        public ResourceBuilder resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return new ResourceBuilder(segments);
        }

        public MetricBuilder metric(String id) {
            segments.add(new Segment(Metric.class, id));
            return new MetricBuilder(segments);
        }

        public UpBuilder up() {
            segments.add(new Segment(Up.class, null));
            return this;
        }

        public RelativePath get() {
            return constructor.create(0, segments.size(), segments);
        }
    }
}
