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
 * @author Lukas Krejci
 * @since 1.0
 */
@XmlRootElement
public final class Metric extends EnvironmentalEntity {

    private final MetricDefinition definition;

    /** JAXB support */
    @SuppressWarnings("unused")
    private Metric() {
        definition = null;
    }

    public Metric(String tenantId, String environmentId, String id, MetricDefinition definition) {
        super(tenantId, environmentId, id);
        this.definition = definition;
    }

    public MetricDefinition getDefinition() {
        return definition;
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", definition=").append(definition);
    }

    @Override
    public <R, P> R accept(EntityVisitor<R, P> visitor, P parameter) {
        return visitor.visitMetric(this, parameter);
    }

    @XmlRootElement
    public static class Blueprint {
        @XmlAttribute
        private final String id;
        private final MetricDefinition definition;

        /** JAXB support */
        @SuppressWarnings("unused")
        private Blueprint() {
            this(null, null);
        }

        public Blueprint(MetricDefinition definition, String id) {
            this.definition = definition;
            this.id = id;
        }

        public MetricDefinition getDefinition() {
            return definition;
        }

        public String getId() {
            return id;
        }
    }
}
