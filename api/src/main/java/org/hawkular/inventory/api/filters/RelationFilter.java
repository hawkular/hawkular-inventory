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
package org.hawkular.inventory.api.filters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for filters that filter relationships.
 *
 * <p>The implementations of the Hawkular inventory API are supposed to support filtering relationships by
 * {@link org.hawkular.inventory.api.filters.RelationWith.Properties},
 * {@link org.hawkular.inventory.api.filters.RelationWith.Direction},
 * {@link org.hawkular.inventory.api.filters.RelationWith.Ids} and
 * {@link org.hawkular.inventory.api.filters.RelationWith.EntityTypes}.
 *
 * @author Jirka Kremser
 * @since 1.0
 */
public class RelationFilter extends Filter {
    private static final RelationFilter[] EMPTY = new RelationFilter[0];

    RelationFilter() {

    }

    public static Accumulator by(RelationFilter... filters) {
        return new Accumulator(filters);
    }

    public static RelationFilter[] all() {
        return EMPTY;
    }
//
//    public static RelationFilter[] pathTo(Entity entity) {
//        return entity.accept(new EntityVisitor<Accumulator, Accumulator>() {
//            @Override
//            public Accumulator visitTenant(Tenant tenant, Accumulator acc) {
//
//                return acc.and(With.type(Tenant.class)).and(With.id(tenant.getId()));
//            }
//
//            @Override
//            public Accumulator visitEnvironment(Environment environment, Accumulator acc) {
//                return acc.and(With.type(Tenant.class)).and(With.id(environment.getTenantId()))
//                        .and(With.type(Environment.class)).and(With.id(environment.getId()));
//            }
//
//            @Override
//            public Accumulator visitFeed(Feed feed, Accumulator acc) {
//                return acc.and(With.type(Tenant.class)).and(With.id(feed.getTenantId()))
//                        .and(With.type(Environment.class)).and(With.id(feed.getEnvironmentId()))
//                        .and(With.type(Feed.class)).and(With.id(feed.getId()));
//            }
//
//            @Override
//            public Accumulator visitMetric(Metric metric, Accumulator acc) {
//                return acc.and(With.type(Tenant.class)).and(With.id(metric.getTenantId()))
//                        .and(With.type(Environment.class)).and(With.id(metric.getEnvironmentId()))
//                        .and(With.type(Metric.class)).and(With.id(metric.getId()));
//            }
//
//            @Override
//            public Accumulator visitMetricType(MetricType type, Accumulator acc) {
//                return acc.and(With.type(Tenant.class)).and(With.id(type.getTenantId()))
//                        .and(With.type(MetricType.class)).and(With.id(type.getId()));
//            }
//
//            @Override
//            public Accumulator visitResource(Resource resource, Accumulator acc) {
//                return acc.and(With.type(Tenant.class)).and(With.id(resource.getTenantId()))
//                        .and(With.type(Environment.class)).and(With.id(resource.getEnvironmentId()))
//                        .and(With.type(Resource.class)).and(With.id(resource.getId()));
//            }
//
//            @Override
//            public Accumulator visitResourceType(ResourceType type, Accumulator acc) {
//                return acc.and(With.type(Tenant.class)).and(With.id(type.getTenantId()))
//                        .and(With.type(ResourceType.class)).and(With.id(type.getId()));
//            }
//        }, by()).get();
//    }

    public static final class Accumulator {
        private final List<RelationFilter> filters = new ArrayList<>();

        private Accumulator(RelationFilter... fs) {
            for (RelationFilter filter : fs) {
                filters.add(filter);
            }
        }

        public Accumulator and(RelationFilter f) {
            filters.add(f);
            return this;
        }

        public Accumulator and(RelationFilter... fs) {
            Collections.addAll(filters, fs);
            return this;
        }

        public RelationFilter[] get() {
            return filters.toArray(new RelationFilter[filters.size()]);
        }
    }
}
