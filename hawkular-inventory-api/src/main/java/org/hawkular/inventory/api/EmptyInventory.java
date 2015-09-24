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
package org.hawkular.inventory.api;

import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.hawkular.inventory.api.configuration.Configuration;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;

import rx.Observable;

/**
 * This is more or a less a helper class to be used in cases where an implementor or a user would like to swap out the
 * proper implementation with something that always returns empty result sets.
 *
 * @author Lukas Krejci
 * @since 0.0.2
 */
public class EmptyInventory implements Inventory {
    @Override
    public void initialize(Configuration configuration) {
    }

    @Override public TransactionFrame newTransactionFrame() {
        return new TransactionFrame() {
            @Override
            public void commit() {
            }

            @Override
            public void rollback() {
            }

            @Override
            public Inventory boundInventory() {
                return EmptyInventory.this;
            }
        };
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public Tenants.ReadWrite tenants() {
        return new TenantsReadWrite();
    }

    @Override
    public Relationships.Read relationships() {
        return new RelationshipsRead();
    }

    @Override
    public boolean hasObservers(Interest<?, ?> interest) {
        return false;
    }

    @Override
    public <C, E> Observable<C> observable(Interest<C, E> interest) {
        return Observable.empty();
    }

    @Override
    public InputStream getGraphSON(String tenantId) {
        throw entityNotFound(Tenant.class);
    }

    @Override public <T extends AbstractElement> T getElement(CanonicalPath path) {
        throw entityNotFound(Tenant.class);
    }

    @Override
    public <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(CanonicalPath startingPoint,
                                                                         Relationships.Direction direction,
                                                                         Class<T> clazz, String... relationshipNames) {
        throw entityNotFound(Tenant.class);
    }

    @Override public Configuration getConfiguration() {
        throw new UnsupportedOperationException();
    }

    protected static <T> Page<T> emptyPage(Pager pager) {
        return new Page<>(Collections.emptyIterator(), pager, 0);
    }

    protected static EntityNotFoundException entityNotFound(Class<? extends Entity<?, ?>> entityClass) {
        return new EntityNotFoundException(entityClass, null);
    }

    protected static class SingleBase<E extends Entity<?, ?>, Update>
            implements ResolvableToSingle<E, Update> {
        private final Class<E> entityType;

        public SingleBase(Class<E> entityType) {
            this.entityType = entityType;
        }

        @Override
        public E entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(entityType);
        }

        @Override
        public void update(Update update) throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(entityType);
        }

        @Override
        public void delete() {
            throw entityNotFound(entityType);
        }
    }

    public static class TenantsReadContained implements Tenants.ReadContained {

        @Override
        public Tenants.Multiple getAll(Filter[][] filters) {
            return new TenantsMultiple();
        }

        @Override
        public Tenants.Single get(String id) throws EntityNotFoundException {
            return new TenantsSingle();
        }
    }

    public static class TenantsRead implements Tenants.Read {

        @Override
        public Tenants.Multiple getAll(Filter[][] filters) {
            return new TenantsMultiple();
        }

        @Override
        public Tenants.Single get(Path path) throws EntityNotFoundException {
            return new TenantsSingle();
        }
    }

    public static class TenantsReadWrite implements Tenants.ReadWrite {

        @Override
        public Tenants.Multiple getAll(Filter[][] filters) {
            return new TenantsMultiple();
        }

        @Override
        public Tenants.Single get(String id) throws EntityNotFoundException {
            return new TenantsSingle();
        }

        @Override
        public Tenants.Single create(Tenant.Blueprint blueprint) throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, Tenant.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class TenantsMultiple implements Tenants.Multiple {

        @Override
        public ResourceTypes.ReadContained feedlessResourceTypes() {
            return new ResourceTypesReadContained();
        }

        @Override
        public MetricTypes.ReadContained feedlessMetricTypes() {
            return new MetricTypesReadContained();
        }

        @Override
        public Environments.ReadContained environments() {
            return new EnvironmentsReadContained();
        }

        @Override
        public MetricTypes.Read allMetricTypes() {
            return new MetricTypesRead();
        }

        @Override
        public ResourceTypes.Read allResourceTypes() {
            return new ResourceTypesRead();
        }

        @Override
        public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override
        public Page<Tenant> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class TenantsSingle extends SingleBase<Tenant, Tenant.Update> implements Tenants.Single {

        public TenantsSingle() {
            super(Tenant.class);
        }

        @Override
        public ResourceTypes.ReadWrite feedlessResourceTypes() {
            return new ResourceTypesReadWrite();
        }

        @Override
        public MetricTypes.ReadWrite feedlessMetricTypes() {
            return new MetricTypesReadWrite();
        }

        @Override
        public Environments.ReadWrite environments() {
            return new EnvironmentReadWrite();
        }

        @Override
        public MetricTypes.Read allMetricTypes() {
            return new MetricTypesRead();
        }

        @Override
        public ResourceTypes.Read allResourceTypes() {
            return new ResourceTypesRead();
        }

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }
    }

    public static class ResourceTypesReadContained implements ResourceTypes.ReadContained {

        @Override
        public ResourceTypes.Multiple getAll(Filter[][] filters) {
            return new ResourceTypesMultiple();
        }

        @Override
        public ResourceTypes.Single get(String id) throws EntityNotFoundException {
            return new ResourceTypesSingle();
        }
    }

    public static class ResourceTypesRead implements ResourceTypes.Read {

        @Override
        public ResourceTypes.Multiple getAll(Filter[][] filters) {
            return new ResourceTypesMultiple();
        }

        @Override
        public ResourceTypes.Single get(Path path) throws EntityNotFoundException {
            return new ResourceTypesSingle();
        }
    }

    public static class ResourceTypesReadWrite implements ResourceTypes.ReadWrite {

        @Override
        public ResourceTypes.Multiple getAll(Filter[][] filters) {
            return new ResourceTypesMultiple();
        }

        @Override
        public ResourceTypes.Single get(String id) throws EntityNotFoundException {
            return new ResourceTypesSingle();
        }

        @Override
        public ResourceTypes.Single create(ResourceType.Blueprint blueprint) throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, ResourceType.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class ResourceTypesSingle extends SingleBase<ResourceType, ResourceType.Update>
            implements ResourceTypes.Single {

        public ResourceTypesSingle() {
            super(ResourceType.class);
        }

        @Override
        public Resources.Read resources() {
            return new ResourcesRead();
        }

        @Override
        public MetricTypes.ReadAssociate metricTypes() {
            return new MetricTypesReadAssociate();
        }

        @Override
        public OperationTypes.ReadWrite operationTypes() {
            return new OperationTypesReadWrite();
        }

        @Override
        public Data.ReadWrite<ResourceTypes.DataRole> data() {
            return new DataReadWrite<>();
        }

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }
    }

    public static class ResourceTypesMultiple implements ResourceTypes.Multiple {

        @Override
        public Resources.Read resources() {
            return new ResourcesRead();
        }

        @Override
        public MetricTypes.ReadContained metricTypes() {
            return new MetricTypesReadContained();
        }

        @Override
        public OperationTypes.ReadContained operationTypes() {
            return new OperationTypesReadContained();
        }

        @Override
        public Data.Read<ResourceTypes.DataRole> data() {
            return new DataRead<>();
        }

        @Override
        public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override
        public Page<ResourceType> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class MetricTypesReadContained implements MetricTypes.ReadContained {

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new MetricTypesMultiple();
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new MetricTypesSingle();
        }
    }

    public static class MetricTypesRead implements MetricTypes.Read {

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new MetricTypesMultiple();
        }

        @Override
        public MetricTypes.Single get(Path path) throws EntityNotFoundException {
            return new MetricTypesSingle();
        }
    }

    public static class MetricTypesReadWrite implements MetricTypes.ReadWrite {

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new MetricTypesMultiple();
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new MetricTypesSingle();
        }

        @Override
        public MetricTypes.Single create(MetricType.Blueprint blueprint) throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, MetricType.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class MetricTypesReadAssociate implements MetricTypes.ReadAssociate {

        @Override
        public Relationship associate(Path id) throws EntityNotFoundException,
                RelationAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship disassociate(Path id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship associationWith(Path path) throws RelationNotFoundException {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter[][] filters) {
            return new MetricTypesMultiple();
        }

        @Override
        public MetricTypes.Single get(Path id) throws EntityNotFoundException {
            return new MetricTypesSingle();
        }
    }

    public static class MetricTypesMultiple implements MetricTypes.Multiple {

        @Override
        public Metrics.Read metrics() {
            return new MetricsRead();
        }

        @Override
        public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override
        public Page<MetricType> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class MetricTypesSingle extends SingleBase<MetricType, MetricType.Update>
            implements MetricTypes.Single {

        public MetricTypesSingle() {
            super(MetricType.class);
        }

        @Override
        public Metrics.Read metrics() {
            return new MetricsRead();
        }

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }
    }

    public static class EnvironmentsReadContained implements Environments.ReadContained {

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new EnvironmentsMultiple();
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new EnvironmentsSingle();
        }
    }

    public static class EnvironmentsRead implements Environments.Read {

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new EnvironmentsMultiple();
        }

        @Override
        public Environments.Single get(Path id) throws EntityNotFoundException {
            return new EnvironmentsSingle();
        }
    }

    public static class EnvironmentReadWrite implements Environments.ReadWrite {

        @Override
        public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
        }

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new EnvironmentsMultiple();
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new EnvironmentsSingle();
        }

        @Override
        public Environments.Single create(Environment.Blueprint blueprint) throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, Environment.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class EnvironmentsSingle extends SingleBase<Environment, Environment.Update>
            implements Environments.Single {

        public EnvironmentsSingle() {
            super(Environment.class);
        }

        @Override
        public Feeds.ReadWrite feeds() {
            return new FeedsReadWrite();
        }

        @Override
        public Resources.ReadWrite feedlessResources() {
            return new ResourcesReadWrite();
        }

        @Override
        public Metrics.ReadWrite feedlessMetrics() {
            return new MetricsReadWrite();
        }

        @Override
        public Resources.Read allResources() {
            return new ResourcesRead();
        }

        @Override
        public Metrics.Read allMetrics() {
            return new MetricsRead();
        }

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }

        @Override
        public Environment entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(Environment.class);
        }
    }

    public static class EnvironmentsMultiple implements Environments.Multiple {

        @Override
        public Feeds.ReadContained feeds() {
            return new FeedsReadContained();
        }

        @Override
        public Resources.ReadContained feedlessResources() {
            return new ResourcesReadContained();
        }

        @Override
        public Metrics.ReadContained feedlessMetrics() {
            return new MetricsReadContained();
        }

        @Override
        public Resources.Read allResources() {
            return new ResourcesRead();
        }

        @Override
        public Metrics.Read allMetrics() {
            return new MetricsRead();
        }

        @Override
        public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override
        public Page<Environment> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class RelationshipsRead implements Relationships.Read {

        @Override
        public Relationships.Multiple named(String name) {
            return new RelationshipsMultiple();
        }

        @Override
        public Relationships.Multiple named(Relationships.WellKnown name) {
            return new RelationshipsMultiple();
        }

        @Override
        public Relationships.Single get(String id) throws EntityNotFoundException, RelationNotFoundException {
            return new RelationshipsSingle();
        }

        @Override
        public Relationships.Multiple getAll(RelationFilter... filters) {
            return new RelationshipsMultiple();
        }
    }

    public static class RelationshipsReadWrite implements Relationships.ReadWrite {

        @Override
        public Relationships.Multiple named(String name) {
            return new RelationshipsMultiple();
        }

        @Override
        public Relationships.Multiple named(Relationships.WellKnown name) {
            return new RelationshipsMultiple();
        }

        @Override
        public Relationships.Single get(String id) throws EntityNotFoundException, RelationNotFoundException {
            return new RelationshipsSingle();
        }

        @Override
        public Relationships.Multiple getAll(RelationFilter... filters) {
            return new RelationshipsMultiple();
        }

        @Override
        public Relationships.Single linkWith(String name, CanonicalPath targetOrSource, Map<String, Object> properties)
                throws IllegalArgumentException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, Relationship.Update update) throws RelationNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws RelationNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class RelationshipsSingle implements Relationships.Single {

        @Override
        public Relationship entity() throws EntityNotFoundException, RelationNotFoundException {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }

        @Override
        public void update(Relationship.Update o) throws EntityNotFoundException, RelationNotFoundException {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }

        @Override
        public void delete() {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }
    }

    public static class RelationshipsMultiple implements Relationships.Multiple {

        @Override
        public Tenants.Read tenants() {
            return new TenantsRead();
        }

        @Override
        public Environments.Read environments() {
            return new EnvironmentsRead();
        }

        @Override
        public Feeds.Read feeds() {
            return new FeedsRead();
        }

        @Override
        public MetricTypes.Read metricTypes() {
            return new MetricTypesRead();
        }

        @Override
        public Metrics.Read metrics() {
            return new MetricsRead();
        }

        @Override
        public Resources.Read resources() {
            return new ResourcesRead();
        }

        @Override
        public ResourceTypes.Read resourceTypes() {
            return new ResourceTypesRead();
        }

        @Override
        public Page<Relationship> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class FeedsReadContained implements Feeds.ReadContained {

        @Override
        public Feeds.Multiple getAll(Filter[][] filters) {
            return new FeedsMultiple();
        }

        @Override
        public Feeds.Single get(String id) throws EntityNotFoundException {
            return new FeedsSingle();
        }
    }

    public static class FeedsRead implements Feeds.Read {

        @Override
        public Feeds.Multiple getAll(Filter[][] filters) {
            return new FeedsMultiple();
        }

        @Override
        public Feeds.Single get(Path id) throws EntityNotFoundException {
            return new FeedsSingle();
        }
    }

    public static class FeedsReadWrite implements Feeds.ReadWrite {

        @Override
        public Feeds.Single create(Feed.Blueprint blueprint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, Feed.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Feeds.Multiple getAll(Filter[][] filters) {
            return new FeedsMultiple();
        }

        @Override
        public Feeds.Single get(String id) throws EntityNotFoundException {
            return new FeedsSingle();
        }
    }

    public static class FeedsSingle extends SingleBase<Feed, Feed.Update> implements Feeds.Single {

        public FeedsSingle() {
            super(Feed.class);
        }

        @Override
        public Resources.ReadWrite resources() {
            return new ResourcesReadWrite();
        }

        @Override
        public Metrics.ReadWrite metrics() {
            return new MetricsReadWrite();
        }

        @Override
        public MetricTypes.ReadWrite metricTypes() {
            return new MetricTypesReadWrite();
        }

        @Override
        public ResourceTypes.ReadWrite resourceTypes() {
            return new ResourceTypesReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }
    }

    public static class FeedsMultiple implements Feeds.Multiple {

        @Override
        public Resources.ReadContained resources() {
            return new ResourcesReadContained();
        }

        @Override
        public Metrics.ReadContained metrics() {
            return new MetricsReadContained();
        }

        @Override
        public MetricTypes.ReadContained metricTypes() {
            return new MetricTypesReadContained();
        }

        @Override
        public ResourceTypes.ReadContained resourceTypes() {
            return new ResourceTypesReadContained();
        }

        @Override
        public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override
        public Page<Feed> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class MetricsReadContained implements Metrics.ReadContained {

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new MetricsMultiple();
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new MetricsSingle();
        }
    }

    public static class MetricsRead implements Metrics.Read {

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new MetricsMultiple();
        }

        @Override
        public Metrics.Single get(Path id) throws EntityNotFoundException {
            return new MetricsSingle();
        }
    }

    public static class MetricsReadWrite implements Metrics.ReadWrite {

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new MetricsMultiple();
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new MetricsSingle();
        }

        @Override
        public Metrics.Single create(Metric.Blueprint blueprint) throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, Metric.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class MetricsReadAssociate implements Metrics.ReadAssociate {

        @Override
        public Relationship associate(
                Path id) throws EntityNotFoundException, RelationAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship disassociate(Path id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship associationWith(Path path) throws RelationNotFoundException {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }

        @Override
        public Metrics.Multiple getAll(Filter[][] filters) {
            return new MetricsMultiple();
        }

        @Override
        public Metrics.Single get(Path id) throws EntityNotFoundException {
            return new MetricsSingle();
        }
    }

    public static class MetricsSingle extends SingleBase<Metric, Metric.Update> implements Metrics.Single {

        public MetricsSingle() {
            super(Metric.class);
        }

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }
    }

    public static class MetricsMultiple implements Metrics.Multiple {

        @Override
        public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override
        public Page<Metric> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class ResourcesReadContained implements Resources.ReadContained {

        @Override
        public Resources.Multiple getAll(Filter[][] filters) {
            return new ResourcesMultiple();
        }

        @Override
        public Resources.Single get(String id) throws EntityNotFoundException {
            return new ResourcesSingle();
        }
    }

    public static class ResourcesRead implements Resources.Read {

        @Override
        public Resources.Multiple getAll(Filter[][] filters) {
            return new ResourcesMultiple();
        }

        @Override
        public Resources.Single get(Path id) throws EntityNotFoundException {
            return new ResourcesSingle();
        }
    }

    public static class ResourcesReadWrite implements Resources.ReadWrite {

        @Override
        public Resources.Multiple getAll(Filter[][] filters) {
            return new ResourcesMultiple();
        }

        @Override
        public Resources.Single get(String id) throws EntityNotFoundException {
            return new ResourcesSingle();
        }

        @Override
        public Resources.Single create(Resource.Blueprint blueprint) throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String id, Resource.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class ResourcesSingle extends SingleBase<Resource, Resource.Update> implements Resources.Single {

        public ResourcesSingle() {
            super(Resource.class);
        }

        @Override
        public Metrics.ReadAssociate metrics() {
            return new MetricsReadAssociate();
        }


        @Override
        public Resources.ReadAssociate allChildren() {
            return new ResourcesReadAssociate();
        }

        @Override
        public Resources.ReadWrite containedChildren() {
            return new ResourcesReadWrite();
        }

        @Override
        public Resources.Single parent() {
            return new ResourcesSingle();
        }

        @Override
        public Resources.Read parents() {
            return new ResourcesRead();
        }

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }

        @Override
        public Data.ReadWrite<Resources.DataRole> data() {
            return new DataReadWrite<>();
        }

    }

    public static class ResourcesMultiple implements Resources.Multiple {

        @Override
        public Metrics.Read metrics() {
            return new MetricsRead();
        }

        @Override
        public Resources.ReadAssociate allChildren() {
            return new ResourcesReadAssociate();
        }

        @Override
        public Resources.ReadWrite containedChildren() {
            return new ResourcesReadWrite();
        }

        @Override
        public Resources.Read parents() {
            return new ResourcesRead();
        }

        @Override
        public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override
        public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override
        public Page<Resource> entities(Pager pager) {
            return emptyPage(pager);
        }

        @Override
        public Data.Read<Resources.DataRole> data() {
            return new DataRead<>();
        }

    }

    public static class ResourcesReadAssociate implements Resources.ReadAssociate {

        @Override
        public Relationship associate(
                Path id) throws EntityNotFoundException, RelationAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship disassociate(Path id) throws EntityNotFoundException, IllegalArgumentException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship associationWith(Path path) throws RelationNotFoundException {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }

        @Override
        public Resources.Multiple getAll(Filter[][] filters) {
            return new ResourcesMultiple();
        }

        @Override
        public Resources.Single get(Path id) throws EntityNotFoundException {
            return new ResourcesSingle();
        }
    }

    public static class DataRead<Role extends DataEntity.Role> implements Data.Read<Role> {

        @Override
        public Data.Multiple getAll(Filter[][] filters) {
            return new DatasMultiple();
        }

        @Override
        public Data.Single get(Role ignored) throws EntityNotFoundException {
            return new DatasSingle();
        }
    }

    public static class DataReadWrite<Role extends DataEntity.Role> implements Data.ReadWrite<Role> {

        @Override
        public Data.Multiple getAll(Filter[][] filters) {
            return new DatasMultiple();
        }

        @Override
        public Data.Single get(Role ignored) throws EntityNotFoundException {
            return new DatasSingle();
        }

        @Override
        public Data.Single create(DataEntity.Blueprint<Role> blueprint) throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Role ignored, DataEntity.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Role ignored) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class DatasSingle implements Data.Single {
        @Override
        public StructuredData flatData(RelativePath dataPath) {
            throw entityNotFound(null);
        }

        @Override
        public DataEntity entity() throws EntityNotFoundException {
            throw entityNotFound(null);
        }

        @Override
        public StructuredData data(RelativePath dataPath) {
            throw entityNotFound(null);
        }

        @Override
        public void update(DataEntity.Update update) throws EntityNotFoundException, RelationNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete() {
            throw new UnsupportedOperationException();
        }
    }

    public static class DatasMultiple implements Data.Multiple {
        @Override
        public Page<DataEntity> entities(Pager pager) {
            return emptyPage(pager);
        }

        @Override
        public Page<StructuredData> data(RelativePath dataPath, Pager pager) {
            return emptyPage(pager);
        }

        @Override
        public Page<StructuredData> flatData(RelativePath dataPath, Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class OperationTypesReadContained implements OperationTypes.ReadContained {

        @Override public OperationTypes.Multiple getAll(Filter[][] filters) {
            return new OperationTypesMultiple();
        }

        @Override public OperationTypes.Single get(String s) throws EntityNotFoundException {
            return new OperationTypesSingle();
        }
    }

    public static class OperationTypesReadWrite implements OperationTypes.ReadWrite {

        @Override public OperationTypes.Multiple getAll(Filter[][] filters) {
            return new OperationTypesMultiple();
        }

        @Override public OperationTypes.Single get(String s) throws EntityNotFoundException {
            return new OperationTypesSingle();
        }

        @Override public OperationTypes.Single create(OperationType.Blueprint blueprint)
                throws EntityAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override public void update(String s, OperationType.Update update) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override public void delete(String s) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

    public static class OperationTypesSingle implements OperationTypes.Single {

        @Override public Data.ReadWrite<OperationTypes.DataRole> data() {
            return new DataReadWrite<>();
        }

        @Override public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }

        @Override public OperationType entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(OperationType.class);
        }

        @Override public void update(OperationType.Update update)
                throws EntityNotFoundException, RelationNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override public void delete() {
            throw new UnsupportedOperationException();
        }
    }

    public static class OperationTypesMultiple implements OperationTypes.Multiple {

        @Override public Data.Read<OperationTypes.DataRole> data() {
            return new DataRead<>();
        }

        @Override public Relationships.Read relationships() {
            return new RelationshipsRead();
        }

        @Override public Relationships.Read relationships(Relationships.Direction direction) {
            return new RelationshipsRead();
        }

        @Override public Page<OperationType> entities(Pager pager) {
            return emptyPage(pager);
        }
    }
}
