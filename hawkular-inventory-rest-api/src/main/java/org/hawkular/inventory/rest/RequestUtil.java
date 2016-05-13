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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.PageContext;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class RequestUtil {

    private RequestUtil() {
    }

    public static Pager extractPaging(UriInfo uri) {
        MultivaluedMap<String, String> params = uri.getQueryParameters();

        String pageS = params.getFirst("page");
        String perPageS = params.getFirst("per_page");
        List<String> sort = params.get("sort");
        List<String> order = params.get("order");

        int page = pageS == null ? 0 : Integer.parseInt(pageS);
        int perPage = perPageS == null ? PageContext.UNLIMITED_PAGE_SIZE : Integer.parseInt(perPageS);

        List<Order> ordering = new ArrayList<>();

        if (sort == null || sort.isEmpty()) {
            ordering.add(Order.unspecified());
        } else {
            for (int i = 0; i < sort.size(); ++i) {
                String field = sort.get(i);
                Order.Direction dir = Order.Direction.ASCENDING;
                if (order != null && i < order.size()) {
                    dir = Order.Direction.fromShortString(order.get(i));
                }

                ordering.add(Order.by(field, dir));
            }
        }

        return new Pager(page, perPage, ordering);
    }

    public static CanonicalPath toCanonicalPath(String restPath) {
        String[] chunks = restPath.split("(?<=[^\\\\])/");
        CanonicalPath.Extender path = CanonicalPath.empty();
        if (chunks.length == 2) {
            if ("tenants".equals(chunks[0])) {
                path.extend(Tenant.SEGMENT_TYPE, chunks[1]);
            } else if ("relationships".equals(chunks[0])) {
                path.extend(Relationship.SEGMENT_TYPE, chunks[1]);
            }
        } else if (chunks.length == 3) {
            if ("environments".equals(chunks[1])) {
                path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(Environment.SEGMENT_TYPE, chunks[2]);
            } else if ("resourceTypes".equals(chunks[1])) {
                path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(ResourceType.SEGMENT_TYPE, chunks[2]);
            } else if ("metricTypes".equals(chunks[1])) {
                path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(MetricType.SEGMENT_TYPE, chunks[2]);
            } else if ("feeds".equals(chunks[1])) {
                path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(Feed.SEGMENT_TYPE, chunks[2]);
            }
        } else if (chunks.length == 4 && "resources".equals(chunks[2])) {
            path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(Environment.SEGMENT_TYPE, chunks[1]).extend(
                    Resource.SEGMENT_TYPE,
                    chunks[3]);
        } else if (chunks.length == 4 && "metrics".equals(chunks[2])) {
            path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(Environment.SEGMENT_TYPE, chunks[1]).extend(
                    Metric.SEGMENT_TYPE,
                    chunks[3]);
        } else if (chunks.length == 5 && "resources".equals(chunks[3])) {
            path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(Feed.SEGMENT_TYPE, chunks[2]).extend(
                    Resource.SEGMENT_TYPE,
                    chunks[4]);
        } else if (chunks.length == 5 && "metrics".equals(chunks[3])) {
            path.extend(Tenant.SEGMENT_TYPE, chunks[0]).extend(Feed.SEGMENT_TYPE, chunks[2]).extend(
                    Metric.SEGMENT_TYPE,
                    chunks[4]);
        }
        return path.get();
    }

    public static boolean isTenantEscapeAttempt(CanonicalPath origin, Path extension) {
        if (extension instanceof CanonicalPath) {
            return !((CanonicalPath) extension).ids().getTenantId().equals(origin.ids().getTenantId());
        } else {
            CanonicalPath target = ((RelativePath) extension).applyTo(origin);
            return !target.ids().getTenantId().equals(origin.ids().getTenantId());
        }
    }

}
