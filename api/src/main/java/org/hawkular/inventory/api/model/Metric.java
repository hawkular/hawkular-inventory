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
 * Metric describes a single metric that is sent out from a feed. Each metric has a unique ID and a type. Metrics live
 * in an environment and can be "owned" by {@link Resource resources} (surprisingly, many resources can own a single
 * metric).
 *
 * @author Lukas Krejci
 * @since 1.0
 */
@XmlRootElement
public final class Metric extends EnvironmentalEntity {

    private final MetricType type;

    /** JAXB support */
    @SuppressWarnings("unused")
    private Metric() {
        type = null;
    }

    public Metric(String tenantId, String environmentId, String id, MetricType type) {
        super(tenantId, environmentId, id);
        this.type = type;
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
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitMetric(this, parameter);
    }

    /**
     * Data required to create a new metric.
     *
     * <p>Note that tenantId, etc., are not needed here because they are provided by the context in which the
     * {@link org.hawkular.inventory.api.WriteInterface#create(Object)} method is called.
     */
    @XmlRootElement
    public static class Blueprint {
        @XmlAttribute
        private final String id;
        private final MetricType type;

        /** JAXB support */
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null);
        }

        public Blueprint(MetricType type, String id) {
            this.type = type;
            this.id = id;
        }

        public MetricType getType() {
            return type;
        }

        public String getId() {
            return id;
        }
    }
}
