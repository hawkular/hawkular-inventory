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
import org.hawkular.inventory.api.model.OperationType;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
public final class OperationTypes {
    private OperationTypes() {
    }

    public enum DataRole implements DataEntity.Role {
        returnType {
            @Override
            public boolean isSchema() {
                return true;
            }

            @Override
            public Filter[] navigateToSchema() {
                return null;
            }
        },
        parameterTypes {
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

    public interface BrowserBase<Data> {
        Data data();
    }

    public interface ReadContained extends ReadInterface<Single, Multiple, String> {

    }

    public interface ReadWrite
            extends ReadWriteInterface<OperationType.Update, OperationType.Blueprint, Single, Multiple, String> {

    }

    public interface Single
            extends ResolvableToSingleWithRelationships<OperationType, OperationType.Update>,
            BrowserBase<Data.ReadWrite<DataRole>> {

    }

    public interface Multiple extends ResolvableToManyWithRelationships<OperationType>,
            BrowserBase<Data.Read<DataRole>> {

    }
}
