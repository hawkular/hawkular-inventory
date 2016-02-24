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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

/**
 * Takes care of defining the pre-commit actions based on the set of entities modified within a single transaction.
 *
 * <p>Most importantly it takes care of minimizing the number of computations needed to compute identity hashes of
 * entities. To compute identity hash of a parent resource, one needs to compute the hashes of all its children so if
 * also the child is modified, it only is necessary to compute the tree of the identity hashes of the parent and pick
 * the hashes of the modified children from that.
 *
 * @author Lukas Krejci
 * @since 0.13.0
 */
public class BasePreCommit<BE> implements Transaction.PreCommit<BE> {

    private final List<EntityAndPendingNotifications<BE, ?>> nonHashedChanges = new ArrayList<>();
    private final List<Consumer<Transaction<BE>>> explicitActions = new ArrayList<>();

    @Override public void initialize(Inventory inventory, Transaction<BE> tx) {
        //TODO implement
    }

    @Override public void reset() {
        nonHashedChanges.clear();
        explicitActions.clear();
        //TODO implement
    }

    @Override public List<EntityAndPendingNotifications<BE, ?>> getFinalNotifications() {
        List<EntityAndPendingNotifications<BE, ?>> ret = new ArrayList<>();

        //TODO implement

        ret.addAll(nonHashedChanges);

        return ret;
    }

    @Override public void addAction(Consumer<Transaction<BE>> action) {
        explicitActions.add(action);
    }

    @Override public List<Consumer<Transaction<BE>>> getActions() {
        //TODO implement
        return explicitActions;
    }

    @Override public void addNotifications(EntityAndPendingNotifications<BE, ?> element) {
        if (needsProcessing(element)) {
            //TODO implement
            //this is just to not break tests atm - the proper implementation will remove this line
            nonHashedChanges.add(element);
        } else {
            nonHashedChanges.add(element);
        }
    }

    @Override public void addProcessedNotifications(EntityAndPendingNotifications<BE, ?> element) {
        nonHashedChanges.add(element);
    }

    private boolean needsProcessing(EntityAndPendingNotifications<?, ?> element) {
        //TODO implement
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Blueprint asBlueprint(AbstractElement<?, ?> element) {
        if (element == null) {
            return null;
        }
        return element.accept(new ElementVisitor<Blueprint, Void>() {
            @Override public Blueprint visitData(DataEntity data, Void parameter) {
                return fillBasicEntity(data, DataEntity.Blueprint.builder()).withRole(data.getRole())
                        .withValue(data.getValue()).build();
            }

            @Override public Blueprint visitTenant(Tenant tenant, Void parameter) {
                return fillBasicEntity(tenant, Tenant.Blueprint.builder()).build();
            }

            @Override public Blueprint visitEnvironment(Environment environment, Void parameter) {
                return fillBasicEntity(environment, Environment.Blueprint.builder()).build();
            }

            @Override public Blueprint visitFeed(Feed feed, Void parameter) {
                return fillBasicEntity(feed, Feed.Blueprint.builder()).build();
            }

            @Override public Blueprint visitMetric(Metric metric, Void parameter) {
                return fillBasicEntity(metric, Metric.Blueprint.builder()).withInterval(metric.getCollectionInterval())
                        .withMetricTypePath(metric.getType().getPath().toString()).build();
            }

            @Override public Blueprint visitMetricType(MetricType type, Void parameter) {
                return fillBasicEntity(type, MetricType.Blueprint.builder(type.getType()))
                        .withInterval(type.getCollectionInterval()).withUnit(type.getUnit()).build();
            }

            @Override public Blueprint visitOperationType(OperationType operationType, Void parameter) {
                return fillBasicEntity(operationType, OperationType.Blueprint.builder()).build();
            }

            @Override public Blueprint visitMetadataPack(MetadataPack metadataPack, Void parameter) {
                return MetadataPack.Blueprint.builder().withName(metadataPack.getName()).withProperties(metadataPack
                        .getProperties()).build();
            }

            @Override public Blueprint visitUnknown(Object entity, Void parameter) {
                throw new IllegalArgumentException("Unknown blueprint type: " + entity);
            }

            @Override public Blueprint visitResource(Resource resource, Void parameter) {
                return fillBasicEntity(resource, Resource.Blueprint.builder())
                        .withResourceTypePath(resource.getType().getPath().toString()).build();
            }

            @Override public Blueprint visitResourceType(ResourceType type, Void parameter) {
                return fillBasicEntity(type, ResourceType.Blueprint.builder()).build();
            }

            @Override public Blueprint visitRelationship(Relationship relationship, Void parameter) {
                return new Relationship.Blueprint(Relationships.Direction.outgoing, relationship.getName(),
                        relationship.getTarget(), relationship.getProperties());
            }

            private <E extends Entity<? extends Bl, ?>, Bl extends Entity.Blueprint,
                    BB extends Entity.Blueprint.Builder<Bl, BB>>
            BB fillBasicEntity(E entity, BB bld) {
                return bld.withId(entity.getId()).withName(entity.getName()).withProperties(entity.getProperties());
            }
        }, null);
    }

}
