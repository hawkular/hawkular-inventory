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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Used to strip the tenant from the canonical path when serializing it.
 *
 * <p>Note that while the stripping can be done using a custom serializer statelessly, the re-introduction of the tenant
 * id to the paths needs manual intervention because the resolution is contextual - it depends on the tenant ID as well
 * as the intended type of the target entity and the path that the path is relative against (if the path is relative).
 * That's why the {@link DetypedPathDeserializer} contains a couple of static methods to inject this context into it.
 *
 * <p>So while this serializer is universally usable, the deserializer is not. It can only be used in places that can
 * reliably inject the necessary state before the deserialization occurs.
 *
 * <p>To use this path serializer to serialize canonical (and relative) paths, you need to configure Jackson for example
 * by providing a mixin class with custom annotations.
 *
 * @author Lukas Krejci
 * @see com.fasterxml.jackson.databind.ObjectMapper#addMixInAnnotations(Class, Class)
 * @since 0.2.0
 */
public final class TenantlessPathSerializer extends JsonSerializer<Path> {
    @Override
    public void serialize(Path value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        //this is easy for relative paths - just use its string representation
        if (value instanceof RelativePath) {
            jgen.writeString(value.toString());
            return;
        }

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
