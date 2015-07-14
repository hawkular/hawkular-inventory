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

import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on resources.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Resources {

    private Resources() {
    }

    private interface BrowserBase<Metrics, ContainedAccess, AllAccess> {

        /**
         * @return access to metrics owned by the resource(s)
         */
        Metrics metrics();

        /**
         * @return access to children that are existentially bound to this/these resource(s)
         */
        ContainedAccess containedChildren();

        /**
         * Access to all children.
         *
         * Note that children that are existentially bound to this resource (i.e. in addition to
         * {@link org.hawkular.inventory.api.Relationships.WellKnown#isParentOf} there also exists the
         * {@link org.hawkular.inventory.api.Relationships.WellKnown#contains} relationship) cannot be disassociated
         * using this interface.
         *
         * @return access to all children of this/these resource(s) (superset of {@link #containedChildren()}, also
         * includes the resources bound merely by
         * {@link org.hawkular.inventory.api.Relationships.WellKnown#isParentOf}).
         */
        AllAccess allChildren();

        /**
         * @return the parent resource(s) of the current resource(s)
         */
        Read parents();
    }

    /**
     * Interface for accessing a single resource in a writable manner.
     */
    public interface Single extends ResolvableToSingleWithRelationships<Resource, Resource.Update>,
            BrowserBase<Metrics.ReadAssociate, ReadWrite, ReadAssociate> {

        /**
         * @return access to the parent resource (if any) that contains the resource on the current position in the
         * path traversal. This resource will not exist for top-level resources living directly under an environment or
         * feed.
         */
        Single parent();
    }

    /**
     * Interface for traversing over a set of resources.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(Object)} method).
     */
    public interface Multiple
            extends ResolvableToManyWithRelationships<Resource>, BrowserBase<Metrics.Read, ReadContained, Read> {}

    public interface ReadBase<Address> extends ReadInterface<Single, Multiple, Address> {

        /**
         * A shortcut for {@code get(firstChild).allChildren().get(furtherChildren[0]).allChildren()
         * .get(furtherChildren[1])...}.
         *
         * <p>Each of the paths in further children is either a canonical path or a relative path that is relative
         * to the preceding child.
         *
         * <p>Remember that relative paths are resolved using the
         * <b>{@link org.hawkular.inventory.api.Relationships.WellKnown#contains}</b> relationship while descend follows
         * the {@link org.hawkular.inventory.api.Relationships.WellKnown#isParentOf} relationships.
         *
         * @param firstChild      the id of the first contained child
         * @param furtherChildren the list of paths to the grand children and on
         * @return access to all children of the last child mentioned
         */
        default Read descend(Address firstChild, Path... furtherChildren) {
            if (firstChild == null) {
                throw new IllegalArgumentException("no first child");
            }

            Read last = get(firstChild).allChildren();

            for (Path p : furtherChildren) {
                if (!Resource.class.equals(p.getSegment().getElementType())) {
                    throw new IllegalArgumentException("Descend can only traverse child resources.");
                }
                last = last.get(p).allChildren();
            }

            return last;
        }
    }

    /**
     * Provides read-only access to resources following the containment chain.
     */
    public interface ReadContained extends ReadBase<String> {
    }

    /**
     * Provides read-only access to resources from positions that follow associations without the possibility to
     * introduce ambiguity when addressing using a relative path.
     */
    public interface Read extends ReadBase<Path> {
    }

    /**
     * Provides read-write access to resources.
     */
    public interface ReadWrite extends ReadWriteInterface<Resource.Update, Resource.Blueprint, Single, Multiple>,
            ReadContained {}

    /**
     * This interface enables the creation of "alternative" tree hierarchies of resources using the
     * {@link org.hawkular.inventory.api.Relationships.WellKnown#isParentOf} relationship. Resources can be contained
     * within each other, which causes such child resources to be deleted along with their parent resources (such
     * resources also implicitly have the {@code isParentOf} relationship between each other). If there is only the
     * {@code isParentOf} relationship between the two resources they form a tree structure but deleting the parent
     * does not affect the child - the link between them just disappears.
     */
    public interface ReadAssociate extends Read, AssociationInterface {

        /**
         * Removes the {@link org.hawkular.inventory.api.Relationships.WellKnown#isParentOf} relationship between the
         * two resources.
         *
         * @param id the id of the entity to remove from the relation with the current entity.
         * @return the relationship that was deleted as a result of the disassociation
         * @throws EntityNotFoundException  if a resource with given id doesn't exist
         * @throws IllegalArgumentException if the resource with the supplied path is existentially bound to its parent
         *                                  resource
         */
        @Override
        Relationship disassociate(Path id) throws EntityNotFoundException, IllegalArgumentException;
    }
}
