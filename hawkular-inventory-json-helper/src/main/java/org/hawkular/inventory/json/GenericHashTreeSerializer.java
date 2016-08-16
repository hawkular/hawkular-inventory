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
import java.io.Serializable;

import org.hawkular.inventory.api.model.AbstractHashTree;
import org.hawkular.inventory.paths.Path;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * @author Lukas Krejci
 * @since 0.18.0
 */
public class GenericHashTreeSerializer<T extends AbstractHashTree<T, H>, H extends Serializable>
        extends JsonSerializer<T> {

    @Override public void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {

        gen.writeStartObject();

        serializers.defaultSerializeField("hash", value.getHash(), gen);

        gen.writeObjectFieldStart("children");

        for (T child : value.getChildren()) {
            gen.writeFieldName(segmentToString(child.getPath().getSegment()));
            serialize(child, gen, serializers);
        }

        //children
        gen.writeEndObject();

        //whole object
        gen.writeEndObject();
    }


    private static String segmentToString(Path.Segment segment) {
        return (segment.getElementType() == null ? "" : (segment.getElementType().getSerialized() + ";")) +
                segment.getElementId();
    }
}
