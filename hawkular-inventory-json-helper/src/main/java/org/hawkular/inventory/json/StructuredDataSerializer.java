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
package org.hawkular.inventory.json;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.hawkular.inventory.api.model.StructuredData;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class StructuredDataSerializer extends JsonSerializer<StructuredData> {
    @Override
    public void serialize(StructuredData value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        value.accept(new StructuredData.Visitor.Simple<Void, Void>() {
            @Override
            public Void visitBool(boolean value, Void parameter) {
                try {
                    jgen.writeBoolean(value);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write value of structured data.", e);
                }
                return null;
            }

            @Override
            public Void visitIntegral(long value, Void parameter) {
                try {
                    jgen.writeNumber(value);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write value of structured data.", e);
                }
                return null;
            }

            @Override
            public Void visitFloatingPoint(double value, Void parameter) {
                try {
                    jgen.writeNumber(value);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write value of structured data.", e);
                }
                return null;
            }

            @Override
            public Void visitString(String value, Void parameter) {
                try {
                    jgen.writeString(value);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write value of structured data.", e);
                }
                return null;
            }

            @Override
            public Void visitUndefined(Void parameter) {
                try {
                    jgen.writeNull();
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write value of structured data.", e);
                }
                return null;
            }

            @Override
            public Void visitList(List<StructuredData> value, Void parameter) {
                try {
                    jgen.writeStartArray();
                    for (StructuredData m : value) {
                        m.accept(this, null);
                    }
                    jgen.writeEndArray();
                    return null;
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write structured data.", e);
                }
            }

            @Override
            public Void visitMap(Map<String, StructuredData> value, Void parameter) {
                try {
                    jgen.writeStartObject();
                    for (Map.Entry<String, StructuredData> e : value.entrySet()) {
                        jgen.writeFieldName(e.getKey());
                        e.getValue().accept(this, null);
                    }
                    jgen.writeEndObject();
                    return null;
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write structured data.", e);
                }
            }
        }, null);
    }
}
