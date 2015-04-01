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

import org.hawkular.inventory.api.model.Resource;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on resources.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Resources {

    private Resources() {}

    private interface BrowserBase<Metrics> {

        /**
         * @return access to metrics owned by the resource(s)
         */
        Metrics metrics();
    }

    /**
     * Interface for accessing a single resource in a writable manner.
     */
    public interface Single extends ResolvableToSingleWithRelationships<Resource>, BrowserBase<Metrics.ReadAssociate> {}

    /**
     * Interface for traversing over a set of resources.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(String)} method).
     */
    public interface Multiple extends ResolvableToManyWithRelationships<Resource>, BrowserBase<Metrics.Read> {}

    /**
     * Provides read-only access to resources.
     */
    public interface Read extends ReadInterface<Single, Multiple> {}

    /**
     * Provides read-write access to resources.
     */
    public interface ReadWrite extends ReadWriteInterface<Resource, Resource.Blueprint, Single, Multiple> {}
}
