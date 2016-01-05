/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public abstract class IdentityHashedEntity<B extends Entity.Blueprint, U extends Entity.Update> extends Entity<B, U>
    implements IdentityHashable {

    private final String identityHash;

    @SuppressWarnings("unused")
    IdentityHashedEntity() {
        identityHash = null;
    }

    IdentityHashedEntity(String name, CanonicalPath path, String identityHash) {
        super(name, path);
        this.identityHash = identityHash;
    }

    IdentityHashedEntity(String name, CanonicalPath path,
                         String identityHash, Map<String, Object> properties) {
        super(name, path, properties);
        this.identityHash = identityHash;
    }

    IdentityHashedEntity(CanonicalPath path, String identityHash) {
        super(path);
        this.identityHash = identityHash;
    }

    IdentityHashedEntity(CanonicalPath path, String identityHash, Map<String, Object> properties) {
        super(path, properties);
        this.identityHash = identityHash;
    }

    @Override
    public String getIdentityHash() {
        return identityHash;
    }
}
