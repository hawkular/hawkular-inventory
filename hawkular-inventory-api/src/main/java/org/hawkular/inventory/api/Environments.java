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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.paths.Path;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on environments.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Environments {

    private Environments() {

    }

    public enum ResourceParents implements Parents {
        ENVIRONMENT, FEED, RESOURCE
    }

    public enum MetricParents implements Parents {
        ENVIRONMENT, FEED, RESOURCE
    }

    /**
     * An interface implemented by Single/Multiple interfaces of entities that can contain environments.
     * @param <Access> the type of access to environments
     */
    public interface Container<Access> {
        Access environments();
    }

    private interface BrowserBase<F, R, M> extends Feeds.Container<F>, Resources.Container<R>, Metrics.Container<M> {
        /**
         * Returns access to all resources in this environment regardless of whether they are under some feed or
         * directly under the environment.
         *
         * @return the access interface to all resources in this environment
         */
        Resources.Read resourcesUnder(ResourceParents... parents);

        /**
         * Returns access to all metrics in this environment regardless of whether they are under some feed or
         * directly under the environment.
         *
         * <p>Note that it is not possible to get a single metric from this access interface because the id is not
         * guaranteed to be unique amongst metrics under different feeds.
         *
         * @return the access interface to all metrics in this environment
         */
        Metrics.Read metricsUnder(MetricParents... parents);
    }

    /**
     * Interface for accessing a single environment in a writable manner.
     */
    public interface Single extends ResolvableToSingleWithRelationships<Environment, Environment.Update>,
            BrowserBase<Feeds.ReadAssociate, Resources.ReadWrite, Metrics.ReadWrite> {
    }

    /**
     * Interface for traversing over a set of environments.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(Object)} method).
     */
    public interface Multiple extends ResolvableToManyWithRelationships<Environment>,
            BrowserBase<Feeds.Read, Resources.ReadContained, Metrics.ReadContained> {
    }

    /**
     * Provides read-only access to environments.
     */
    public interface ReadContained extends ReadInterface<Single, Multiple, String> {}

    public interface Read extends ReadInterface<Single, Multiple, Path> {}

    /**
     * Provides methods for read-write access to environments.
     */
    public interface ReadWrite
            extends ReadWriteInterface<Environment.Update, Environment.Blueprint, Single, Multiple, String> {
        void copy(String sourceEnvironmentId, String targetEnvironmentId);
    }
}
