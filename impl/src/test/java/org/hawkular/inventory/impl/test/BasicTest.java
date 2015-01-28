/*
 * Copyright 2014-2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.impl.test;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Resource;
import org.hawkular.inventory.api.ResourceType;
import org.hawkular.inventory.impl.InventoryService;
import org.junit.Test;

import java.util.List;

/**
 * Test some basic functionality
 *
 * @author Heiko W. Rupp
 */
public class BasicTest {

    @Test
    public void testAddGetOne() throws Exception {

        Inventory inventory = new InventoryService();

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
        assert resources.get(0).equals(result);

    }

    @Test
    public void testAddGetBadTenant() throws Exception {

        Inventory inventory = new InventoryService();

        Resource resource = new Resource();
        resource.setType(ResourceType.URL);
        resource.addParameter("url","http://hawkular.org");
        String id = inventory.addResource("test",resource);

        Resource result = inventory.getResource("bla",id);
        assert result == null;

    }
}
