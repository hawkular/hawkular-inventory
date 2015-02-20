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
package org.hawkular.inventory.api.filters;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.ResourceType;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Defined<T extends Entity> extends Related<T> {

    private Defined(T entity) {
        super(entity, Relationships.WellKnown.defines.name(), Related.Direction.IN);
    }

    public static Defined<MetricType> by(MetricType definition) {
        return new Defined<>(definition);
    }

    public static Defined<ResourceType> by(ResourceType type) {
        return new Defined<>(type);
    }
}
