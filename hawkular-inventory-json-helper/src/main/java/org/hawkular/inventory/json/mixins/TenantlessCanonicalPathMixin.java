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

import org.hawkular.inventory.json.DetypedPathDeserializer;
import org.hawkular.inventory.json.TenantlessPathSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The core inventory API is unaware of JSON and even less so about Jackson, so it can't contain any Jackson specific
 * code there.
 *
 * <p>Fortunately, Jackson has a concept of mixins which can be used to "re-annotate" classes. This mixin class
 * is meant to provide Jackson annotations for CanonicalPath class.
 *
 * @author Lukas Krejci
 * @see com.fasterxml.jackson.databind.ObjectMapper#addMixInAnnotations(Class, Class)
 * @since 0.2.0
 */
@JsonSerialize(using = TenantlessPathSerializer.class)
@JsonDeserialize(using = DetypedPathDeserializer.class)
public final class TenantlessCanonicalPathMixin {
}
