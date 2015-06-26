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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * We can't touch the CanonicalPath code here, but Jackson enables us to use mixins to "re-annotate" the classes.
 *
 * @author Lukas Krejci
 * @see com.fasterxml.jackson.databind.ObjectMapper#addMixInAnnotations(Class, Class)
 * @see JacksonConfig
 * @since 0.1.0
 */
@JsonSerialize(using = PathSerializer.class)
public final class CanonicalPathSerializationMixin {
}
