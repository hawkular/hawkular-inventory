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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * Metric type defines metadata of metrics of the same type. Metric types are owned by
 * {@link ResourceType resource type}s in the same way as {@link Metric metric}s are owned by {@link Resource resource}s
 * (i.e. multiple resource types can "own" a single metric type).
 *
 * @author Heiko W. Rupp
 * @author Lukas Krejci
 */
@XmlRootElement
public final class MetricType extends Entity<MetricType.Blueprint, MetricType.Update> {

    @XmlAttribute
    private final MetricUnit unit;

    @XmlAttribute
    private final MetricDataType type;

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private MetricType() {
        unit = null;
        type = null;
    }

    public MetricType(CanonicalPath path) {
        this(path, MetricUnit.NONE, MetricDataType.GAUGE, null);
    }

    public MetricType(CanonicalPath path, MetricUnit unit, MetricDataType type) {
        this(path, unit, type, null);
    }

    public MetricType(String name, CanonicalPath path, MetricUnit unit, MetricDataType type) {
        this(name, path, unit, type, null);
    }

    public MetricType(CanonicalPath path, MetricUnit unit, MetricDataType type, Map<String, Object> properties) {
        super(path, properties);
        if (type == null) {
            throw new IllegalArgumentException("metricDataType == null");
        }
        this.unit = unit;
        this.type = type;
    }

    public MetricType(String name, CanonicalPath path, MetricUnit unit, MetricDataType type,
                      Map<String, Object> properties) {
        super(name, path, properties);
        this.type = type;
        this.unit = unit;
    }

    public MetricUnit getUnit() {
        return unit;
    }

    public MetricDataType getType() {
        return type;
    }

    @Override
    public Updater<Update, MetricType> update() {
        return new Updater<>((u) -> new MetricType(u.getName(), getPath(), valueOrDefault(u.unit, this.unit), type,
                u.getProperties()));
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
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
     * {@link org.hawkular.inventory.api.WriteInterface#create(org.hawkular.inventory.api.model.Blueprint)} method is
     * called.
     */
    @XmlRootElement
    public static final class Blueprint extends Entity.Blueprint {
        @XmlAttribute
        private final MetricUnit unit;
        private final MetricDataType type;

        public static Builder builder(MetricDataType type) {
            return new Builder(type);
        }

        /**
         * JAXB support
         */
        @SuppressWarnings("unused")
        private Blueprint() {
            unit = null;
            type = null;
        }

        public Blueprint(String id, MetricUnit unit, MetricDataType type) {
            this(id, unit, type, Collections.emptyMap());
        }

        public Blueprint(String id, MetricUnit unit, MetricDataType type, Map<String, Object> properties) {
            super(id, properties);
            this.unit = unit == null ? MetricUnit.NONE : unit;
            this.type = type;
        }

        public Blueprint(String id, MetricUnit unit, MetricDataType type,
                         Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, properties, outgoing, incoming);
            this.type = type;
            this.unit = unit;
        }

        public Blueprint(String id, String name, MetricUnit unit, MetricDataType type, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, name, properties, outgoing, incoming);
            this.type = type;
            this.unit = unit;
        }

        public MetricUnit getUnit() {
            return unit;
        }

        public MetricDataType getType() {
            return type;
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitMetricType(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            private MetricUnit unit;
            private MetricDataType type;


            public Builder(MetricDataType type) {
                this.type = type;
            }

            public Builder withUnit(MetricUnit unit) {
                this.unit = unit;
                return this;
            }

            public Builder withType(MetricDataType type) {
                this.type = type;
                return this;
            }

            @Override
            public Blueprint build() {
                return new Blueprint(id, name, unit, type, properties, outgoing, incoming);
            }
        }
    }

    public static final class Update extends Entity.Update {
        private final MetricUnit unit;

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Update() {
            this(null, null);
        }

        public Update(Map<String, Object> properties, MetricUnit unit) {
            super(null, properties);
            this.unit = unit;
        }

        public Update(String name, Map<String, Object> properties, MetricUnit unit) {
            super(name, properties);
            this.unit = unit;
        }

        public MetricUnit getUnit() {
            return unit;
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitMetricType(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {
            private MetricUnit unit;

            public Builder withUnit(MetricUnit unit) {
                this.unit = unit;
                return this;
            }

            @Override
            public Update build() {
                return new Update(name, properties, unit);
            }
        }
    }
}
