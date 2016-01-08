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
package org.hawkular.inventory.json.mixins;

import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
@SuppressWarnings("unused")
public final class EntityBlueprintMixin {

    @JsonProperty
    private String id;

    @JsonProperty
    private Map<String, Object> properties;

    @JsonProperty
    private Map<String, Set<CanonicalPath>> outgoing;

    @JsonProperty
    private Map<String, Set<CanonicalPath>> incoming;

    @JsonIgnore
    public Map<String, Set<CanonicalPath>> getOutgoingRelationships() {
        return null;
    }

    @JsonIgnore
    public Map<String, Set<CanonicalPath>> getIncomingRelationships() {
        return null;
    }
}
