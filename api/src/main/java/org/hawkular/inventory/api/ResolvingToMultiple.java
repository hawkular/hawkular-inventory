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

import org.hawkular.inventory.api.filters.Filter;

/**
 * This is the most generic interface for positions in the graph traversal from which one can resolve a many entities
 * using some filters. Note that a position might not allow for {@link ResolvingToSingle} if the same type of the entity
 * the position represents can be resolved in multiple ways - for an example, see how
 * {@link Environments.Multiple#allResources()} is defined.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface ResolvingToMultiple<Multiple> {

    /**
     * Returns access interface to all entities conforming to provided filters in the current position in the inventory
     * traversal.
     *
     * @param filters the (possibly empty) list of filters to apply.
     * @return the (read-only) access interface to the found entities
     */
    default Multiple getAll(Filter... filters) {
        Filter[][] fs;
        if (filters.length == 0) {
            fs = new Filter[0][0];
        } else {
            fs = new Filter[1][];
            fs[0] = filters;
        }
        return getAll(fs);
    }

    /**
     * Returns access interface to all entities conforming to the provided filters in the current position in the
     * inventory traversal.
     *
     * <p>The filters parameter is actually composed of individual sets of filters each of which is applied to
     * the "source" entities. Each set can have multiple filters that are then applied one after another. This becomes
     * useful when you want to filter by sources having some kind of relationships with other entities and in addition
     * filter on the sources themselves, too.
     * E.g.:
     * <pre>{@code
     * inventory.tenants().getAll(new Filter[][]{{Related.by("myRel"), With.type(ResourceType.class)},
     * {With.id("asf")}}).entities();
     * }</pre>
     *
     * The filter above would first check that the tenants in question have a relationship called "myRel" with some
     * resource types and then would also check if the tenant has ID "asf".
     *
     * @param filters the sets of filters to apply
     * @return the access to found entities
     */
    Multiple getAll(Filter[][] filters);
}
