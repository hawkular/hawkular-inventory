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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Path;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Feeds {

    private Feeds() {

    }

    public enum ResourceParents implements Parents {
        FEED, RESOURCE
    }

    public enum MetricParents implements Parents {
        FEED, RESOURCE
    }

    private interface BrowserBase<AccessResources, AccessMetrics, MetricTypes, ResourceTypes> {
        AccessResources resources();

        Resources.Read resourcesUnder(ResourceParents... parents);

        AccessMetrics metrics();

        Metrics.Read metricsUnder(MetricParents... parents);

        MetricTypes metricTypes();

        ResourceTypes resourceTypes();
    }

    public interface Single extends ResolvableToSingleWithRelationships<Feed, Feed.Update>,
            BrowserBase<Resources.ReadWrite, Metrics.ReadWrite, MetricTypes.ReadWrite, ResourceTypes.ReadWrite> {}

    public interface Multiple extends ResolvableToManyWithRelationships<Feed>,
            BrowserBase<Resources.ReadContained, Metrics.ReadContained, MetricTypes.ReadContained,
            ResourceTypes.ReadContained> {}

    public interface ReadContained extends ReadInterface<Single, Multiple, String> {}

    public interface Read extends ReadInterface<Single, Multiple, Path> {}

    /**
     * Provides readonly access to feeds associated with environments, with the ability to modify the associations
     * between the two.
     */
    public interface ReadAssociate extends Read, AssociationInterface {
    }

    public interface ReadWrite extends ReadWriteInterface<Feed.Update, Feed.Blueprint, Single, Multiple, String> {

        /**
         * Registers a new feed.
         * The id in the blueprint is merely a suggestion and does not need to be honored by the server. The caller is
         * advised to use the returned access interface to check what the actual ID was assigned to the feed.
         *
         * @param blueprint the blueprint of the feed
         * @return the access interface to the newly created feed
         */
        Single create(Feed.Blueprint blueprint);
    }
}
