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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.paths.Path;

/**
 * An interface providing methods to add a pre-existing entity into relation with the single entity in the current
 * position on the inventory traversal.
 *
 * <p>Note that this interface should never be inherited by the actual {@code Single} interface together with the
 * {@link WriteInterface}. Having both {@code create} and {@code associate} or {@code disassociate} and {@code delete}
 * methods would be semantically incorrect (in addition to being confusing to the API consumer).
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
interface AssociationInterface {

    /**
     * Adds a pre-existing entity into the relation with the current entity.
     *
     * The current entity is determined by the current position in the inventory traversal as is the type of the entity
     * being related to it. Consider this example:
     *
     * <pre><code>
     * inventory.tenants().get(tenantId).environments().get(envId).resources().get(rId).metrics().associate(
     * RelativePath.to().up().metric(metricId));
     * </code></pre>
     *
     * <p>In here the {@code associate} method will add a new metric (identified by the {@code metricId}) to the
     * resource identified by the {@code rId}.
     *
     * <p>The provided path can be a {@link org.hawkular.inventory.paths.RelativePath} (as in the above example)
     * which is evaluated against the current position in the graph - the
     * {@link org.hawkular.inventory.paths.RelativePath#up() up()} call will move up the path from the resource
     * to the environment and the {@code metric()} call will the move to a metric in that environment.
     *
     * <p>The provided path can also be a {@link org.hawkular.inventory.paths.CanonicalPath} in which case it is
     * fully evaluated and the entity on that path is associated (provided the path points to the correct type of
     * entity and all the rules specific for the association of the two types of entities are fulfilled).
     *
     * @param path the path to a pre-existing entity to be related to the entity on the current position in the
     *             inventory traversal.
     * @return the relationship that was created as the consequence of the association.
     */
    Relationship associate(Path path) throws EntityNotFoundException, RelationAlreadyExistsException;

    /**
     * Associates a number of paths with the entity on the current position in the traversal.
     * <p>
     * The default implementation merely calls {@link #associate(Path)} for each of the paths.
     *
     * @param paths the paths to the entities to associate
     * @return the list of the relationships in the iteration order of the supplied paths
     * @see #associate(Path)
     */
    default List<Relationship> associate(Iterable<Path> paths) {
        return StreamSupport.stream(paths.spliterator(), false).map(this::associate).collect(toList());
    }

    /**
     * Removes an entity from the relation with the current entity.
     *
     * The current entity and the type of the entity to remove from the relation is determined by the current position
     * in the inventory traversal.
     *
     * @see #associate(Path) for explanation of how the current entity and the relation is determined.
     *
     * @param path the id of the entity to remove from the relation with the current entity.
     *
     * @return the relationship that was deleted as a result of the disassociation
     */
    Relationship disassociate(Path path) throws EntityNotFoundException;

    /**
     * Similar to {@link #associate(Iterable)}, this method disassociates the provided paths.
     * <p>
     * The default implementation merely calls {@link #disassociate(Path)} for each of the provided paths.
     *
     * @param paths the paths to disassociate
     * @return the list of the removed relationships in the iteration order of the paths
     * @see #disassociate(Path)
     */
    default List<Relationship> disassociate(Iterable<Path> paths) {
        return StreamSupport.stream(paths.spliterator(), false).map(this::disassociate).collect(toList());
    }

    /**
     * Finds the relationship with the entity with the provided id.
     *
     * @see #associate(Path) for the discussion of how the entity and the relationship is determined
     *
     * @param path the id of the entity to find the relation with
     *
     * @return the relationship found
     *
     * @throws RelationNotFoundException if a relation with an entity of given id doesn't exist
     */
    Relationship associationWith(Path path) throws RelationNotFoundException;
}
