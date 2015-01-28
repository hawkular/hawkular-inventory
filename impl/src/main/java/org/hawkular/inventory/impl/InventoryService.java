/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.impl;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Resource;
import org.hawkular.inventory.api.ResourceType;

import java.util.*;

/**
 * The inventory backend
 * @author Heiko Rupp
 */
public class InventoryService implements Inventory {

    // Those are dummy impls that need to be replaced by more persistent ones
    Map<String, Resource> resourcesByUid = new HashMap<String, Resource>();
    Map<ResourceType, List<Resource>> resourcesByType = new HashMap<>();
    Map<String,String> tenantByUId = new HashMap<>();


    @Override
    public String addResource(String tenant, Resource resource) throws Exception {

        String id = resource.getId();
        if (id == null || id.isEmpty()) {
            id = createUUID();
            resource.setId(id);
        }

        resourcesByUid.put(id,resource);
        tenantByUId.put(id,tenant);

        List<Resource> resources = resourcesByType.get(resource.getType());
        if (resources == null) {
            resources = new ArrayList<>();
            resourcesByType.put(resource.getType(),resources);
        }
        resources.add(resource);

        return id;
    }

    @Override
    public List<Resource> getResourcesForType(String tenant, ResourceType type) throws Exception {

        List<Resource> result = new ArrayList<>();

        if (!resourcesByType.containsKey(type)) {
            return result;
        }

        for (Resource resource: resourcesByType.get(type)) {
            if (tenantByUId.get(resource.getId()).equals(tenant)) {
                result.add(resource);
            }
        }

        return result;
    }

    @Override
    public Resource getResource(String tenant, String uid) throws Exception {

        if (tenantByUId.get(uid).equals(tenant)) {
            return resourcesByUid.get(uid);
        }

        return null;
    }

    private String createUUID() {
        return "x" + String.valueOf(System.currentTimeMillis());
    }
}
