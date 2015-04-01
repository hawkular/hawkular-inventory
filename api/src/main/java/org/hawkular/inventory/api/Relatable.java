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

/**
 * The contract for resolvable interfaces ({@link ResolvableToMany} and {@link ResolvableToMany}) that also support
 * relationships.
 *
 * @param <Access> the access interface to the relationships (this should be one of
 * {@link org.hawkular.inventory.api.Relationships.Read} or {@link org.hawkular.inventory.api.Relationships.ReadWrite}).
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface Relatable<Access> {
    /**
     * @return the (r/w) access interface to all (outgoing) relationships of the entities on the current position in
     * the inventory traversal.
     */
    Access relationships();

    /**
     * @param direction the direction of the relation (aka edge) This is needed because relationships are not
     *                  bidirectional.
     * @return the (r/w) access interface to all relationships of the entities on the current position in
     * the inventory traversal.
     */
    Access relationships(Relationships.Direction direction);
}
