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

    public static Discriminator time(Instant time) {
        return new Discriminator(time);
    }

    private Discriminator(Instant time) {
        this.time = time;
    }

    public Instant getTime() {
        return time;
    }
}
