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

import org.hawkular.inventory.api.model.StructuredData;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class StructuredDataDeserializer extends JsonDeserializer<StructuredData> {
    @Override
    public StructuredData deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return deserialize(jp);
    }

    private StructuredData deserialize(JsonParser jp) throws IOException {
        JsonToken token = jp.getCurrentToken();
        if (token == null) {
            return StructuredData.get().undefined();
        }

        switch (token) {
            case START_ARRAY:
                StructuredData.ListBuilder lst = StructuredData.get().list();
                deserializeList(lst, jp);
                return lst.build();
            case START_OBJECT:
                StructuredData.MapBuilder map = StructuredData.get().map();
                deserializeMap(map, jp);
                return map.build();
            case VALUE_TRUE:
                return StructuredData.get().bool(true);
            case VALUE_FALSE:
                return StructuredData.get().bool(false);
            case VALUE_NULL:
                return StructuredData.get().undefined();
            case VALUE_NUMBER_FLOAT:
                return StructuredData.get().floatingPoint(jp.getDoubleValue());
            case VALUE_NUMBER_INT:
                return StructuredData.get().integral(jp.getLongValue());
            case VALUE_STRING:
                return StructuredData.get().string(jp.getText());
        }

        return StructuredData.get().undefined();
    }

    private void deserializeList(StructuredData.AbstractListBuilder<?> bld, JsonParser jp) throws IOException {
        JsonToken token;
        while ((token = jp.nextToken()) != null) {
            switch (token) {
                case START_ARRAY:
                    StructuredData.InnerListBuilder lst = bld.addList();
                    deserializeList(lst, jp);
                    lst.closeList();
                    break;
                case START_OBJECT:
                    StructuredData.InnerMapBuilder map = bld.addMap();
                    deserializeMap(map, jp);
                    map.closeMap();
                    break;
                case VALUE_TRUE:
                    bld.addBool(true);
                    break;
                case VALUE_FALSE:
                    bld.addBool(false);
                    break;
                case VALUE_NULL:
                    bld.addUndefined();
                    break;
                case VALUE_NUMBER_FLOAT:
                    bld.addFloatingPoint(jp.getDoubleValue());
                    break;
                case VALUE_NUMBER_INT:
                    bld.addIntegral(jp.getLongValue());
                    break;
                case VALUE_STRING:
                    bld.addString(jp.getText());
                    break;
                default:
                    return;
            }
        }
    }

    private void deserializeMap(StructuredData.AbstractMapBuilder<?> bld, JsonParser jp) throws IOException {
        JsonToken token;
        while ((token = jp.nextValue()) != null) {
            String key = jp.getCurrentName();
            switch (token) {
                case START_ARRAY:
                    StructuredData.InnerListBuilder lst = bld.putList(key);
                    deserializeList(lst, jp);
                    lst.closeList();
                    break;
                case START_OBJECT:
                    StructuredData.InnerMapBuilder map = bld.putMap(key);
                    deserializeMap(map, jp);
                    map.closeMap();
                    break;
                case VALUE_TRUE:
                    bld.putBool(key, true);
                    break;
                case VALUE_FALSE:
                    bld.putBool(key, false);
                    break;
                case VALUE_NULL:
                    bld.putUndefined(key);
                    break;
                case VALUE_NUMBER_FLOAT:
                    bld.putFloatingPoint(key, jp.getDoubleValue());
                    break;
                case VALUE_NUMBER_INT:
                    bld.putIntegral(key, jp.getLongValue());
                    break;
                case VALUE_STRING:
                    bld.putString(key, jp.getText());
                    break;
                default:
                    return;
            }
        }
    }
}
