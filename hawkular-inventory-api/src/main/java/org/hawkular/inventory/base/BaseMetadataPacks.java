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

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.With.id;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.MetadataPacks;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.IdentityHash;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public final class BaseMetadataPacks {

    private BaseMetadataPacks() {

    }

    public static class ReadWrite<BE> extends Mutator<BE, MetadataPack, MetadataPack.Blueprint, MetadataPack.Update,
            String> implements MetadataPacks.ReadWrite {

        public ReadWrite(TraversalContext<BE, MetadataPack> context) {
            super(context);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected String getProposedId(Transaction<BE> tx, MetadataPack.Blueprint blueprint) {
            Iterator<? extends Entity<? extends Entity.Blueprint, ?>> members = blueprint.getMembers().stream()
                    .map((p) -> {
                        SegmentType type = p.getSegment().getElementType();
                        Class<?> cls = Entity.entityTypeFromSegmentType(type);
                        try {
                            BE e = tx.find(p);
                            return (Entity<? extends Entity.Blueprint, ?>) tx.convert(e, cls);
                        } catch (ElementNotFoundException ex) {
                            throw new EntityNotFoundException(cls, Query.filters(Query.to(p)));
                        }
                    }).iterator();

            return IdentityHash.of(members, context.inventory);
        }

        @Override
        protected EntityAndPendingNotifications<BE, MetadataPack>
        wireUpNewEntity(BE entity, MetadataPack.Blueprint blueprint, CanonicalPath parentPath, BE parent,
                        Transaction<BE> tx) {
            Set<Notification<?, ?>> newRels = new HashSet<>();

            blueprint.getMembers().forEach((p) -> {
                try {
                    BE member = tx.find(p);

                    BE rel = tx.relate(entity, member, incorporates.name(), null);

                    Relationship r = tx.convert(rel, Relationship.class);
                    newRels.add(new Notification<>(r, r, created()));
                } catch (ElementNotFoundException e) {
                    throw new EntityNotFoundException(p.getSegment().getElementType().getSimpleName(),
                            Query.filters(Query.to(p)));
                }
            });

            MetadataPack entityObject = tx.convert(entity, MetadataPack.class);

            return new EntityAndPendingNotifications<>(entity, entityObject, newRels);
        }

        @Override public MetadataPacks.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override public MetadataPacks.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override public MetadataPacks.Single create(MetadataPack.Blueprint blueprint)
                throws EntityAlreadyExistsException {

            blueprint.getMembers().forEach(p -> {
                if (p.ids().getFeedId() != null) {
                    throw new IllegalArgumentException("Only global types can be part of a metadata pack. No " +
                            "feed-local types are allowed but '" + p + "' encountered.");
                }
            });

            return new Single<>(context.toCreatedEntity(doCreate(blueprint)));
        }
    }

    public static class ReadContained<BE> extends Traversal<BE, MetadataPack> implements MetadataPacks.ReadContained {

        public ReadContained(TraversalContext<BE, MetadataPack> context) {
            super(context);
        }


        @Override public MetadataPacks.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override public MetadataPacks.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Traversal<BE, MetadataPack> implements MetadataPacks.Read {

        public Read(TraversalContext<BE, MetadataPack> context) {
            super(context);
        }

        @Override public MetadataPacks.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override public MetadataPacks.Single get(Path path) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(path));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, MetadataPack, MetadataPack.Update>
            implements MetadataPacks.Single {

        public Single(TraversalContext<BE, MetadataPack> context) {
            super(context);
        }

        @Override public ResourceTypes.Read resourceTypes() {
            return new BaseResourceTypes.Read<>(context.proceedTo(incorporates, ResourceType.class).get());
        }

        @Override public MetricTypes.Read metricTypes() {
            return new BaseMetricTypes.Read<>(context.proceedTo(incorporates, MetricType.class).get());
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, MetadataPack, MetadataPack.Update>
            implements MetadataPacks.Multiple {

        public Multiple(TraversalContext<BE, MetadataPack> context) {
            super(context);
        }

        @Override public ResourceTypes.Read resourceTypes() {
            return new BaseResourceTypes.Read<>(context.proceedTo(incorporates, ResourceType.class).get());
        }

        @Override public MetricTypes.Read metricTypes() {
            return new BaseMetricTypes.Read<>(context.proceedTo(incorporates, MetricType.class).get());
        }
    }
}
