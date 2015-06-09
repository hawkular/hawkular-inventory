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
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.EntityTypeVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.spi.CanonicalPath;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

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

    protected abstract QueryFragmentTree initNewEntity(E entity, BE backendEntity);

    protected final QueryFragmentTree doCreate(Blueprint blueprint) {
        String id = getProposedId(blueprint);

        QueryFragmentTree existenceCheck = context.sourcePath.extend().withFilters(With.id(id)).get();

        Page<BE> results = context.backend.query(existenceCheck, Pager.single());

        if (!results.isEmpty()) {
            throw new EntityAlreadyExistsException(id, QueryFragmentTree.filters(existenceCheck));
        }

        CanonicalPath canonicalPath = getCanonicalPath(id, blueprint);

        try {
            BE entity = context.backend.persist(canonicalPath, blueprint);

            wireUpNewEntity(entity, blueprint, canonicalPath);

            context.backend.commit();
        } catch (Throwable t) {
            context.backend.rollback();
            throw t;
        }

        return EntityTypeVisitor.accept(context.entityClass, new EntityTypeVisitor<QueryFragmentTree.Builder, Object>() {
            @Override
            public QueryFragmentTree.Builder visitTenant(Object parameter) {
                return new QueryFragmentTree.Builder().with(PathFragment.from(With.type(Tenant.class),
                        With.id(canonicalPath.getTenantId())));
            }

            @Override
            public QueryFragmentTree.Builder visitEnvironment(Object parameter) {
                return new QueryFragmentTree.Builder().with(PathFragment.from(With.type(Tenant.class),
                        With.id(canonicalPath.getTenantId()), Related.by(contains), With.type(Environment.class),
                        With.id(canonicalPath.getEnvironmentId())));
            }

            @Override
            public QueryFragmentTree.Builder visitFeed(Object parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitMetric(Object parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitMetricType(Object parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitResource(Object parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitResourceType(Object parameter) {
                //TODO implement
                return null;
            }

            @Override
            public QueryFragmentTree.Builder visitUnknown(Object parameter) {
                //TODO implement
                return null;
            }
        }, null).build();
    }

    public final void update(String id, Update update) throws EntityNotFoundException {
        QueryFragmentTree query = context.sourcePath.extend().withFilters(With.id(id)).get();

        Page<BE> toUpdate = context.backend.query(query, Pager.unlimited(Order.unspecified()));

        if (toUpdate.isEmpty()) {
            throw new EntityNotFoundException(context.entityClass, QueryFragmentTree.filters(query));
        }

        try {
            context.backend.update(toUpdate.get(0), update);
            context.backend.commit();
        } catch (Throwable e) {
            context.backend.rollback();
            throw e;
        }
    }

    protected CanonicalPath getCanonicalPath(String realId, Blueprint blueprint) {
        return EntityTypeVisitor.accept(context.entityClass, new EntityTypeVisitor<CanonicalPath.Builder,
                CanonicalPath.Builder>() {
            @Override
            public CanonicalPath.Builder visitTenant(CanonicalPath.Builder parameter) {
                return parameter.withTenantId(realId);
            }

            @Override
            public CanonicalPath.Builder visitEnvironment(CanonicalPath.Builder parameter) {
                BE tenant = getParentOfType(Tenant.class, false);
                return parameter.withTenantId(context.backend.extractId(tenant)).withEnvironmentId(realId);
            }

            @Override
            public CanonicalPath.Builder visitFeed(CanonicalPath.Builder parameter) {
                Environment env = context.backend.convert(getParentOfType(Environment.class, false), Environment.class);
                return parameter.withTenantId(env.getTenantId()).withEnvironmentId(env.getId()).withFeedId(realId);
            }

            @Override
            public CanonicalPath.Builder visitMetric(CanonicalPath.Builder parameter) {
                BE env = getParentOfType(Environment.class, true);
                if (env != null) {
                    //feedless metric
                    Environment e = context.backend.convert(env, Environment.class);
                    return parameter.withTenantId(e.getTenantId()).withEnvironmentId(e.getId()).withMetricId(realId);
                } else {
                    //metric under a feed
                    BE feed = getParentOfType(Feed.class, false);
                    Feed f = context.backend.convert(feed, Feed.class);
                    return parameter.withTenantId(f.getTenantId()).withEnvironmentId(f.getEnvironmentId())
                            .withFeedId(f.getId()).withMetricId(realId);
                }
            }

            @Override
            public CanonicalPath.Builder visitMetricType(CanonicalPath.Builder parameter) {
                BE tenant = getParentOfType(Tenant.class, false);
                return parameter.withTenantId(context.backend.extractId(tenant)).withMetricTypeId(realId);
            }

            @Override
            public CanonicalPath.Builder visitResource(CanonicalPath.Builder parameter) {
                BE env = getParentOfType(Environment.class, true);
                if (env != null) {
                    //feedless metric
                    Environment e = context.backend.convert(env, Environment.class);
                    return parameter.withTenantId(e.getTenantId()).withEnvironmentId(e.getId()).withResourceId(realId);
                } else {
                    //metric under a feed
                    BE feed = getParentOfType(Feed.class, false);
                    Feed f = context.backend.convert(feed, Feed.class);
                    return parameter.withTenantId(f.getTenantId()).withEnvironmentId(f.getEnvironmentId())
                            .withFeedId(f.getId()).withResourceId(realId);
                }
            }

            @Override
            public CanonicalPath.Builder visitResourceType(CanonicalPath.Builder parameter) {
                BE tenant = getParentOfType(Tenant.class, false);
                return parameter.withTenantId(context.backend.extractId(tenant)).withResourceTypeId(realId);
            }

            @Override
            public CanonicalPath.Builder visitUnknown(CanonicalPath.Builder parameter) {
                throw new IllegalArgumentException("Unknown entity type: " + context.entityClass);
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
        }, CanonicalPath.builder()).buid();
    }

    protected abstract void wireUpNewEntity(BE entity, Blueprint blueprint, CanonicalPath path);

}
