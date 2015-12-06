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
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.ResourceType;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on resource types.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ResourceTypes {

    private ResourceTypes() {

    }

    public enum DataRole implements DataEntity.Role {
        configurationSchema {
            @Override
            public boolean isSchema() {
                return true;
            }

            @Override
            public Filter[] navigateToSchema() {
                return null;
            }
        },
        connectionConfigurationSchema {
            @Override
            public boolean isSchema() {
                return true;
            }

            @Override
            public Filter[] navigateToSchema() {
                return null;
            }
        }
    }

    private interface BrowserBase<Resources, MetricTypes, OperationTypes, Data> {
        /**
         * @return access to resources defined by the resource type(s)
         */
        Resources resources();

        /**
         * @return access to metric types associated with the resource type(s)
         */
        MetricTypes metricTypes();

        /**
         * @return access to the operation types contained by the resource type(s)
         */
        OperationTypes operationTypes();

        /**
         * @return access to the data associated with the resource type.
         */
        Data data();
    }

    /**
     * Interface for accessing a single resource type in a writable manner.
     */
    public interface Single extends ResolvableToSingleWithRelationships<ResourceType, ResourceType.Update>,
            BrowserBase<Resources.Read, MetricTypes.ReadAssociate, OperationTypes.ReadWrite, Data.ReadWrite<DataRole>> {
    }

    /**
     * Interface for traversing over a set of resource types.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(Object)} method).
     */
    public interface Multiple extends ResolvableToManyWithRelationships<ResourceType>,
            BrowserBase<Resources.Read, MetricTypes.Read, OperationTypes.ReadContained, Data.Read<DataRole>> {
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
