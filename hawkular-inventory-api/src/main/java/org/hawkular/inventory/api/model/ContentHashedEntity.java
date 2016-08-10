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

import java.util.Map;

import org.hawkular.inventory.paths.CanonicalPath;

/**
 * Base class for entities with a content hash.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
public abstract class ContentHashedEntity<B extends Blueprint, U extends Entity.Update> extends Entity<B, U>
    implements ContentHashable {

    private final String contentHash;

    protected ContentHashedEntity() {
        this.contentHash = null;
    }

    protected ContentHashedEntity(CanonicalPath path, String contentHash) {
        super(path);
        this.contentHash = contentHash;
    }

    protected ContentHashedEntity(CanonicalPath path,
                                  String contentHash, Map<String, Object> properties) {
        super(path, properties);
        this.contentHash = contentHash;
    }

    protected ContentHashedEntity(String name, CanonicalPath path, String contentHash) {
        super(name, path);
        this.contentHash = contentHash;
    }

    protected ContentHashedEntity(String name, CanonicalPath path,
                                  String contentHash, Map<String, Object> properties) {
        super(name, path, properties);
        this.contentHash = contentHash;
    }

    @Override public String getContentHash() {
        return contentHash;
    }
}
