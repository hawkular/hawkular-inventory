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
import java.util.HashSet;
import java.util.Set;

/**
 * A resource.
 *
 * @author Heiko Rupp
 */
@XmlRootElement
public final class Resource extends EnvironmentalEntity {

    private final ResourceType type;
    private Set<Metric> metrics;

    /** JAXB support */
    @SuppressWarnings("unused")
    private Resource() {
        type = null;
    }

    public Resource(String tenantId, String environmentId, String id, ResourceType type) {
        super(tenantId, environmentId, id);
        this.type = type;
    }

    public ResourceType getType() {
        return type;
    }

    public Set<Metric> getMetrics() {
        if (metrics == null) {
            metrics = new HashSet<>();
        }
        return metrics;
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitResource(this, parameter);
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", type=").append(type);
    }

    @XmlRootElement
    public static final class Blueprint {
        @XmlAttribute
        private final String id;
        private final ResourceType type;

        /** JAXB support */
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null);
        }

        public Blueprint(String id, ResourceType type) {
            this.id = id;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public ResourceType getType() {
            return type;
        }
    }
}
