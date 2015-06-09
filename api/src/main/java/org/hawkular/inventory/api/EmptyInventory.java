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

import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.RelationFilter;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import rx.Observable;

import java.util.Collections;
import java.util.Map;

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

    @Override
    public void close() throws Exception {
    }

    @Override
    public Tenants.ReadWrite tenants() {
        return new TenantsReadWrite();
    }

    @Override
    public boolean hasObservers(Interest<?, ?> interest) {
        return false;
    }

    @Override
    public <C, E> Observable<C> observable(Interest<C, E> interest) {
        return Observable.empty();
    }

    protected static <T> Page<T> emptyPage(Pager pager) {
        return new Page<>(Collections.emptyList(), pager, 0);
    }

    protected static EntityNotFoundException entityNotFound(Class<? extends Entity> entityClass) {
        return new EntityNotFoundException(entityClass, (Filter[]) null);
    }

    public static class TenantsRead implements Tenants.Read {

        @Override
        public Tenants.Multiple getAll(Filter... filters) {
            return new TenantsMultiple();
        }

        @Override
        public Tenants.Single get(String id) throws EntityNotFoundException {
            return new TenantsSingle();
        }
    }

    public static class TenantsReadWrite implements Tenants.ReadWrite {

        @Override
        public Tenants.Multiple getAll(Filter... filters) {
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
        public ResourceTypes.Read resourceTypes() {
            return new ResourceTypesRead();
        }

        @Override
        public MetricTypes.Read metricTypes() {
            return new MetricTypesRead();
        }

        @Override
        public Environments.Read environments() {
            return new EnvironmentsRead();
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

    public static class TenantsSingle implements Tenants.Single {

        @Override
        public ResourceTypes.ReadWrite resourceTypes() {
            return new ResourceTypesReadWrite();
        }

        @Override
        public MetricTypes.ReadWrite metricTypes() {
            return new MetricTypesReadWrite();
        }

        @Override
        public Environments.ReadWrite environments() {
            return new EnvironmentReadWrite();
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
        public Tenant entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(Tenant.class);
        }
    }

    public static class ResourceTypesRead implements ResourceTypes.Read {

        @Override
        public ResourceTypes.Multiple getAll(Filter... filters) {
            return new ResourceTypesMultiple();
        }

        @Override
        public ResourceTypes.Single get(String id) throws EntityNotFoundException {
            return new ResourceTypesSingle();
        }
    }

    public static class ResourceTypesReadWrite implements ResourceTypes.ReadWrite {

        @Override
        public ResourceTypes.Multiple getAll(Filter... filters) {
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

    public static class ResourceTypesSingle implements ResourceTypes.Single {

        @Override
        public Resources.Read resources() {
            return new ResourcesRead();
        }

        @Override
        public MetricTypes.ReadAssociate metricTypes() {
            return new MetricTypesReadAssociate();
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
        public ResourceType entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(ResourceType.class);
        }
    }

    public static class ResourceTypesMultiple implements ResourceTypes.Multiple {

        @Override
        public Resources.Read resources() {
            return new ResourcesRead();
        }

        @Override
        public MetricTypes.Read metricTypes() {
            return new MetricTypesRead();
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

    public static class MetricTypesRead implements MetricTypes.Read {

        @Override
        public MetricTypes.Multiple getAll(Filter... filters) {
            return new MetricTypesMultiple();
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
            return new MetricTypesSingle();
        }
    }

    public static class MetricTypesReadWrite implements MetricTypes.ReadWrite {

        @Override
        public MetricTypes.Multiple getAll(Filter... filters) {
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
        public Relationship associate(String id) throws EntityNotFoundException, RelationAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship disassociate(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship associationWith(String id) throws RelationNotFoundException {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }

        @Override
        public MetricTypes.Multiple getAll(Filter... filters) {
            return new MetricTypesMultiple();
        }

        @Override
        public MetricTypes.Single get(String id) throws EntityNotFoundException {
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

    public static class MetricTypesSingle implements MetricTypes.Single {

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

        @Override
        public MetricType entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(MetricType.class);
        }
    }

    public static class EnvironmentsRead implements Environments.Read {

        @Override
        public Environments.Multiple getAll(Filter... filters) {
            return new EnvironmentsMultiple();
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new EnvironmentsSingle();
        }
    }

    public static class EnvironmentReadWrite implements Environments.ReadWrite {

        @Override
        public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
        }

        @Override
        public Environments.Multiple getAll(Filter... filters) {
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

    public static class EnvironmentsSingle implements Environments.Single {

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
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            return filters -> new ResourcesMultiple();
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            return filters -> new MetricsMultiple();
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
        public Feeds.Read feeds() {
            return new FeedsRead();
        }

        @Override
        public Resources.Read feedlessResources() {
            return new ResourcesRead();
        }

        @Override
        public Metrics.Read feedlessMetrics() {
            return new MetricsRead();
        }

        @Override
        public ResolvingToMultiple<Resources.Multiple> allResources() {
            return filters -> new ResourcesMultiple();
        }

        @Override
        public ResolvingToMultiple<Metrics.Multiple> allMetrics() {
            return filters -> new MetricsMultiple();
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
        public Relationships.Single linkWith(String name, Entity<?, ?> targetOrSource,
                Map<String, Object> properties) throws IllegalArgumentException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationships.Single linkWith(Relationships.WellKnown name, Entity<?, ?> targetOrSource,
                Map<String, Object> properties) throws IllegalArgumentException {
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

    public static class FeedsRead implements Feeds.Read {

        @Override
        public Feeds.Multiple getAll(Filter... filters) {
            return new FeedsMultiple();
        }

        @Override
        public Feeds.Single get(String id) throws EntityNotFoundException {
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
        public Feeds.Multiple getAll(Filter... filters) {
            return new FeedsMultiple();
        }

        @Override
        public Feeds.Single get(String id) throws EntityNotFoundException {
            return new FeedsSingle();
        }
    }

    public static class FeedsSingle implements Feeds.Single {

        @Override
        public Resources.ReadWrite resources() {
            return new ResourcesReadWrite();
        }

        @Override
        public Metrics.ReadWrite metrics() {
            return new MetricsReadWrite();
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
        public Feed entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(Feed.class);
        }
    }

    public static class FeedsMultiple implements Feeds.Multiple {

        @Override
        public Resources.Read resources() {
            return new ResourcesRead();
        }

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
        public Page<Feed> entities(Pager pager) {
            return emptyPage(pager);
        }
    }

    public static class MetricsRead implements Metrics.Read {

        @Override
        public Metrics.Multiple getAll(Filter... filters) {
            return new MetricsMultiple();
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new MetricsSingle();
        }
    }

    public static class MetricsReadWrite implements Metrics.ReadWrite {

        @Override
        public Metrics.Multiple getAll(Filter... filters) {
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
        public Relationship associate(String id) throws EntityNotFoundException, RelationAlreadyExistsException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship disassociate(String id) throws EntityNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Relationship associationWith(String id) throws RelationNotFoundException {
            throw new RelationNotFoundException((String) null, (Filter[]) null);
        }

        @Override
        public Metrics.Multiple getAll(Filter... filters) {
            return new MetricsMultiple();
        }

        @Override
        public Metrics.Single get(String id) throws EntityNotFoundException {
            return new MetricsSingle();
        }
    }

    public static class MetricsSingle implements Metrics.Single {

        @Override
        public Relationships.ReadWrite relationships() {
            return new RelationshipsReadWrite();
        }

        @Override
        public Relationships.ReadWrite relationships(Relationships.Direction direction) {
            return new RelationshipsReadWrite();
        }

        @Override
        public Metric entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(Metric.class);
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

    public static class ResourcesRead implements Resources.Read {

        @Override
        public Resources.Multiple getAll(Filter... filters) {
            return new ResourcesMultiple();
        }

        @Override
        public Resources.Single get(String id) throws EntityNotFoundException {
            return new ResourcesSingle();
        }
    }

    public static class ResourcesReadWrite implements Resources.ReadWrite {

        @Override
        public Resources.Multiple getAll(Filter... filters) {
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

    public static class ResourcesSingle implements Resources.Single {

        @Override
        public Metrics.ReadAssociate metrics() {
            return new MetricsReadAssociate();
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
        public Resource entity() throws EntityNotFoundException, RelationNotFoundException {
            throw entityNotFound(Resource.class);
        }
    }

    public static class ResourcesMultiple implements Resources.Multiple {

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
        public Page<Resource> entities(Pager pager) {
            return emptyPage(pager);
        }
    }
}
