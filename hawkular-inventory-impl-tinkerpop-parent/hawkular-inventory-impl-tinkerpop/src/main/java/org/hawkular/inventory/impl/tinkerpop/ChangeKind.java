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
package org.hawkular.inventory.impl.tinkerpop;

/**
 * We represent a change kind on the __inState edges as ordinals of this enum. We need 2 values for a delete because
 * a delete is represented as just an explicit to date coupled with a change kind. We need to distinguish between a
 * delete after create and delete after update so that we can correctly reconstruct the history of mutations on an
 * entity.
 *
 * @author Lukas Krejci
 * @since 0.20.0
 */
enum ChangeKind {
    create, update,

    /**
     * represents a delete for an entity right after it has been created with no intermediate update
     */
    delete_after_create,

    /**
     * represents a delete following an update
     */
    delete_after_update;

    static ChangeKind deleteOf(ChangeKind priorState) {
        switch (priorState) {
            case create:
                return delete_after_create;
            case update:
                return delete_after_update;
            default:
                throw new IllegalArgumentException("Delete of a delete is definitely unexpected.");
        }
    }
}
