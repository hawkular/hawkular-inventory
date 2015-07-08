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

import java.util.Iterator;
import java.util.function.Consumer;

import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
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
         * A shortcut for {@code getAll(id(id1)).allChildren().getAll(id(id2))...} given the path {@code id1->id2->...}.
         *
         * @param path the path to descend to
         * @return access to all children reachable using the relative path
         */
        default Read descend(RelativePath path) {
            if (path.getPath().size() == 0) {
                throw new IllegalArgumentException("empty relative path");
            }

            int[] depth = new int[1];

            Consumer<Path.Segment> checker = (s) -> {
                if (RelativePath.Up.class.equals(s.getElementType())) {
                    --depth[0];
                    if (depth[0] < 0) {
                    }
                } else if (Resource.class.equals(s.getElementType())) {
                    depth[0]++;
                } else {
                    throw new IllegalArgumentException("Descend can only traverse child resources.");
                }
            };

            Iterator<Path.Segment> it = path.getPath().iterator();
            Path.Segment seg = it.next();

            checker.accept(seg);

            Read last = get(toAddress(seg)).allChildren();

            while (it.hasNext()) {
                last = last.get(RelativePath.empty().extend(it.next()).get()).allChildren();
            }

            return last;
        }

        /**
         * A helper method implemented by {@link org.hawkular.inventory.api.Resources.ReadContained} and
         * {@link org.hawkular.inventory.api.Resources.Read}. This is used in the default implementation of the
         * {@link #descend(RelativePath)} method.
         *
         * @param pathSegment the path segment to convert to an address valid for this access interface
         * @return the converted address
         */
        Address toAddress(Path.Segment pathSegment);
    }

    /**
     * Provides read-only access to resources following the containment chain.
     */
    public interface ReadContained extends ReadBase<String> {

        default String toAddress(Path.Segment segment) {
            return segment.getElementId();
        }
    }

    /**
     * Provides read-only access to resources from positions that follow associations without the possibility to
     * introduce ambiguity when addressing using a relative path.
     */
    public interface Read extends ReadBase<Path> {

        default Path toAddress(Path.Segment segment) {
            return RelativePath.empty().extend(segment).get();
        }
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
