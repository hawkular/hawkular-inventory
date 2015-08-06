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
package org.hawkular.inventory.base.spi;

import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.StructuredData;

/**
 * This is essentially a marker class to distinguish a fully loaded structured data from the "bare" shallow variant that
 * doesn't contain the potential children (see for example
 * {@link org.hawkular.inventory.api.Datas.Single#bareData(RelativePath)}).
 *
 * <p>When calling {@link InventoryBackend#convert(Object, Class)}, with {@link StructuredData}, the whole structured
 * data is loaded. On the other hand calling it with {@code ShallowStructuredData} will make the convert method not load
 * the potential children of the structured data.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
public final class ShallowStructuredData {

    private final StructuredData data;

    public ShallowStructuredData(StructuredData data) {
        this.data = data;
    }

    public StructuredData getData() {
        return data;
    }
}
