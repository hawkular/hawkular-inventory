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

import org.hawkular.inventory.api.model.Environment;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on environments.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Environments {

    private Environments() {

    }

    private interface BrowserBase<Feeds, Resources, Metrics> {
        /**
         * @return feeds in the environment(s)
         */
        Feeds feeds();

        /**
         * @return resources in the environment(s)
         */
        Resources resources();

        /**
         * @return metrics in the environment(s)
         */
        Metrics metrics();
    }

    /**
     * Interface for accessing a single environment in a writable manner.
     */
    public interface Single extends SingleRelatableEntityBrowser<Environment>,
            BrowserBase<Feeds.ReadAndRegister, Resources.ReadWrite, Metrics.ReadWrite> {}

    /**
     * Interface for traversing over a set of environments.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(String)} method).
     */
    public interface Multiple extends MultipleRelatableEntityBrowser<Environment>,
            BrowserBase<Feeds.Read, Resources.Read, Metrics.Read> {}

    /**
     * Provides read-only access to environments.
     */
    public interface Read extends ReadInterface<Single, Multiple> {}

    /**
     * Provides methods for read-write access to environments.
     */
    public interface ReadWrite extends ReadWriteInterface<Environment, String, Single, Multiple> {
        void copy(String sourceEnvironmentId, String targetEnvironmentId);
    }

    public interface ReadRelate extends ReadInterface<Single, Multiple>, RelateInterface {}
}
