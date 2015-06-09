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
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.ElementTypeVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.spi.CanonicalPath;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

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

        QueryFragmentTree existenceCheck = context.hop().filter().with(id(id)).get();

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

        return ElementTypeVisitor.accept(context.entityClass,
                new ElementTypeVisitor<QueryFragmentTree, QueryFragmentTree.Builder>() {
                    @Override
                    public QueryFragmentTree visitTenant(QueryFragmentTree.Builder bld) {
                        return bld.with(PathFragment.from(type(Tenant.class), id(id))).build();
                    }

                    @Override
                    public QueryFragmentTree visitEnvironment(QueryFragmentTree.Builder bld) {
                        return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                by(contains), type(Environment.class), id(id))).build();
                    }

                    @Override
                    public QueryFragmentTree visitFeed(QueryFragmentTree.Builder bld) {
                        return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                by(contains), type(Environment.class), id(parentPath.path.getEnvironmentId()),
                                by(contains), type(Feed.class), id(id))).build();
                    }

                    @Override
                    public QueryFragmentTree visitMetric(QueryFragmentTree.Builder bld) {
                        if (parentPath.path.getFeedId() == null) {
                            return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                    by(contains), type(Environment.class), id(parentPath.path.getEnvironmentId()),
                                    by(contains), type(Metric.class), id(id))).build();
                        } else {
                            return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                    by(contains), type(Environment.class), id(parentPath.path.getEnvironmentId()),
                                    by(contains), type(Feed.class), id(parentPath.path.getFeedId()),
                                    by(contains), type(Metric.class), id(id))).build();
                        }
                    }

                    @Override
                    public QueryFragmentTree visitMetricType(QueryFragmentTree.Builder bld) {
                        return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                by(contains), type(MetricType.class), id(id))).build();
                    }

                    @Override
                    public QueryFragmentTree visitResource(QueryFragmentTree.Builder bld) {
                        if (parentPath.path.getFeedId() == null) {
                            return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                    by(contains), type(Environment.class), id(parentPath.path.getEnvironmentId()),
                                    by(contains), type(Resource.class), id(id))).build();
                        } else {
                            return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                    by(contains), type(Environment.class), id(parentPath.path.getEnvironmentId()),
                                    by(contains), type(Feed.class), id(parentPath.path.getFeedId()),
                                    by(contains), type(Resource.class), id(id))).build();
                        }
                    }

                    @Override
                    public QueryFragmentTree visitResourceType(QueryFragmentTree.Builder bld) {
                        return bld.with(PathFragment.from(type(Tenant.class), id(parentPath.path.getTenantId()),
                                by(contains), type(ResourceType.class), id(id))).build();
                    }

                    @Override
                    public QueryFragmentTree visitUnknown(QueryFragmentTree.Builder bld) {
                        return null;
                    }

                    @Override
                    public QueryFragmentTree visitRelationship(QueryFragmentTree.Builder bld) {
                        return null;
                    }
                }, new QueryFragmentTree.Builder());
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
            context.backend.getTransitiveClosureOver(toDelete, contains.name(), outgoing).forEachRemaining((e) -> {
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
                            definingEntity + ", which it (indirectly) contains, acts as a definition for some " +
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
            public CanonicalPathAndEntity<BE> visitTenant(CanonicalPath.Builder builder) {
                return new CanonicalPathAndEntity<>(null, builder.build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitEnvironment(CanonicalPath.Builder builder) {
                BE tenant = getParentOfType(Tenant.class, true);
                return new CanonicalPathAndEntity<>(tenant, builder.withTenantId(context.backend.extractId(tenant))
                        .build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitFeed(CanonicalPath.Builder builder) {
                BE e = getParentOfType(Environment.class, true);
                Environment env = context.backend.convert(e, Environment.class);
                return new CanonicalPathAndEntity<>(e, builder.withTenantId(env.getTenantId())
                        .withEnvironmentId(env.getId()).build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitMetric(CanonicalPath.Builder builder) {
                BE env = getParentOfType(Environment.class, false);
                if (env != null) {
                    //feedless metric
                    Environment e = context.backend.convert(env, Environment.class);
                    return new CanonicalPathAndEntity<>(env, builder.withTenantId(e.getTenantId())
                            .withEnvironmentId(e.getId()).build());
                } else {
                    //metric under a feed
                    BE feed = getParentOfType(Feed.class, true);
                    Feed f = context.backend.convert(feed, Feed.class);
                    return new CanonicalPathAndEntity<>(feed, builder.withTenantId(f.getTenantId())
                            .withEnvironmentId(f.getEnvironmentId()).withFeedId(f.getId()).build());
                }
            }

            @Override
            public CanonicalPathAndEntity<BE> visitMetricType(CanonicalPath.Builder builder) {
                BE tenant = getParentOfType(Tenant.class, false);
                return new CanonicalPathAndEntity<>(tenant, builder.withTenantId(context.backend.extractId(tenant))
                        .build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitResource(CanonicalPath.Builder builder) {
                BE env = getParentOfType(Environment.class, false);
                if (env != null) {
                    //feedless resource
                    Environment e = context.backend.convert(env, Environment.class);
                    return new CanonicalPathAndEntity<>(env, builder.withTenantId(e.getTenantId())
                            .withEnvironmentId(e.getId()).build());
                } else {
                    //resource under a feed
                    BE feed = getParentOfType(Feed.class, true);
                    Feed f = context.backend.convert(feed, Feed.class);
                    return new CanonicalPathAndEntity<>(feed, builder.withTenantId(f.getTenantId())
                            .withEnvironmentId(f.getEnvironmentId()).withFeedId(f.getId()).build());
                }
            }

            @Override
            public CanonicalPathAndEntity<BE> visitResourceType(CanonicalPath.Builder builder) {
                BE tenant = getParentOfType(Tenant.class, false);
                return new CanonicalPathAndEntity<>(tenant, builder.withTenantId(context.backend.extractId(tenant))
                        .build());
            }

            @Override
            public CanonicalPathAndEntity<BE> visitUnknown(CanonicalPath.Builder builder) {
                throw new IllegalArgumentException("Unknown entity type: " + context.entityClass);
            }

            @Override
            public CanonicalPathAndEntity<BE> visitRelationship(CanonicalPath.Builder builder) {
                throw new IllegalArgumentException("Relationship cannot act as a parent of any other entity");
            }

            private BE getParentOfType(Class<? extends Entity<?, ?>> type, boolean throwException) {
                QueryFragmentTree query = context.sourcePath.extend().filter().with(type(type)).get();

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
        //sourcePath is "path to the parent"
        //selectCandidates - is the elements possibly matched by this mutator
        //we're given the id to select from these
        QueryFragmentTree query = context.sourcePath.extend().path().with(context.selectCandidates).with(id(id)).get();

        Page<BE> result = context.backend.query(query, Pager.single());

        if (result.isEmpty()) {
            throw new EntityNotFoundException(context.entityClass, QueryFragmentTree.filters(query));
        }

        return result.get(0);
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
