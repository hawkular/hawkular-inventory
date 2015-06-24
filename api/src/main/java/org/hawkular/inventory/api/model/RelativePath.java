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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
public final class RelativePath extends Path implements Serializable {

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
        super(start, end, segments);
    }

    @JsonCreator
    public static RelativePath fromString(String path) {
        return (RelativePath) Path.fromString(path, VALID_PROGRESSIONS, SHORT_NAME_TYPES,
                (c) -> !Up.class.equals(c), Extender::new, false);
    }

    @Override
    protected Path newInstance(int startIdx, int endIdx, List<Segment> segments) {
        return new RelativePath(startIdx, endIdx, segments);
    }

    /**
     * @return an empty canonical path to be extended
     */
    public static Extender empty() {
        return new Extender(0, new ArrayList<>(), VALID_PROGRESSIONS);
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
        CanonicalPath.Extender extender = new CanonicalPath.Extender(0, new ArrayList<>(path.getPath()),
                VALID_PROGRESSIONS) {
            @Override
            public CanonicalPath.Extender extend(Segment segment) {
                super.extend(segment);
                if (Up.class.equals(segment.getElementType())) {
                    segments.remove(segments.size() - 1);
                    segments.remove(segments.size() - 1);
                }

                return this;
            }
        };

        getPath().forEach(extender::extend);

        return extender.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<RelativePath> ascendingIterator() {
        return (Iterator<RelativePath>) super.ascendingIterator();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<RelativePath> descendingIterator() {
        return (Iterator<RelativePath>) super.descendingIterator();
    }

    @Override
    public RelativePath down() {
        return (RelativePath) super.down();
    }

    @Override
    public RelativePath down(int distance) {
        return (RelativePath) super.down(distance);
    }

    @Override
    public RelativePath up() {
        return (RelativePath) super.up();
    }

    @Override
    public RelativePath up(int distance) {
        return (RelativePath) super.up(distance);
    }

    @JsonValue
    @Override
    public String toString() {
        return new Encoder(SHORT_TYPE_NAMES).encode("", this);
    }

    public static final class Up {
        private Up() {
        }
    }

    public static class Builder extends Path.Builder<RelativePath> {

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

    public static class RelationshipBuilder extends Path.RelationshipBuilder<RelativePath> {
        RelationshipBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }
    }

    public static class TenantBuilder extends Path.TenantBuilder<RelativePath> {

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

    public static class ResourceTypeBuilder extends Path.ResourceTypeBuilder<RelativePath> {
        ResourceTypeBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }
    }

    public static class MetricTypeBuilder extends Path.MetricTypeBuilder<RelativePath> {
        MetricTypeBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }
    }

    public static class EnvironmentBuilder extends Path.EnvironmentBuilder<RelativePath> {
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

    public static class FeedBuilder extends Path.FeedBuilder<RelativePath> {

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

    public static class ResourceBuilder extends Path.ResourceBuilder<RelativePath> {

        ResourceBuilder(List<Segment> segments) {
            super(segments, RelativePath::new);
        }

        public ResourceBuilder resource(String id) {
            segments.add(new Segment(Resource.class, id));
            return this;
        }
    }

    public static class MetricBuilder extends Path.MetricBuilder<RelativePath> {

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

    public static class Extender extends Path.Extender {

        Extender(int from, List<Segment> segments, Map<Class<?>, List<Class<?>>> validProgressions) {
            super(from, segments, validProgressions);
        }

        @Override
        protected RelativePath newPath(int startIdx, int endIdx, List<Segment> segments) {
            return new RelativePath(startIdx, endIdx, segments);
        }

        @Override
        public Extender extend(Segment segment) {
            return (Extender) super.extend(segment);
        }

        @Override
        public Extender extend(Class<? extends AbstractElement<?, ?>> type, String id) {
            return (Extender) super.extend(type, id);
        }

        @Override
        public RelativePath get() {
            return (RelativePath) super.get();
        }
    }
}
