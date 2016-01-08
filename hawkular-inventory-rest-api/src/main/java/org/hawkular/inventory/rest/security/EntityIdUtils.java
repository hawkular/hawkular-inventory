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
package org.hawkular.inventory.rest.security;

import java.util.UUID;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

/**
 * @author Jirka Kremser
 * @since 0.3.4
 */
public class EntityIdUtils {

    public static String getStableId(CanonicalPath path) {
        return shortenIfNeeded(path);
    }

    public static String getStableId(AbstractElement<?, ?> element) {
        return getStableId(element.getPath());
    }

    public static CanonicalPath toCanonicalPath(String restPath) {
        String[] chunks = restPath.split("(?<=[^\\\\])/");
        CanonicalPath.Extender path = CanonicalPath.empty();
        if (chunks.length == 2) {
            if ("tenants".equals(chunks[0])) {
                path.extend(Tenant.class, chunks[1]);
            } else if ("relationships".equals(chunks[0])) {
                path.extend(Relationship.class, chunks[1]);
            }
        } else if (chunks.length == 3) {
            if ("environments".equals(chunks[1])) {
                path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[2]);
            } else if ("resourceTypes".equals(chunks[1])) {
                path.extend(Tenant.class, chunks[0]).extend(ResourceType.class, chunks[2]);
            } else if ("metricTypes".equals(chunks[1])) {
                path.extend(Tenant.class, chunks[0]).extend(MetricType.class, chunks[2]);
            } else if ("feeds".equals(chunks[1])) {
                path.extend(Tenant.class, chunks[0]).extend(Feed.class, chunks[2]);
            }
        } else if (chunks.length == 4 && "resources".equals(chunks[2])) {
            path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[1]).extend(Resource.class,
                    chunks[3]);
        } else if (chunks.length == 4 && "metrics".equals(chunks[2])) {
            path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[1]).extend(Metric.class,
                    chunks[3]);
        } else if (chunks.length == 5 && "resources".equals(chunks[3])) {
            path.extend(Tenant.class, chunks[0]).extend(Feed.class, chunks[2]).extend(Resource.class, chunks[4]);
        } else if (chunks.length == 5 && "metrics".equals(chunks[3])) {
            path.extend(Tenant.class, chunks[0]).extend(Feed.class, chunks[2]).extend(Metric.class, chunks[4]);
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

    private static String shortenIfNeeded(CanonicalPath path) {
        String id = path.toString();

        if (id.length() > 250) {
            return UUID.nameUUIDFromBytes(id.getBytes()).toString();
        } else {
            return id;
        }
    }
}
