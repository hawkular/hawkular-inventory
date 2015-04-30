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
package org.hawkular.inventory.rest;

import org.hawkular.accounts.api.model.Persona;
import org.hawkular.inventory.api.ResultFilter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.EnvironmentBasedEntity;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.FeedBasedEntity;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.model.TenantBasedEntity;
import org.hawkular.inventory.api.observable.Action;
import org.hawkular.inventory.api.observable.Interest;
import org.hawkular.inventory.api.observable.ObservableInventory;
import org.hawkular.inventory.api.observable.PartiallyApplied;
import rx.Subscription;

import java.util.HashSet;
import java.util.Set;

import static org.hawkular.inventory.api.observable.Action.created;
import static org.hawkular.inventory.api.observable.Action.deleted;

/**
 * Integrates the security concerns with the inventory.
 *
 * <p>Mutation operations must be checked explicitly in the REST classes using the {@link Security} bean and invoking
 * one of its {@code can*()} methods. The creation of the security resources associated with the newly created inventory
 * entities is handled automagically by this class which does that by observing the mutation events on the inventory.
 *
 * <p>Retrieval operations are handled automagically by this class which acts as a {@link ResultFilter} and is installed
 * into the REST inventory as such during the initialization of {@link BusIntegrationProducer}.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class SecurityIntegration implements ResultFilter {

    private final Security security;
    private final Set<Subscription> subscriptions = new HashSet<>();

    public SecurityIntegration(Security security) {
        this.security = security;
    }


    @Override
    public boolean isApplicable(AbstractElement<?, ?> element) {
        return security.canRead(element);
    }

    public void start(ObservableInventory inventory) {
        install(inventory, Tenant.class);
        install(inventory, Environment.class);
        install(inventory, Feed.class);
        install(inventory, ResourceType.class);
        install(inventory, MetricType.class);
        install(inventory, Resource.class);
        install(inventory, Metric.class);
        install(inventory, Relationship.class);
    }

    public void stop() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }

    private <E extends AbstractElement<?, ?>> void install(ObservableInventory inventory, Class<E> cls) {
        subscriptions.add(inventory.observable(Interest.in(cls).being(created()))
                .subscribe(PartiallyApplied.method(this::react).second(created())));

        subscriptions.add(inventory.observable(Interest.in(cls).being(deleted()))
                .subscribe(PartiallyApplied.method(this::react).second(deleted())));
    }

    private void react(AbstractElement<?, ?> entity, Action<?, ?> action) {
        switch (action.asEnum()) {
            case CREATED:
                createSecurityResource(entity);
                break;
            case DELETED:
                security.storage.delete(Security.getStableId(entity));
                break;
        }
    }

    private void createSecurityResource(AbstractElement<?, ?> entity) {
        //these are the possible parents
        String feedId = null;
        String environmentId = null;
        String tenantId = null;

        if (entity instanceof FeedBasedEntity) {
            feedId = ((FeedBasedEntity) entity).getFeedId();
        }

        if (entity instanceof EnvironmentBasedEntity) {
            environmentId = ((EnvironmentBasedEntity) entity).getEnvironmentId();
        }

        if (entity instanceof TenantBasedEntity) {
            tenantId = ((TenantBasedEntity) entity).getTenantId();
        }

        org.hawkular.accounts.api.model.Resource parent = null;

        //establish what parent we are going to be creating our resource under
        if (feedId != null) {
            parent = security.storage.get(Security.getStableId(Feed.class, tenantId, environmentId, feedId));
        } else if (environmentId != null) {
            parent = security.storage.get(Security.getStableId(Environment.class, tenantId, environmentId));
        } else if (tenantId != null) {
            parent = security.storage.get(Security.getStableId(Tenant.class, tenantId));
        }

        //establish the owner. If the owner of the parent is the same as the current user, then create the resource
        //as being owner-less, inheriting the owner from the parent
        Persona owner = security.personas.getCurrent();

        if (parent != null) {
            org.hawkular.accounts.api.model.Resource ownedParent = parent;
            while (ownedParent != null && ownedParent.getPersona() == null) {
                ownedParent = ownedParent.getParent();
            }

            if (ownedParent != null && ownedParent.getPersona().equals(owner)) {
                owner = null;
            }
        }

        security.storage.create(Security.getStableId(entity), parent, owner);
    }
}
