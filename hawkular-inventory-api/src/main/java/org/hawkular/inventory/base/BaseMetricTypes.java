/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import static java.util.Collections.emptyList;

import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.With.id;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.ResourceType;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseMetricTypes {

    private BaseMetricTypes() {

    }

    public static class ReadWrite<BE>
            extends Mutator<BE, MetricType, MetricType.Blueprint, MetricType.Update, String>
            implements MetricTypes.ReadWrite {

        public ReadWrite(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Transaction<BE> tx, MetricType.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<BE, MetricType>
        wireUpNewEntity(BE entity, MetricType.Blueprint blueprint, CanonicalPath parentPath, BE parent,
                        Transaction<BE> tx) {
            tx.update(entity, MetricType.Update.builder().withUnit(blueprint.getUnit()).build());

            MetricType metricType = new MetricType(blueprint.getName(),
                    parentPath.extend(MetricType.class, tx.extractId(entity)).get(), null,
                    blueprint.getUnit(), blueprint.getType(), blueprint.getProperties(),
                    blueprint.getCollectionInterval());

            return new EntityAndPendingNotifications<>(entity, metricType, emptyList());
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public MetricTypes.Single create(MetricType.Blueprint blueprint) throws EntityAlreadyExistsException {
            if (blueprint.getType() == null ||
                blueprint.getUnit() == null ||
                blueprint.getCollectionInterval() == null) {

                String msg = getErrorMessage(blueprint);
                throw new IllegalArgumentException(msg);
            }

            return new BaseMetricTypes.Single<>(context.replacePath(doCreate(blueprint)));
        }

        @Override
        protected void preDelete(String s, BE entityRepresentation, Transaction<BE> transaction) {
            preDelete(entityRepresentation, transaction);
        }

        @Override
        protected void preUpdate(String s, BE entityRepresentation, MetricType.Update update,
                                 Transaction<BE> transaction) {
            preUpdate(context, entityRepresentation, update, transaction);
        }

        @Override protected void postUpdate(BE entityRepresentation, Transaction<BE> transaction) {
            postUpdate(context, entityRepresentation, transaction);
        }

        private static <BE> void preDelete(BE deletedEntity, Transaction<BE> tx) {
            if (isInMetadataPack(deletedEntity, tx)) {
                throw new IllegalArgumentException("Cannot delete a metric type that is a part of metadata pack.");
            }
        }

        private static <BE> void preUpdate(TraversalContext<BE, ?> context, BE entity, MetricType.Update update,
                                           Transaction<BE> tx) {
            MetricType mt = tx.convert(entity, MetricType.class);
            if (mt.getUnit() == update.getUnit()) {
                //k, this is the only updatable thing that influences metadata packs, so if it is equal, we're ok.
                return;
            }

            if (isInMetadataPack(entity, tx)) {
                throw new IllegalArgumentException("Cannot update a metric type that is a part of metadata pack.");
            }
        }

        private static <BE> void postUpdate(TraversalContext<BE, ?> context, BE entity,
                                            Transaction<BE> tx) {
        }

        private static <BE> boolean isInMetadataPack(BE metricType, Transaction<BE> tx) {
            return tx.traverseToSingle(metricType, Query.path().with(Related.asTargetBy(incorporates),
                    With.type(MetadataPack.class)).get()) != null;

        }

        private String getErrorMessage(MetricType.Blueprint blueprint) {
            String msg;
            if (blueprint.getCollectionInterval() == null) {
                msg = "Interval (\"collectionInterval\" in JSON)";
            } else if (blueprint.getType() == null) {
                msg = "Data type (\"type\" in JSON)";
            } else {
                msg = "Metric unit (\"unit\" in JSON)";
            }

            return msg + " is null";
        }
    }

    public static class ReadContained<BE> extends Fetcher<BE, MetricType, MetricType.Update>
            implements MetricTypes.ReadContained {

        public ReadContained(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Fetcher<BE, MetricType, MetricType.Update> implements MetricTypes.Read {

        public Read(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(Path id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceedTo(id));
        }
    }

    public static class ReadAssociate<BE> extends Associator<BE, MetricType>
            implements MetricTypes.ReadAssociate {

        public ReadAssociate(TraversalContext<BE, MetricType> context) {
            super(context, incorporates, ResourceType.class);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new BaseMetricTypes.Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public MetricTypes.Single get(Path id) throws EntityNotFoundException {
            return new BaseMetricTypes.Single<>(context.proceedTo(id));
        }
    }

    public static class Single<BE> extends SingleIdentityHashedFetcher<BE, MetricType, MetricType.Blueprint,
            MetricType.Update> implements MetricTypes.Single {

        public Single(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedTo(defines, Metric.class).get());
        }

        @Override
        public MetricTypes.Read identical() {
            return new Read<>(context.proceed().hop(With.sameIdentityHash()).get());
        }

        @Override
        protected void preDelete(BE deletedEntity, Transaction<BE> transaction) {
            ReadWrite.preDelete(deletedEntity, transaction);
        }

        @Override
        protected void preUpdate(BE updatedEntity, MetricType.Update update, Transaction<BE> transaction) {
            ReadWrite.preUpdate(context, updatedEntity, update, transaction);
        }

        @Override protected void postUpdate(BE updatedEntity, Transaction<BE> transaction) {
            ReadWrite.postUpdate(context, updatedEntity, transaction);
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, MetricType, MetricType.Update>
            implements MetricTypes.Multiple {

        public Multiple(TraversalContext<BE, MetricType> context) {
            super(context);
        }

        @Override
        public Metrics.Read metrics() {
            return new BaseMetrics.Read<>(context.proceedTo(defines, Metric.class).get());
        }

        @Override public MetricTypes.Read identical() {
            return new Read<>(context.proceed().hop(With.sameIdentityHash()).get());
        }
    }

}
