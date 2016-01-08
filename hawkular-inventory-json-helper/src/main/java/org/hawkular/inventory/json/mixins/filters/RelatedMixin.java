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
package org.hawkular.inventory.json.mixins.filters;

import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.paths.CanonicalPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Pavol Loffay
 * @since 0.13.0
 */
public final class RelatedMixin {

    @JsonCreator
    public RelatedMixin(@JsonProperty("entityPath") CanonicalPath entityPath,
                        @JsonProperty("relationshipName") String relationshipName,
                        @JsonProperty("relationshipId") String relationshipId,
                        @JsonProperty("entityRole") Related.EntityRole entityRole) {
    }

    /**
     * you can only have one creator per type. You can have as many
     * constructors as you want in your type, but only one of them should have a @JsonCreator annotation on it.
     */
//    @JsonCreator
//    public RelatedMixin(@JsonProperty("entityPath") CanonicalPath entityPath,
//                        @JsonProperty("relationshipName") String relationshipName,
//                        @JsonProperty("entityRole") Related.EntityRole entityRole) {
//    }
}
