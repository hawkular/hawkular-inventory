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
package org.hawkular.inventory.bus.api;

import org.hawkular.bus.common.AbstractMessage;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.hawkular.inventory.json.mixins.model.CanonicalPathMixin;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Pavol Loffay
 * @since 0.13.0
 */
public abstract class InventoryAbstractMessage extends AbstractMessage {

    @Override
    protected ObjectMapper buildObjectMapperForSerialization() {
        final ObjectMapper mapper = new ObjectMapper();
        InventoryJacksonConfig.configure(mapper);
        mapper.addMixIn(CanonicalPath.class, CanonicalPathMixin.class);
        return mapper;
    }

    public static ObjectMapper buildObjectMapperForDeserialization() {
        final ObjectMapper mapper = new ObjectMapper();
        InventoryJacksonConfig.configure(mapper);
        mapper.addMixIn(CanonicalPath.class, CanonicalPathMixin.class);
        return mapper;
    }
}
