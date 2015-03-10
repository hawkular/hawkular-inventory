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
package org.hawkular.inventory.api.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a relationship between 2 entities. A relationship has a source and target entities (somewhat obviously),
 * a name, id (multiple relationships of the same name can exist between the same source and target) and also a map of
 * properties.
 *
 * @author Lukas Krejci
 * @author Jirka Kremser
 * @since 1.0
 */
@XmlRootElement
public final class Relationship {

    @XmlAttribute
    private final String id;

    @XmlAttribute
    private final String name;

    private Map<String, Object> properties;
    private final Entity source;
    private final Entity target;

    /** JAXB support */
    @SuppressWarnings("unused")
    private Relationship() {
        this(null, null, null, null);
    }

    public Relationship(String id, String name, Entity source, Entity target) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.target = target;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new HashMap<>();
        }
        return properties;
    }

    public Entity getSource() {
        return source;
    }

    public Entity getTarget() {
        return target;
    }

    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder(getClass().getSimpleName());
        bld.append("[id='").append(id).append('\'');
        bld.append(", name='").append(name).append('\'');
        bld.append(", source=").append(source);
        bld.append(" --").append(name).append("--> ");
        bld.append(" target=").append(target);
        bld.append(']');
        return bld.toString();
    }
}
