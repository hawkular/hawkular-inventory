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

import io.swagger.annotations.ApiModel;

/**
 * Abstract base class for all syncable entities.
 *
 * @author Lukas Krejci
 * @since 0.18.0
 */
@ApiModel(description = "A super type of all entities that support identity hashing",
        subTypes = {Feed.class, MetricType.class, Metric.class, Resource.class, DataEntity.class, OperationType.class,
        ResourceType.class}, parent = Entity.class)
public abstract class SyncedEntity<B extends Entity.Blueprint, U extends Entity.Update> extends Entity<B, U>
    implements Syncable {

    private final String identityHash;
    private final String contentHash;
    private final String syncHash;

    @SuppressWarnings("unused") SyncedEntity() {
        identityHash = null;
        contentHash = null;
        syncHash = null;
    }

    SyncedEntity(String name, CanonicalPath path, String identityHash, String contentHash, String syncHash) {
        super(name, path);
        this.identityHash = identityHash;
        this.contentHash = contentHash;
        this.syncHash = syncHash;
    }

    SyncedEntity(String name, CanonicalPath path,
                 String identityHash, String contentHash, String syncHash, Map<String, Object> properties) {
        super(name, path, properties);
        this.identityHash = identityHash;
        this.contentHash = contentHash;
        this.syncHash = syncHash;
    }

    SyncedEntity(CanonicalPath path, String identityHash, String contentHash, String syncHash) {
        super(path);
        this.identityHash = identityHash;
        this.contentHash = contentHash;
        this.syncHash = syncHash;
    }

    SyncedEntity(CanonicalPath path, String identityHash, String contentHash, String syncHash,
                 Map<String, Object> properties) {
        super(path, properties);
        this.identityHash = identityHash;
        this.contentHash = contentHash;
        this.syncHash = syncHash;
    }

    @Override
    public String getIdentityHash() {
        return identityHash;
    }

    @Override public String getContentHash() {
        return contentHash;
    }

    @Override public String getSyncHash() {
        return syncHash;
    }
}
