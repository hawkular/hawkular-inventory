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
package org.hawkular.inventory.api.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.hawkular.inventory.paths.SegmentType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration of the inventory synchronization.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
public final class SyncConfiguration implements Serializable {
    public static final SyncConfiguration DEFAULT = builder().withAllTypes().withDeepSearch(false).build();

    private final EnumSet<SegmentType> syncedTypes;
    private final boolean deepSearch;

    public static Builder builder() {
        return new Builder();
    }

    @JsonCreator
    public SyncConfiguration(@JsonProperty("syncedTypes") EnumSet<SegmentType> syncedTypes,
                             @JsonProperty(value = "deepSearch", defaultValue = "false") boolean deepSearch) {
        this.syncedTypes = syncedTypes;
        this.deepSearch = deepSearch;
    }

    /**
     * When syncing entities under some sync root, the user can restrict what types of entities
     * will be synced. I.e. you can issue a sync of a feed but restrict the sync only to resource types. This will in
     * effect make sure that the feed only defines the resource types you instruct it to but all the metric types,
     * resource, metrics, etc. that exist under the feed but were not mentioned in the provided inventory structure
     * will be left intact.
     *
     * @return the set of entity types to be considered during synchronization
     */
    public Set<SegmentType> getSyncedTypes() {
        return syncedTypes;
    }

    /**
     * When for example the sync is limited to metrics, this property dictates whether to sync metrics that are
     * contained in some (grand)children of the sync root (true) or just directly under the sync root or any other
     * explicitly mentioned parent (which itself is a child of the sync root).
     *
     * <p>As an example, consider these two scenarios. In each case, we have the inventory that looks like this:
     * <pre>{@code
     * feed
     * + resource1
     * | - metric1
     * | - metric2
     * + resource2
     * | - metric3
     * | - metric4
     * + metric5
     * + metric6
     * }</pre>
     * I.e. {@code metric1} and {@code metric2} are contained within {@code resource1}, {@code resource2}
     * contains {@code metric3} and {@code metric4}, while {@code metric5} and {@code metric6 }are contained directly
     * under the feed.
     *
     * <p>In each case, we send the inventory structure that looks like this:
     * <pre>{@code
     * feed
     * + resource1
     * | - metric1
     * + metric5
     * }</pre>
     *
     * <p>When {@code deepSearch} is {@code false} (which is the default, when constructing the configuration using the
     * {@link Builder}), the inventory will look like this after the sync:
     * <pre>{@code
     * feed
     * + resource1
     * | - metric1
     * + resource2
     * | - metric3
     * | - metric4
     * + metric5
     * }</pre>
     * The {@code metric2} is removed from under {@code resource1} after the sync because it's not mentioned as a direct
     * child of {@code resource1} which is present in the synced inventory structure. {@code resource2} retains both
     * of its child metrics, because {@code resource2} is not present in the synced inventory structure and is also
     * not configured to be synced. {@code metric6} is removed for the same reason as {@code metric2} - it is not
     * mentioned under its immediate parent.
     *
     * <p>On the other hand, if {@code deepSearch} is {@code true}, the inventory will look like this after the sync:
     * <pre>{@code
     * feed
     * + resource1
     * | - metric1
     * + resource2
     * + metric5
     * }</pre>
     * All the {@code metric2} and {@code metric3}, {@code metric4} and {@code metric6 }will be removed from inventory
     * because none of them was specified in the synced structure.
     *
     * @return whether to perform deep search when looking for entities of the synced types or not
     */
    public boolean isDeepSearch() {
        return deepSearch;
    }

    public static final class Builder {
        private final EnumSet<SegmentType> syncedTypes = EnumSet.noneOf(SegmentType.class);
        private boolean deepSearch = false;

        private Builder() {

        }

        public Builder withType(SegmentType segmentType) {
            syncedTypes.add(segmentType);
            return this;
        }

        public Builder withTypes(SegmentType... segmentTypes) {
            return withTypes(Arrays.asList(segmentTypes));
        }

        public Builder withTypes(Iterable<SegmentType> segmentTypes) {
            segmentTypes.forEach(syncedTypes::add);
            return this;
        }

        public Builder withAllTypes() {
            return withTypes(SegmentType.values());
        }

        public Builder withoutType(SegmentType segmentType) {
            syncedTypes.remove(segmentType);
            return this;
        }

        public Builder withoutTypes(SegmentType... segmentTypes) {
            return withoutTypes(Arrays.asList(segmentTypes));
        }

        public Builder withoutTypes(Iterable<SegmentType> segmentTypes) {
            segmentTypes.forEach(syncedTypes::remove);
            return this;
        }

        public Builder withDeepSearch(boolean value) {
            this.deepSearch = value;
            return this;
        }

        public SyncConfiguration build()  {
            return new SyncConfiguration(syncedTypes, deepSearch);
        }
    }
}
