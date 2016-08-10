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
package org.hawkular.inventory.json;

import java.io.IOException;

import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * @author Pavol Loffay
 * @since 0.2.0
 */
public final class PathDeserializer extends JsonDeserializer<Path> {
    @Override
    public Path deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        String val = jp.getValueAsString();
        if (val.isEmpty()) {
            return RelativePath.empty().get();
        }
        return Path.fromString(jp.getValueAsString());
    }
}
