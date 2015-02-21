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

import org.hawkular.inventory.api.model.Tenant;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Tenants {

    private Tenants() {

    }

    private interface BrowserBase<ResourceTypes, MetricTypes, Envs> {
        /**
         * @return resource types API
         */
        ResourceTypes resourceTypes();

        /**
         * @return metric types API
         */
        MetricTypes metricTypes();

        /**
         * @return environments API
         */
        Envs environments();

    }

    public interface Single extends SingleRelatableEntityBrowser<Tenant>,
            BrowserBase<ResourceTypes.ReadWrite, MetricTypes.ReadWrite, Environments.ReadWrite> {}

    public interface Multiple extends MultipleRelatableEntityBrowser<Tenant>,
            BrowserBase<ResourceTypes.Read, MetricTypes.Read, Environments.Read> {}

    public interface Read extends ReadInterface<Single, Multiple> {}

    public interface ReadWrite extends ReadWriteInterface<Tenant, String, Single, Multiple> {}
}
