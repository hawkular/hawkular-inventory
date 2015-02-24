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

import java.util.Set;

/**
 * Base interface for all browser interfaces over multiple entities.
 *
 * @param <Entity> the type of the entity being browsed
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface ResolvableToMany<Entity> {

    /**
     * @return resolves all entities on the current position in the inventory traversal and returns them as a set.
     */
    Set<Entity> entities();
}
