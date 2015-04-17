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
package org.hawkular.inventory.api.model;

import java.util.Map;

/**
 * Base class for entities that are part of an environment.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public abstract class EnvironmentBasedEntity<B extends Entity.Blueprint, U extends AbstractElement.Update>
        extends TenantBasedEntity<B, U> {

    /**
     * JAXB support
     */
    EnvironmentBasedEntity() {
    }

    EnvironmentBasedEntity(CanonicalPath path) {
        this(path, null);
    }

    EnvironmentBasedEntity(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
        if (path.getRoot().down().getSegment().getElementType() != ElementType.ENVIRONMENT) {
            throw new IllegalArgumentException("An environment based entity should be contained in an environment.");
        }
    }

    public String getEnvironmentId() {
        return getPath().getRoot().down().getSegment().getElementId();
    }
}
