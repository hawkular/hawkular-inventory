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
package org.hawkular.inventory.api.filters;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.paths.CanonicalPath;

/**
 * A helper class to create filters on the "incorporates" relationship.  This can also be achieved by using the
 * {@link Related} filter.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class Incorporated extends Related {

    public static final Incorporated entities = new Incorporated(null);

    private Incorporated(CanonicalPath entity) {
        super(entity, Relationships.WellKnown.incorporates.name(), EntityRole.TARGET);
    }

    public static Incorporated by(CanonicalPath entity) {
        return new Incorporated(entity);
    }
}
