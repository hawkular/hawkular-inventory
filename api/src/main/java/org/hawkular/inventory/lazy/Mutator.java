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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.ElementTypeVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.spi.CanonicalPath;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
abstract class Mutator<BE, E extends Entity<Blueprint, Update>, Blueprint extends Entity.Blueprint,
        Update extends AbstractElement.Update> extends Traversal<BE, E> {

    protected Mutator(TraversalContext<BE, E> context) {
        super(context);
    }

    protected abstract String getProposedId(Blueprint entity);

    protected final QueryFragmentTree doCreate(Blueprint blueprint) {
        String id = getProposedId(blueprint);

        QueryFragmentTree existenceCheck = context.proceedByPath().where(With.id(id)).get().sourcePath;

        Page<BE> results = context.backend.query(existenceCheck, Pager.single());

        if (!results.isEmpty()) {
            throw new EntityAlreadyExistsException(id, QueryFragmentTree.filters(existenceCheck));
        }

        CanonicalPathAndEntity<BE> parentPath = getCanonicalParentPath();

        try {
            BE entity = context.backend.persist(id, blueprint);

            if (parentPath.path.isDefined()) {
                BE parent = parentPath.entity;
                context.backend.relate(parent, entity, contains.name(), Collections.emptyMap());
            }

            wireUpNewEntity(entity, blueprint, parentPath.path, parentPath.entity);

            context.backend.commit();
        } catch (Throwable t) {
            context.backend.rollback();
            throw t;
        }

        return ElementTypeVisitor.accept(context.entityClass, new ElementTypeVisitor<QueryFragmentTree.Builder, Void>() {
            @Override
            public QueryFragmentTree.Builder visitTenant(Void parameter) {
                return new QueryFragmentTree.Builder().with(PathFragment.from(With.type(Tenant.class),
                        With.id(id)));
            }

            @Override
            public QueryFragmentTree.Builder visitEnvironment(Void parameter) {
                return new QueryFragmentTree.Builder().with(PathFragment.from(With.type(Tenant.class),
                        With.id(parentPath.path.getTenantId()), Related.by(contains), With.type(Environment.class),
                        With.id(id)));
            }

            @Override
            public QueryFragmentTree.Builder visitFeed(Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitMetric(Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitMetricType(Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitResource(Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitResourceType(Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitUnknown(Void parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitRelationship(Void parameter) {
                //TODO implement
                return null;
            }
        }, null).build();
    }

    public final void update(String id, Update update) throws EntityNotFoundException {
        BE toUpdate = checkExists(id);

        try {
            context.backend.update(toUpdate, update);
            context.backend.commit();
        } catch (Throwable e) {
            context.backend.rollback();
            throw e;
        }
    }

    public final void delete(String id) throws EntityNotFoundException {
        BE toDelete = checkExists(id);

        Set<BE> verticesToDeleteThatDefineSomething = new HashSet<>();

        try {
            context.backend.getTransitiveClosureOver(toDelete, contains.name()).forEachRemaining((e) -> {
                if (context.backend.hasRelationship(e, outgoing, defines.name())) {
                    verticesToDeleteThatDefineSomething.add(e);
                } else {
                    context.backend.delete(e);
                }
            });

            if (context.backend.hasRelationship(toDelete, outgoing, defines.name())) {
                verticesToDeleteThatDefineSomething.add(toDelete);
            } else {
                context.backend.delete(toDelete);
            }

            for (BE e : verticesToDeleteThatDefineSomething) {
                if (context.backend.hasRelationship(e, outgoing, defines.name())) {
                    //we avoid the convert() function here because it assumes the containing entities of the passed in
                    //entity exist. This might not be true during the delete because the transitive closure "walks" the
                    //entities from the "top" down the containment chain and the entities are immediately deleted.
                    String rootId = context.backend.extractId(toDelete);
                    String definingId = context.backend.extractId(e);
                    String rootType = context.entityClass.getSimpleName();
                    String definingType = context.backend.getType(e).getSimpleName();

                    String rootEntity = "Entity[id=" + rootId + ", type=" + rootType + "]";
                    String definingEntity = "Entity[id=" + definingId + ", type=" + definingType + "]";

                    throw new IllegalArgumentException("Could not delete entity " + rootEntity + ". The entity " +
                            definingEntity + ", which it (indirectly) contains, acts as a definition for some" +
                            "entities that are not deleted along with it, which would leave them without a " +
                            "definition. This is illegal.");
                } else {
                    context.backend.delete(e);
                }
            }

            context.backend.commit();
        } catch (Exception e) {
            context.backend.rollback();
            throw e;
        }
    }

    protected CanonicalPathAndEntity<BE> getCanonicalParentPath() {
        return ElementTypeVisitor.accept(context.entityClass, new ElementTypeVisitor<CanonicalPathAndEntity<BE>,
                CanonicalPath.Builder>() {
            @Override
            public CanonicalPathAndEntity<BE> visitTenant(CanonicalPath.Builder parameter) {
                return new CanonicalPathAndEntity<>(null, null);
            }

            @Override
            public CanonicalPathAndEntity<BE> visitEnvironment(CanonicalPath.Builder parameter) {
                BE tenant = getParentOfType(Tenant.class, true);
                return new CanonicalPathAndEntity<>(tenant, parameter.withTenantId(context.backend.extractId(tenant))
                        .build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitFeed(CanonicalPath.Builder parameter) {
                BE e = getParentOfType(Environment.class, true);
                Environment env = context.backend.convert(e, Environment.class);
                return new CanonicalPathAndEntity<>(e, parameter.withTenantId(env.getTenantId())
                        .withEnvironmentId(env.getId()).build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitMetric(CanonicalPath.Builder parameter) {
                BE env = getParentOfType(Environment.class, true);
                if (env != null) {
                    //feedless metric
                    Environment e = context.backend.convert(env, Environment.class);
                    return new CanonicalPathAndEntity<>(env, parameter.withTenantId(e.getTenantId())
                            .withEnvironmentId(e.getId()).build());
                } else {
                    //metric under a feed
                    BE feed = getParentOfType(Feed.class, false);
                    Feed f = context.backend.convert(feed, Feed.class);
                    return new CanonicalPathAndEntity<>(feed, parameter.withTenantId(f.getTenantId())
                            .withEnvironmentId(f.getEnvironmentId()).withFeedId(f.getId()).build());
                }
            }

            @Override
            public CanonicalPathAndEntity<BE> visitMetricType(CanonicalPath.Builder parameter) {
                BE tenant = getParentOfType(Tenant.class, false);
                return new CanonicalPathAndEntity<>(tenant, parameter.withTenantId(context.backend.extractId(tenant))
                        .build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitResource(CanonicalPath.Builder parameter) {
                BE env = getParentOfType(Environment.class, true);
                if (env != null) {
                    //feedless resource
                    Environment e = context.backend.convert(env, Environment.class);
                    return new CanonicalPathAndEntity<>(env, parameter.withTenantId(e.getTenantId())
                            .withEnvironmentId(e.getId()).build());
                } else {
                    //metric under a rsource
                    BE feed = getParentOfType(Feed.class, false);
                    Feed f = context.backend.convert(feed, Feed.class);
                    return new CanonicalPathAndEntity<>(feed, parameter.withTenantId(f.getTenantId())
                            .withEnvironmentId(f.getEnvironmentId()).withFeedId(f.getId()).build());
                }
            }

            @Override
            public CanonicalPathAndEntity<BE> visitResourceType(CanonicalPath.Builder parameter) {
                BE tenant = getParentOfType(Tenant.class, false);
                return new CanonicalPathAndEntity<>(tenant, parameter.withTenantId(context.backend.extractId(tenant))
                        .build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitUnknown(CanonicalPath.Builder parameter) {
                throw new IllegalArgumentException("Unknown entity type: " + context.entityClass);
            }

            @Override
            public CanonicalPathAndEntity<BE> visitRelationship(CanonicalPath.Builder parameter) {
                throw new IllegalArgumentException("Relationship cannot act as a parent of any other entity");
            }

            private BE getParentOfType(Class<? extends Entity> type, boolean throwException) {
                QueryFragmentTree query = context.sourcePath.extend().withFilters(With.type(type)).get();

                Page<BE> parents = context.backend.query(query, Pager.single());

                if (parents.isEmpty()) {
                    if (throwException) {
                        throw new EntityNotFoundException(type, QueryFragmentTree.filters(query));
                    } else {
                        return null;
                    }
                }

                return parents.get(0);
            }
        }, CanonicalPath.builder());
    }

    protected abstract void wireUpNewEntity(BE entity, Blueprint blueprint, CanonicalPath parentPath, BE parent);

    private BE checkExists(String id) {
        QueryFragmentTree query = context.sourcePath.extend().withFilters(With.id(id)).get();

        Page<BE> toUpdate = context.backend.query(query, Pager.unlimited(Order.unspecified()));

        if (toUpdate.isEmpty()) {
            throw new EntityNotFoundException(context.entityClass, QueryFragmentTree.filters(query));
        }

        return toUpdate.get(0);
    }

    private static final class CanonicalPathAndEntity<BE> {
        final BE entity;
        final CanonicalPath path;

        public CanonicalPathAndEntity(BE entity, CanonicalPath path) {
            this.entity = entity;
            this.path = path;
        }
    }
}
