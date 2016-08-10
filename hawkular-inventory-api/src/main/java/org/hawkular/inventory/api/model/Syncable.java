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
 * Interface implemented by all entities that can be synchronized.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
public interface Syncable extends IdentityHashable, ContentHashable {
    /**
     * A sync hash is a hash used to compare entities during the sync operation. This hash depends both on the
     * identity hash and content hash of entities.
     *
     * @return the sync hash of the entity
     */
    String getSyncHash();
}
