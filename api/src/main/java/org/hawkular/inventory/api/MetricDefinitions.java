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

import org.hawkular.inventory.api.model.MetricDefinition;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class MetricDefinitions {

    private MetricDefinitions() {

    }

    private interface BrowserBase {
        Metrics.Read metrics();
    }

    public interface Single extends SingleRelatableEntityBrowser<MetricDefinition>, BrowserBase {
    }

    public interface Multiple extends MultipleRelatableEntityBrowser<MetricDefinition>, BrowserBase {
    }

    public interface ReadWrite extends ReadWriteInterface<MetricDefinition, MetricDefinition.Blueprint, Single,
            Multiple> {}

    public interface Read extends ReadInterface<Single, Multiple> {}

    public interface ReadRelate extends Read, RelateInterface {}
}
