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

import org.hawkular.inventory.api.model.Entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Pavol Loffay
 * @since 0.13.0
 */
public class RelationWithMixin {

    public static class IdsMixin {
        @JsonCreator
        public IdsMixin(@JsonProperty("ids") String... ids) {
        }
    }

    public static class PropertyValuesMixin {
        @JsonCreator
        public PropertyValuesMixin(@JsonProperty("property") String property,
                                   @JsonProperty("values") Object... values) {
        }
    }

    public abstract static class SourceOrTargetOfTypeMixin {
        @JsonCreator
        public SourceOrTargetOfTypeMixin(@JsonProperty("types") Class<? extends Entity<?, ?>>... types) {
        }
        @JsonIgnore
        public abstract String getFilterName();
    }

    public abstract static class SourceOfTypeMixin {
        @JsonCreator
        public SourceOfTypeMixin(@JsonProperty("types") Class<? extends Entity<?, ?>>... types) {
        }
        @JsonIgnore
        public abstract String getFilterName();
    }

    public abstract static class TargetOfTypeMixin {
        @JsonCreator
        public TargetOfTypeMixin(@JsonProperty("types") Class<? extends Entity<?, ?>>... types) {
        }
        @JsonIgnore
        public abstract String getFilterName();
    }
}
