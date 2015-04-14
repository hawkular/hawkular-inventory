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

import org.hawkular.inventory.api.model.ResourceType;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on resource types.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ResourceTypes {

    private ResourceTypes() {

    }

    private interface BrowserBase<Resources, MetricTypes> {
        /**
         * @return access to resources defined by the resource type(s)
         */
        Resources resources();

        /**
         * @return access to metrics owned by the resource type(s)
         */
        MetricTypes metricTypes();
    }

    /**
     * Interface for accessing a single resource type in a writable manner.
     */
    public interface Single extends ResolvableToSingleWithRelationships<ResourceType>,
            BrowserBase<Resources.Read, MetricTypes.ReadAssociate> {}

    /**
     * Interface for traversing over a set of resource types.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(String)} method).
     */
    public interface Multiple extends ResolvableToManyWithRelationships<ResourceType>,
            BrowserBase<Resources.Read, MetricTypes.Read> {}

    /**
     * Provides read-only access to resource types.
     */
    public interface Read extends ReadInterface<Single, Multiple> {}

    /**
     * Provides read-write access to resource types.
     */
    public interface ReadWrite
            extends ReadWriteInterface<ResourceType.Update, ResourceType.Blueprint, Single, Multiple> {}
}
