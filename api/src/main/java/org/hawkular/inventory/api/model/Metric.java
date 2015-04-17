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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.Map;

/**
 * Metric describes a single metric that is sent out from a feed. Each metric has a unique ID and a type. Metrics live
 * in an environment and can be "incorporated" by {@link Resource resources} (surprisingly, many resources can
 * incorporate a single metric).
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
@XmlRootElement
public final class Metric extends FeedBasedEntity<Metric.Blueprint, Metric.Update> {

    @Expose
    private final MetricType type;

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private Metric() {
        type = null;
    }

    public Metric(CanonicalPath path, MetricType type) {
        this(path, type, null);
    }

    @JsonCreator
    public Metric(@JsonProperty("path") CanonicalPath path, @JsonProperty("type") MetricType type,
            @JsonProperty("properties") Map<String, Object> properties) {

        super(path, properties);
        this.type = type;
    }

    @Override
    public Updater<Update, Metric> update() {
        return new Updater<>((u) -> new Metric(getPath(), getType(), u.getProperties()));
    }

    public MetricType getType() {
        return type;
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", definition=").append(type);
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitMetric(this, parameter);
    }

    /**
     * Data required to create a new metric.
     *
     * <p>Note that tenantId, etc., are not needed here because they are provided by the context in which the
     * {@link org.hawkular.inventory.api.WriteInterface#create(Entity.Blueprint)} method is called.
     */
    @XmlRootElement
    public static final class Blueprint extends Entity.Blueprint {
        @XmlAttribute
        private final String metricTypeId;

        public static Builder builder() {
            return new Builder();
        }

        /**
         * JAXB support
         */
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null, null);
        }

        public Blueprint(String metricTypeId, String id) {
            this(metricTypeId, id, Collections.emptyMap());
        }

        public Blueprint(String metricTypeId, String id, Map<String, Object> properties) {
            super(id, properties);
            this.metricTypeId = metricTypeId;
        }

        public String getMetricTypeId() {
            return metricTypeId;
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitMetric(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            private String metricTypeId;

            public Builder withMetricTypeId(String metricTypeId) {
                this.metricTypeId = metricTypeId;
                return this;
            }

            @Override
            public Blueprint build() {
                return new Blueprint(metricTypeId, id, properties);
            }
        }
    }

    public static final class Update extends AbstractElement.Update {
        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Update() {
            this(null);
        }

        public Update(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitMetric(this, parameter);
        }

        public static final class Builder extends AbstractElement.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(properties);
            }
        }
    }
}
