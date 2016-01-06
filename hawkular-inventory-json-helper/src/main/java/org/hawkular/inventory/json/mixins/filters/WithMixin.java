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

import java.io.Serializable;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.StructuredData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * @author Pavol Loffay
 * @since 0.13.0
 */
public final class WithMixin {

    public static final class IdsMixin {
        @JsonCreator
        public IdsMixin(@JsonProperty("ids") String... ids) {
        }
    }

    public static final class CanonicalPathsMixin {
        @JsonCreator
        public CanonicalPathsMixin(@JsonProperty("paths") CanonicalPath... id) {
        }
    }

    public static final class TypesMixin {
        @JsonCreator
        public TypesMixin(@JsonProperty("types") Class<? extends Entity<?, ?>>... types) {
        }
    }

    public static final class RelativePathsMixin {
        @JsonCreator
        public RelativePathsMixin(@JsonProperty("markerLabel") String markerLabel,
                                  @JsonProperty("paths") RelativePath... paths) {
        }
    }

    public static final class PropertyValuesMixin {
        @JsonCreator
        public PropertyValuesMixin(@JsonProperty("name") String name, @JsonProperty("values") Object... values) {
        }
    }

    public abstract static class DataValuedMixin {
        @JsonCreator
        public DataValuedMixin(@JsonProperty("value") Serializable value) {
        }

        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
        public abstract Serializable getValue();
    }

    public static final class DataAtMixin {
        @JsonCreator
        public DataAtMixin(@JsonProperty("dataPath") RelativePath dataPath) {
        }
    }

    public static final class DataOfTypesMixin {
        @JsonCreator
        public DataOfTypesMixin(@JsonProperty("types") StructuredData.Type... types) {
        }
    }
}
