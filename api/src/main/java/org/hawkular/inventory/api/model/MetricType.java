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

import com.google.gson.annotations.Expose;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.Map;

/**
 * Metric type defines metadata of metrics of the same type. Metric types are owned by
 * {@link ResourceType resource type}s in the same way as {@link Metric metric}s are owned by {@link Resource resource}s
 * (i.e. multiple resource types can "own" a single metric type).
 *
 * @author Heiko W. Rupp
 * @author Lukas Krejci
 */
@XmlRootElement
public final class MetricType extends OwnedEntity {

    @XmlAttribute
    @Expose
    private final MetricUnit unit;

    /** JAXB support */
    @SuppressWarnings("unused")
    private MetricType() {
        unit = null;
    }

    public MetricType(String tenantId, String id) {
        super(tenantId, id);
        this.unit = MetricUnit.NONE;
    }

    public MetricType(String tenantId, String id, MetricUnit unit) {
        super(tenantId, id);
        this.unit = unit;
    }

    public MetricUnit getUnit() {
        return unit;
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitMetricType(this, parameter);
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", unit=").append(unit);
    }

    /**
     * Data required to create a new metric type.
     *
     * <p>Note that tenantId, etc., are not needed here because they are provided by the context in which the
     * {@link org.hawkular.inventory.api.WriteInterface#create(Entity.Blueprint)} method is called.
     */
    @XmlRootElement
    public static final class Blueprint extends Entity.Blueprint {
        @XmlAttribute
        private final MetricUnit unit;

        public static Builder builder() {
            return new Builder();
        }

        /** JAXB support */
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null, null);
        }

        public Blueprint(String id, MetricUnit unit) {
            this(id, unit, Collections.emptyMap());
        }

        public Blueprint(String id, MetricUnit unit, Map<String, Object> properties) {
            super(id, properties);
            this.unit = unit;
        }

        public MetricUnit getUnit() {
            return unit;
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            private MetricUnit unit;

            public Builder withUnit(MetricUnit unit) {
                this.unit = unit;
                return this;
            }

            @Override
            public Blueprint build() {
                return new Blueprint(id, unit, properties);
            }
        }
    }
}
