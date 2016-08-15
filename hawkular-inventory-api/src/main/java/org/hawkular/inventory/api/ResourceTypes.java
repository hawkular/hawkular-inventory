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

import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.Path;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on resource types.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ResourceTypes {

    private ResourceTypes() {

    }

    private interface BrowserBase<ResourcesAccess, MetricTypesAccess, OperationTypesAccess, DataAccess>
            extends OperationTypes.Container<OperationTypesAccess>, Data.Container<DataAccess> {
        /**
         * @return access to resources defined by the resource type(s)
         */
        ResourcesAccess resources();

        /**
         * @return access to metric types associated with the resource type(s)
         */
        MetricTypesAccess metricTypes();

        /**
         * @return access to the operation types contained by the resource type(s)
         */
        OperationTypesAccess operationTypes();

        /**
         * @return access to the data associated with the resource type.
         */
        DataAccess data();

        /**
         * @return resource types that are equivalent to this resource type based on the
         * {@link org.hawkular.inventory.api.model.IdentityHash} rules.
         */
        Read identical();
    }

    /**
     * An interface implemented by Single/Multiple interfaces of entities that can contain resource types.
     * @param <Access> the type of access to resource types
     */
    public interface Container<Access> {
        Access resourceTypes();
    }

    /**
     * Interface for accessing a single resource type in a writable manner.
     */
    public interface Single
            extends Synced.SingleWithRelationships<ResourceType, ResourceType.Blueprint, ResourceType.Update>,
            BrowserBase<Resources.Read, MetricTypes.ReadAssociate, OperationTypes.ReadWrite,
                    Data.ReadWrite<DataRole.ResourceType>> {
    }

    /**
     * Interface for traversing over a set of resource types.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(Object)} method).
     */
    public interface Multiple extends ResolvableToManyWithRelationships<ResourceType>,
            BrowserBase<Resources.Read, MetricTypes.Read, OperationTypes.ReadContained,
                    Data.Read<DataRole.ResourceType>> {
    }

    /**
     * Provides read-only access to resource types.
     */
    public interface ReadContained extends ReadInterface<Single, Multiple, String> {}

    public interface Read extends ReadInterface<Single, Multiple, Path> {}

    /**
     * Provides read-write access to resource types.
     */
    public interface ReadWrite
            extends ReadWriteInterface<ResourceType.Update, ResourceType.Blueprint, Single, Multiple, String> {}

    public interface ReadAssociate extends Read, AssociationInterface {
    }
}
