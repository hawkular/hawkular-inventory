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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

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
     * <b>IMPORTANT:</b> The returned page object MUST be {@link Page#close() close()}'d once it's done with (the
     * results are iterated or no longer needed).
     *
     * @param pager the pager object describing the subset of the entities to return
     * @return resolves all entities on the current position in the inventory traversal and produces a single "page"
     * of those entities according to the provided pager
     */
    Page<Entity> entities(Pager pager);

    /**
     * @return all the entities on the current position in the traversal and returns them as a set.
     */
    default Set<Entity> entities() {
        try (Page<Entity> it = entities(Pager.unlimited(Order.unspecified()))) {
            Iterable<Entity> iterable = () -> it;
            return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toSet());
        }
    }

    /**
     * @return true if there is at least 1 entity on the current position in the inventory traversal
     */
    default boolean anyExists() {
        try (Page<?> p = entities(Pager.single())) {
            return p.hasNext();
        }
    }
}
