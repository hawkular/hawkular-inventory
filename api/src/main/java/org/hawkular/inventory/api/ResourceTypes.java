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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.ResourceType;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ResourceTypes {

    private ResourceTypes() {

    }

    private interface BrowserBase<Resources, MetricTypes> {
        Resources resources();

        MetricTypes metricTypes();
    }

    public interface Single extends SingleRelatableEntityBrowser<ResourceType>,
            BrowserBase<Resources.Read, MetricTypes.ReadRelate> {}

    public interface Multiple extends MultipleRelatableEntityBrowser<ResourceType>,
            BrowserBase<Resources.Read, MetricTypes.Read> {}

    public interface Read extends ReadInterface<Single, Multiple> {}
    public interface ReadWrite extends ReadWriteInterface<ResourceType, ResourceType.Blueprint, Single, Multiple> {}
    public interface ReadRelate extends ReadInterface<Single, Multiple>, RelateInterface {}
}
