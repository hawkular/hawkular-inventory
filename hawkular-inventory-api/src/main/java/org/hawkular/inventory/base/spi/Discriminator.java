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
package org.hawkular.inventory.base.spi;

import java.time.Instant;

/**
 * A discriminator is basically a global filter on any request to an inventory backend.
 *
 * @author Lukas Krejci
 * @since 0.19.0
 */
public final class Discriminator {
    private final Instant time;
    private final boolean preferExistence;

    public static Discriminator time(Instant time) {
        return new Discriminator(time, true);
    }

    public static Discriminator timeExcludingMillisecondDeletes(Instant time) {
        return new Discriminator(time, false);
    }

    private Discriminator(Instant time, boolean preferExistence) {
        this.time = time;
        this.preferExistence = preferExistence;
    }

    public Instant getTime() {
        return time;
    }

    /**
     * Depending on situation, once may want to include states that were created and ended within a millisecond in some
     * sort of results or not.
     *
     * @return whether to include sub-millisecond changes in results (true) or not (false).
     */
    public boolean isPreferExistence() {
        return preferExistence;
    }

    public Discriminator excludeDeletedInMillisecond() {
        return new Discriminator(time, false);
    }
}
