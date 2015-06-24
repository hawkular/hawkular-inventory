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
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

import java.util.function.Function;

import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.base.EntityAndPendingNotifications.Notification;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
final class Util {

    private Util() {

    }

    public static <R> R runInTransaction(InventoryBackend<?> backend, boolean readOnly,
            Function<InventoryBackend.Transaction, R> payload) {

        InventoryBackend.Transaction t = backend.startTransaction(!readOnly);
        try {
            R ret = payload.apply(t);
            if (readOnly) {
                backend.commit(t);
            }

            return ret;
        } catch (Throwable e) {
            backend.rollback(t);
            throw e;
        }
    }

    public static <BE> BE getSingle(InventoryBackend<BE> backend, Query query,
            Class<? extends Entity<?, ?>> entityType) {
        Page<BE> results = backend.query(query, Pager.single());

        if (results.isEmpty()) {
            throw new EntityNotFoundException(entityType, Query.filters(query));
        }

        return results.get(0);
    }

    public static <BE> EntityAndPendingNotifications<Relationship> createAssociation(InventoryBackend<BE> backend,
            Query sourceQuery, Class<? extends Entity<?, ?>> sourceType, String relationship, BE target) {

        return runInTransaction(backend, false, (t) -> {
            BE source = getSingle(backend, sourceQuery, sourceType);

            if (backend.hasRelationship(source, target, relationship)) {
                throw new RelationAlreadyExistsException(relationship, Query.filters(sourceQuery));
            }

            RelationshipRules.checkCreate(backend, source, Relationships.Direction.outgoing, relationship, target);
            BE relationshipObject = backend.relate(source, target, relationship, null);

            backend.commit(t);

            Relationship ret = backend.convert(relationshipObject, Relationship.class);

            return new EntityAndPendingNotifications<>(ret, new Notification<>(ret, ret, created()));
        });
    }

    public static <BE> EntityAndPendingNotifications<Relationship> deleteAssociation(InventoryBackend<BE> backend,
            Query sourceQuery, Class<? extends Entity<?, ?>> sourceType, String relationship, BE target) {

        return runInTransaction(backend, false, (t) -> {
            BE source = getSingle(backend, sourceQuery, sourceType);

            BE relationshipObject;

            try {
                relationshipObject = backend.getRelationship(source, target, relationship);
            } catch (ElementNotFoundException e) {
                throw new RelationNotFoundException(sourceType, relationship, Query.filters(sourceQuery),
                        null, null);
            }

            RelationshipRules.checkDelete(backend, source, Relationships.Direction.outgoing, relationship, target);

            Relationship ret = backend.convert(relationshipObject, Relationship.class);

            backend.delete(relationshipObject);

            backend.commit(t);

            return new EntityAndPendingNotifications<>(ret, new Notification<>(ret, ret, deleted()));
        });
    }

    public static <BE> Relationship getAssociation(InventoryBackend<BE> backend, Query sourceQuery,
            Class<? extends Entity<?, ?>> sourceType, Query targetQuery, Class<? extends Entity<?, ?>> targetType,
            String rel) {

        return runInTransaction(backend, true, (t) -> {
            BE source = getSingle(backend, sourceQuery, sourceType);
            BE target = getSingle(backend, targetQuery, targetType);

            BE relationship;
            try {
                relationship = backend.getRelationship(source, target, rel);
            } catch (ElementNotFoundException e) {
                throw new RelationNotFoundException(sourceType, rel, Query.filters(sourceQuery),
                        null, null);
            }

            return backend.convert(relationship, Relationship.class);
        });
    }

    @SuppressWarnings("unchecked")
    public static Query queryTo(TraversalContext context, Path path) {
        if (path instanceof CanonicalPath) {
            return Query.to((CanonicalPath) path);
        }

        Query.SymmetricExtender extender = context.sourcePath.extend().path();

        for (Path.Segment s : path.getPath()) {
            if (RelativePath.Up.class.equals(s.getElementType())) {
                extender.with(Related.asTargetBy(contains));
            } else {
                extender.with(Related.by(contains), With.type((Class<? extends Entity<?, ?>>) s.getElementType()),
                        With.id(s.getElementId()));
            }
        }

        return extender.get();
    }

}
