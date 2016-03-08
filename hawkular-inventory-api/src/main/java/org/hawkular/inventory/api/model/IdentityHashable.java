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
package org.hawkular.inventory.api.model;

/**
 * Entities implementing this interface compute their identity hash. This hash is essentially a Merkle tree hash that
 * ensures that the entity and its contained child entities are in a certain state.
 *
 * @see IdentityHash
 *
 * @author Lukas Krejci
 * @since 0.11.0
 */
public interface IdentityHashable {

    String getIdentityHash();
}
