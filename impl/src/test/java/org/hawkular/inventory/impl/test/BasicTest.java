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
package org.hawkular.inventory.impl.test;

import org.hawkular.inventory.api.MetricDefinition;
import org.hawkular.inventory.api.MetricUnit;
import org.hawkular.inventory.api.Resource;
import org.hawkular.inventory.api.ResourceType;
import org.hawkular.inventory.impl.InventoryService;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test some basic functionality
 *
 * @author Heiko W. Rupp
 */
public class BasicTest {


    Connection conn;

    @Before
    public void setup() throws Exception {


        Class.forName("org.h2.Driver");
        conn = DriverManager.
            getConnection("jdbc:h2:mem:test");
    }

    @Test
    public void testAddGetOne() throws Exception {

        InventoryService inventory = new InventoryService(conn);

        Resource resource = new Resource();
        resource.setType(ResourceType.URL);
        resource.addParameter("url","http://hawkular.org");
        String id = inventory.addResource("test",resource);

        assert id != null;
        assert !id.isEmpty();

        Resource result = inventory.getResource("test",id);
        assert result != null;
        assert result.getId()!=null;
        assert result.getId().equals(id);

        List<Resource> resources = inventory.getResourcesForType("test",ResourceType.URL);
        assert resources != null;
        assert !resources.isEmpty();
        assert resources.size() == 1 : "Found " + resources.size() + " entries, but expected 1";
        assert resources.get(0).equals(result);

    }

    @Test
    public void testAddGetBadTenant() throws Exception {

        InventoryService inventory = new InventoryService(conn);

        Resource resource = new Resource();
        resource.setType(ResourceType.URL);
        resource.addParameter("url","http://hawkular.org");
        String id = inventory.addResource("test2",resource);

        Resource result = inventory.getResource("bla",id);
        assert result == null;

    }

    @Test
    public void testAddMetricsToResource() throws Exception {

        InventoryService inventory = new InventoryService(conn);

        Resource resource = new Resource();
        resource.setType(ResourceType.URL);
        resource.addParameter("url","http://hawkular.org");
        String tenant = "test3";
        String id = inventory.addResource(tenant,resource);


        inventory.addMetricToResource(tenant,id,"vm.user_load");
        inventory.addMetricToResource(tenant,id,"vm.system_load");
        inventory.addMetricToResource(tenant,id,"vm.size");
        List<MetricDefinition> definitions = new ArrayList<>(2);
        definitions.add(new MetricDefinition("cpu.count1"));
        definitions.add(new MetricDefinition("cpu.count15"));
        MetricDefinition def = new MetricDefinition("cpu.load.42", MetricUnit.NONE);
        def.setDescription("The question, you know :-)");
        definitions.add(def);
        inventory.addMetricsToResource(tenant, id, definitions );

        List<MetricDefinition> metrics = inventory.listMetricsForResource(tenant,id);

        assert metrics.size()==6;

        MetricDefinition updateDef = new MetricDefinition("vm.size");
        updateDef.setUnit(MetricUnit.BYTE);
        updateDef.setDescription("How much memory does the vm use?");

        boolean updated = inventory.updateMetric(tenant,id,updateDef);
        assert updated;

        MetricDefinition vmDef = inventory.getMetric(tenant,id,"vm.size");
        assertNotNull(vmDef);
        assertEquals("vm.size", vmDef.getName());
        assertEquals(MetricUnit.BYTE, vmDef.getUnit());
    }
}
