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

import java.io.IOException;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Path;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Used to strip the tenant from the canonical path. REST API transparently strips it in the output and re-adds it when
 * processing the input.
 *
 * <p>Note that while the stripping can be done using a custom serializer, the re-introduction of the tenant id to the
 * paths needs to be done manually in each of the "affected" REST API methods because (de-)serializers are stateless
 * and shared and therefore cannot be used for a context-specific, and therefore stateful, work.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public class PathSerializer extends JsonSerializer<CanonicalPath> {
    @Override
    public void serialize(CanonicalPath value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        //strip the tenant from the canonical path so that it appears to users it doesn't even exist. They don't
        //need to provide it in their paths and the API doesn't show it to them either.

        String output = value.toString();

        int firstDelimIdx = output.indexOf(Path.PATH_DELIM);
        int secondDelim = output.indexOf(Path.PATH_DELIM, firstDelimIdx + 1);

        if (secondDelim < 0) {
            jgen.writeString(output);
        } else {
            jgen.writeString(output.substring(secondDelim));
        }
    }
}
