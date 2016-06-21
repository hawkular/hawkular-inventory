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

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.SegmentType;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class DetypedPathDeserializer extends JsonDeserializer<Path> {
    private static ThreadLocal<CanonicalPath> CURRENT_CANONICAL_ORIGIN = new ThreadLocal<>();
    private static final ThreadLocal<CanonicalPath> CURRENT_RELATIVE_PATH_ORIGIN = new ThreadLocal<>();
    private static final ThreadLocal<Class<?>> CURRENT_ENTITY_TYPE = new ThreadLocal<>();

    public static void setCurrentCanonicalOrigin(CanonicalPath path) {
        CURRENT_CANONICAL_ORIGIN.set(path);
    }

    public static void setCurrentRelativePathOrigin(CanonicalPath path) {
        CURRENT_RELATIVE_PATH_ORIGIN.set(path);
    }

    public static void setCurrentEntityType(Class<?> entityType) {
        CURRENT_ENTITY_TYPE.set(entityType);
    }

    @Override
    public Path deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        String str = jp.getValueAsString();

        CanonicalPath co = CURRENT_CANONICAL_ORIGIN.get();
        CanonicalPath ro = CURRENT_RELATIVE_PATH_ORIGIN.get();
        Class<?> entityType = CURRENT_ENTITY_TYPE.get();

        return Path.fromPartiallyUntypedString(str, co, ro, getIntendedFinalType(entityType));
    }

    private SegmentType getIntendedFinalType(Class<?> type) {
        if (type == null) {
            return null;
        }

        try {
            return AbstractElement.segmentTypeFromType(type);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }
}
