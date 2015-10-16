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
package org.hawkular.inventory.base;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.With.id;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.base.spi.ElementNotFoundException;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseMetrics {

    private BaseMetrics() {

    }

    public static class ReadWrite<BE> extends Mutator<BE, Metric, Metric.Blueprint,
            Metric.Update, String>
            implements Metrics.ReadWrite {

        public ReadWrite(TraversalContext<BE, Metric> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Metric.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<Metric> wireUpNewEntity(BE entity, Metric.Blueprint blueprint,
                CanonicalPath parentPath, BE parent) {

            BE metricTypeObject;

            try {
                CanonicalPath tenant = CanonicalPath.of().tenant(parentPath.ids().getTenantId()).get();
                CanonicalPath metricTypePath = Util.canonicalize(blueprint.getMetricTypePath(), tenant, parentPath,
                        MetricType.class);
                metricTypeObject = context.backend.find(metricTypePath);

            } catch (ElementNotFoundException e) {
                throw new IllegalArgumentException("A metric type with id '" + blueprint.getMetricTypePath() +
                        "' not found in tenant '" + parentPath.getRoot().getSegment().getElementId() + "'.");
            }

            //specifically do NOT check relationship rules, here because defines cannot be created "manually".
            //here we "know what we are doing" and need to create the defines relationship to capture the
            //contract of the metric.
            BE r = context.backend.relate(metricTypeObject, entity, defines.name(), null);

            CanonicalPath entityPath = context.backend.extractCanonicalPath(entity);

            MetricType metricType = context.backend.convert(metricTypeObject, MetricType.class);

            Metric ret = new Metric(blueprint.getName(), parentPath.extend(Metric.class,
                    context.backend.extractId(entity)).get(), metricType, blueprint.getProperties());

            Relationship rel = new Relationship(context.backend.extractId(r), defines.name(), parentPath, entityPath);

            return new EntityAndPendingNotifications<>(ret, new Notification<>(rel, rel, created()));
        }

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Metrics.Single create(Metric.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class ReadContained<BE> extends Traversal<BE, Metric> implements Metrics.ReadContained {

        public ReadContained(TraversalContext<BE, Metric> context) {
            super(context);
        }

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Traversal<BE, Metric> implements Metrics.Read {

        public Read(TraversalContext<BE, Metric> context) {
            super(context);
        }

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Metrics.Single get(Path id) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(id));
        }
    }

    public static class ReadAssociate<BE> extends Associator<BE, Metric> implements Metrics.ReadAssociate {

        public ReadAssociate(TraversalContext<BE, Metric> context) {
            super(context);
        }

        @Override
        public Relationship associate(
                Path id) throws EntityNotFoundException, RelationAlreadyExistsException {
            BE metric = Util.find(context, id);

            return createAssociation(Resource.class, incorporates, metric);
        }

        @Override
        public Relationship disassociate(Path id) throws EntityNotFoundException {
            BE metric = Util.find(context, id);

            return deleteAssociation(Resource.class, incorporates, metric);
        }

        @Override
        public Relationship associationWith(Path path) throws RelationNotFoundException {
            return getAssociation(Resource.class, path, incorporates);
        }

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Metrics.Single get(Path id) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(id));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, Metric, Metric.Update> implements Metrics.Single {

        public Single(TraversalContext<BE, Metric> context) {
            super(context);
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Metric, Metric.Update>
            implements Metrics.Multiple {

        public Multiple(TraversalContext<BE, Metric> context) {
            super(context);
        }
    }
}
