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

/**
 * Simple definition of a Metric for Inventory purposes
 *
 * @author Heiko W. Rupp
 */
@XmlRootElement
public final class MetricDefinition extends OwnedEntity {

    @XmlAttribute
    private final MetricUnit unit;

    /** JAXB support */
    @SuppressWarnings("unused")
    private MetricDefinition() {
        unit = null;
    }

    public MetricDefinition(String tenantId, String id) {
        super(tenantId, id);
        this.unit = MetricUnit.NONE;
    }

    public MetricDefinition(String tenantId, String id, MetricUnit unit) {
        super(tenantId, id);
        this.unit = unit;
    }

    public MetricUnit getUnit() {
        return unit;
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitMetricDefinition(this, parameter);
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", unit=").append(unit);
    }

    @XmlRootElement
    public static final class Blueprint {
        @XmlAttribute
        private final String id;
        @XmlAttribute
        private final MetricUnit unit;

        /** JAXB support */
        @SuppressWarnings("unused")
        private Blueprint() {
            id = null;
            unit = null;
        }

        public Blueprint(String id, MetricUnit unit) {
            this.id = id;
            this.unit = unit;
        }

        public String getId() {
            return id;
        }

        public MetricUnit getUnit() {
            return unit;
        }
    }
}
