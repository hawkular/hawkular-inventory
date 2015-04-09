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
package org.hawkular.inventory.rest.json;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple wrapper for Ids
 * @author Heiko Rupp
 */
public class IdJSON {

    private String id;
    private Map<String, Object> properties;

    public IdJSON() {
    }

    public IdJSON(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getProperties() {
        return properties == null ? new HashMap<>() : properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
