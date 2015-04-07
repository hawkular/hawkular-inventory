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
package org.hawkular.inventory.impl.tinkerpop;

import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Metric;

import java.util.Set;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class MetricBrowser extends AbstractBrowser<Metric> {

    public static Metrics.Single single(InventoryContext context, FilterApplicator... path) {
        MetricBrowser b = new MetricBrowser(context, path);

        return new Metrics.Single() {

            @Override
            public Metric entity() {
                return b.entity();
            }

            @Override
            public Relationships.ReadWrite relationships() {
                return b.relationships();
            }

            @Override
            public Relationships.ReadWrite relationships(Relationships.Direction direction) {
                return b.relationships(direction);
            }
        };
    }

    public static Metrics.Multiple multiple(InventoryContext context, FilterApplicator... path) {
        MetricBrowser b = new MetricBrowser(context, path);

        return new Metrics.Multiple() {

            @Override
            public Set<Metric> entities() {
                return b.entities();
            }

            @Override
            public Relationships.Read relationships() {
                return b.relationships();
            }

            @Override
            public Relationships.Read relationships(Relationships.Direction direction) {
                return b.relationships(direction);
            }
        };
    }

    MetricBrowser(InventoryContext context, FilterApplicator... path) {
        super(context, Metric.class, path);
    }
}
