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
package org.hawkular.inventory.api.test;

import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.mockito.Mockito;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.when;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class InventoryMock {
    public static Inventory inventory;

    public static Environments.Multiple environmentsMultiple;
    public static Environments.Read environmentsRead;
    public static Environments.ReadWrite environmentsReadWrite;
    public static Environments.Single environmentsSingle;

    public static Feeds.Multiple feedsMultiple;
    public static Feeds.Read feedsRead;
    public static Feeds.ReadAndRegister feedsReadAndRegister;
    public static Feeds.Single feedsSingle;

    public static Metrics.Multiple metricsMultiple;
    public static Metrics.Read metricsRead;
    public static Metrics.ReadAssociate metricsReadAssociate;
    public static Metrics.ReadWrite metricsReadWrite;
    public static Metrics.Single metricsSingle;

    public static MetricTypes.Multiple metricTypesMultiple;
    public static MetricTypes.Read metricTypesRead;
    public static MetricTypes.ReadAssociate metricTypesReadAssociate;
    public static MetricTypes.ReadWrite metricTypesReadWrite;
    public static MetricTypes.Single metricTypesSingle;

    public static Relationships.Multiple relationshipsMultiple;
    public static Relationships.Read relationshipsRead;
    public static Relationships.ReadWrite relationshipsReadWrite;
    public static Relationships.Single relationshipsSingle;

    public static Resources.Multiple resourcesMultiple;
    public static Resources.Read resourcesRead;
    public static Resources.ReadWrite resourcesReadWrite;
    public static Resources.Single resourcesSingle;

    public static ResourceTypes.Multiple resourceTypesMultiple;
    public static ResourceTypes.Read resourceTypesRead;
    public static ResourceTypes.ReadWrite resourceTypesReadWrite;
    public static ResourceTypes.Single resourceTypesSingle;

    public static Tenants.Multiple tenantsMultiple;
    public static Tenants.Read tenantsRead;
    public static Tenants.ReadWrite tenantsReadWrite;
    public static Tenants.Single tenantsSingle;


    public static void rewire() {
        inventory = Mockito.mock(Inventory.class);

        environmentsMultiple = Mockito.mock(Environments.Multiple.class);
        environmentsRead = Mockito.mock(Environments.Read.class);
        environmentsReadWrite = Mockito.mock(Environments.ReadWrite.class);
        environmentsSingle = Mockito.mock(Environments.Single.class);

        feedsMultiple = Mockito.mock(Feeds.Multiple.class);
        feedsRead = Mockito.mock(Feeds.Read.class);
        feedsReadAndRegister = Mockito.mock(Feeds.ReadAndRegister.class);
        feedsSingle = Mockito.mock(Feeds.Single.class);

        metricsMultiple = Mockito.mock(Metrics.Multiple.class);
        metricsRead = Mockito.mock(Metrics.Read.class);
        metricsReadAssociate = Mockito.mock(Metrics.ReadAssociate.class);
        metricsReadWrite = Mockito.mock(Metrics.ReadWrite.class);
        metricsSingle = Mockito.mock(Metrics.Single.class);

        metricTypesMultiple = Mockito.mock(MetricTypes.Multiple.class);
        metricTypesRead = Mockito.mock(MetricTypes.Read.class);
        metricTypesReadAssociate = Mockito.mock(MetricTypes.ReadAssociate.class);
        metricTypesReadWrite = Mockito.mock(MetricTypes.ReadWrite.class);
        metricTypesSingle = Mockito.mock(MetricTypes.Single.class);

        relationshipsMultiple = Mockito.mock(Relationships.Multiple.class);
        relationshipsRead = Mockito.mock(Relationships.Read.class);
        relationshipsReadWrite = Mockito.mock(Relationships.ReadWrite.class);
        relationshipsSingle = Mockito.mock(Relationships.Single.class);

        resourcesMultiple = Mockito.mock(Resources.Multiple.class);
        resourcesRead = Mockito.mock(Resources.Read.class);
        resourcesReadWrite = Mockito.mock(Resources.ReadWrite.class);
        resourcesSingle = Mockito.mock(Resources.Single.class);

        resourceTypesMultiple = Mockito.mock(ResourceTypes.Multiple.class);
        resourceTypesRead = Mockito.mock(ResourceTypes.Read.class);
        resourceTypesReadWrite = Mockito.mock(ResourceTypes.ReadWrite.class);
        resourceTypesSingle = Mockito.mock(ResourceTypes.Single.class);

        tenantsMultiple = Mockito.mock(Tenants.Multiple.class);
        tenantsRead = Mockito.mock(Tenants.Read.class);
        tenantsReadWrite = Mockito.mock(Tenants.ReadWrite.class);
        tenantsSingle = Mockito.mock(Tenants.Single.class);

        when(inventory.tenants()).thenReturn(tenantsReadWrite);

        when(environmentsMultiple.feeds()).thenReturn(feedsRead);
        when(environmentsMultiple.metrics()).thenReturn(metricsRead);
        when(environmentsMultiple.relationships()).thenReturn(relationshipsRead);
        when(environmentsMultiple.relationships(any())).thenReturn(relationshipsRead);
        when(environmentsMultiple.resources()).thenReturn(resourcesRead);
        when(environmentsRead.get(any())).thenReturn(environmentsSingle);
        when(environmentsRead.getAll(anyVararg())).thenReturn(environmentsMultiple);
        when(environmentsReadWrite.get(any())).thenReturn(environmentsSingle);
        when(environmentsReadWrite.getAll(anyVararg())).thenReturn(environmentsMultiple);
        when(environmentsSingle.feeds()).thenReturn(feedsReadAndRegister);
        when(environmentsSingle.metrics()).thenReturn(metricsReadWrite);
        when(environmentsSingle.relationships()).thenReturn(relationshipsReadWrite);
        when(environmentsSingle.relationships(any())).thenReturn(relationshipsReadWrite);
        when(environmentsSingle.resources()).thenReturn(resourcesReadWrite);

        when(feedsMultiple.relationships()).thenReturn(relationshipsRead);
        when(feedsMultiple.relationships(anyVararg())).thenReturn(relationshipsRead);
        when(feedsMultiple.resources()).thenReturn(resourcesRead);
        when(feedsRead.get(any())).thenReturn(feedsSingle);
        when(feedsRead.getAll(anyVararg())).thenReturn(feedsMultiple);
        when(feedsReadAndRegister.get(any())).thenReturn(feedsSingle);
        when(feedsReadAndRegister.getAll(anyVararg())).thenReturn(feedsMultiple);
        when(feedsReadAndRegister.register(any())).thenReturn(feedsSingle);
        when(feedsSingle.relationships()).thenReturn(relationshipsReadWrite);
        when(feedsSingle.relationships(any())).thenReturn(relationshipsReadWrite);
        when(feedsSingle.resources()).thenReturn(resourcesRead);

        when(metricsMultiple.relationships()).thenReturn(relationshipsRead);
        when(metricsMultiple.relationships(any())).thenReturn(relationshipsRead);
        when(metricsRead.get(any())).thenReturn(metricsSingle);
        when(metricsRead.getAll(anyVararg())).thenReturn(metricsMultiple);
        when(metricsReadAssociate.get(any())).thenReturn(metricsSingle);
        when(metricsReadAssociate.getAll(anyVararg())).thenReturn(metricsMultiple);
        when(metricsReadWrite.get(any())).thenReturn(metricsSingle);
        when(metricsReadWrite.getAll(anyVararg())).thenReturn(metricsMultiple);
        when(metricsSingle.relationships()).thenReturn(relationshipsReadWrite);
        when(metricsSingle.relationships(any())).thenReturn(relationshipsReadWrite);

        when(metricTypesMultiple.metrics()).thenReturn(metricsRead);
        when(metricTypesMultiple.relationships()).thenReturn(relationshipsRead);
        when(metricTypesMultiple.relationships(any())).thenReturn(relationshipsRead);
        when(metricTypesRead.get(any())).thenReturn(metricTypesSingle);
        when(metricTypesRead.getAll(anyVararg())).thenReturn(metricTypesMultiple);
        when(metricTypesReadAssociate.get(any())).thenReturn(metricTypesSingle);
        when(metricTypesReadAssociate.getAll(anyVararg())).thenReturn(metricTypesMultiple);
        when(metricTypesReadWrite.get(any())).thenReturn(metricTypesSingle);
        when(metricTypesReadWrite.getAll(anyVararg())).thenReturn(metricTypesMultiple);
        when(metricTypesSingle.metrics()).thenReturn(metricsRead);
        when(metricTypesSingle.relationships()).thenReturn(relationshipsReadWrite);
        when(metricTypesSingle.relationships(any())).thenReturn(relationshipsReadWrite);

        when(relationshipsMultiple.environments()).thenReturn(environmentsRead);
        when(relationshipsMultiple.feeds()).thenReturn(feedsRead);
        when(relationshipsMultiple.metrics()).thenReturn(metricsRead);
        when(relationshipsMultiple.metricTypes()).thenReturn(metricTypesRead);
        when(relationshipsMultiple.resources()).thenReturn(resourcesRead);
        when(relationshipsMultiple.resourceTypes()).thenReturn(resourceTypesRead);
        when(relationshipsMultiple.tenants()).thenReturn(tenantsRead);
        when(relationshipsRead.get(any())).thenReturn(relationshipsSingle);
        when(relationshipsRead.getAll(anyVararg())).thenReturn(relationshipsMultiple);
        when(relationshipsRead.named(anyString())).thenReturn(relationshipsMultiple);
        when(relationshipsRead.named(Mockito.<Relationships.WellKnown>any())).thenReturn(relationshipsMultiple);
        when(relationshipsReadWrite.named(anyString())).thenReturn(relationshipsMultiple);
        when(relationshipsReadWrite.named(Mockito.<Relationships.WellKnown>any())).thenReturn(relationshipsMultiple);

        when(resourcesMultiple.metrics()).thenReturn(metricsRead);
        when(resourcesMultiple.relationships()).thenReturn(relationshipsRead);
        when(resourcesMultiple.relationships(any())).thenReturn(relationshipsRead);
        when(resourcesRead.get(any())).thenReturn(resourcesSingle);
        when(resourcesRead.getAll(anyVararg())).thenReturn(resourcesMultiple);
        when(resourcesReadWrite.get(any())).thenReturn(resourcesSingle);
        when(resourcesReadWrite.getAll(anyVararg())).thenReturn(resourcesMultiple);
        when(resourcesSingle.metrics()).thenReturn(metricsReadAssociate);
        when(resourcesSingle.relationships()).thenReturn(relationshipsReadWrite);
        when(resourcesSingle.relationships(any())).thenReturn(relationshipsReadWrite);

        when(resourceTypesMultiple.metricTypes()).thenReturn(metricTypesRead);
        when(resourceTypesMultiple.relationships()).thenReturn(relationshipsRead);
        when(resourceTypesMultiple.relationships(any())).thenReturn(relationshipsRead);
        when(resourceTypesRead.get(any())).thenReturn(resourceTypesSingle);
        when(resourceTypesRead.getAll(anyVararg())).thenReturn(resourceTypesMultiple);
        when(resourceTypesReadWrite.get(any())).thenReturn(resourceTypesSingle);
        when(resourceTypesReadWrite.getAll(anyVararg())).thenReturn(resourceTypesMultiple);
        when(resourceTypesSingle.metricTypes()).thenReturn(metricTypesReadAssociate);
        when(resourceTypesSingle.relationships()).thenReturn(relationshipsReadWrite);
        when(resourceTypesSingle.relationships(any())).thenReturn(relationshipsReadWrite);

        when(tenantsReadWrite.get(anyString())).thenReturn(tenantsSingle);
        when(tenantsReadWrite.getAll(anyVararg())).thenReturn(tenantsMultiple);
        when(tenantsRead.get(anyString())).thenReturn(tenantsSingle);
        when(tenantsRead.getAll(anyVararg())).thenReturn(tenantsMultiple);
        when(tenantsSingle.environments()).thenReturn(environmentsReadWrite);
        when(tenantsSingle.metricTypes()).thenReturn(metricTypesReadWrite);
        when(tenantsSingle.relationships()).thenReturn(relationshipsReadWrite);
        when(tenantsSingle.relationships(any())).thenReturn(relationshipsReadWrite);
        when(tenantsSingle.resourceTypes()).thenReturn(resourceTypesReadWrite);
        when(tenantsMultiple.environments()).thenReturn(environmentsRead);
        when(tenantsMultiple.metricTypes()).thenReturn(metricTypesRead);
        when(tenantsMultiple.relationships()).thenReturn(relationshipsRead);
        when(tenantsMultiple.relationships(any())).thenReturn(relationshipsRead);
        when(tenantsMultiple.resourceTypes()).thenReturn(resourceTypesRead);
    }
}
