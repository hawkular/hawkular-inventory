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
package org.hawkular.inventory.rest.json;

import org.hawkular.inventory.json.PathSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * We need to deserialize paths manually in REST API, because we cannot provide the default
 * {@link org.hawkular.inventory.json.PathDeserializer} with enough info in the early stages of request processing.
 *
 * <p>So we need to configure it with a custom mixin that will be used just for serialization.
 *
 * @author Lukas Krejci
 * @see JacksonConfig
 * @since 0.2.0
 */
@JsonSerialize(using = PathSerializer.class)
public final class PathSerializationMixin {
}
