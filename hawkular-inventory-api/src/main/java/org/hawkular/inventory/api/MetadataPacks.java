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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.paths.Path;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public final class MetadataPacks {

    private MetadataPacks() {

    }

    /**
     * An interface implemented by Single/Multiple interfaces of entities that can contain metadata packs.
     * @param <Access> the type of access to metadata packs
     */
    public interface Container<Access> {
        Access metadataPacks();
    }

    //note that this does not implement ResourceTypes.Container and MetricTypes.Container intentionally, because
    //metadata packs do not *contain* those types - they merely reference them
    public interface BrowserBase {

        ResourceTypes.Read resourceTypes();

        MetricTypes.Read metricTypes();
    }

    public interface Single extends ResolvableToSingleWithRelationships<MetadataPack, MetadataPack.Update>,
            BrowserBase {
    }

    public interface Multiple extends ResolvableToManyWithRelationships<MetadataPack>, BrowserBase {
    }

    public interface ReadContained extends ReadInterface<Single, Multiple, String> {
    }

    public interface Read extends ReadInterface<Single, Multiple, Path> {
    }

    public interface ReadWrite extends ReadWriteInterface<MetadataPack.Update, MetadataPack.Blueprint, Single,
            Multiple, String> {
    }
}
