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

import org.hawkular.accounts.api.PersonaService;
import org.hawkular.accounts.api.ResourceService;
import org.hawkular.accounts.api.model.Persona;
import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.PartiallyApplied;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.EnvironmentBasedEntity;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.FeedBasedEntity;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.model.TenantBasedEntity;
import org.hawkular.inventory.cdi.DisposingObservableInventory;
import org.hawkular.inventory.cdi.ObservableInventoryInitialized;
import rx.Subscription;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.deleted;
import static org.hawkular.inventory.rest.RestApiLogger.LOGGER;

/**
 * Integrates the security concerns with the inventory.
 *
 * <p>Mutation operations must be checked explicitly in the REST classes using the {@link Security} bean and invoking
 * one of its {@code can*()} methods. The creation of the security resources associated with the newly created inventory
 * entities is handled automagically by this class which does that by observing the mutation events on the inventory.
 *
 * @author Lukas Krejci
 * @since 0.0.2
 */
@ApplicationScoped
public class SecurityIntegration {

    @Inject
    ResourceService storage;

    @Inject
    PersonaService personas;

    private final Set<Subscription> subscriptions = new HashSet<>();

    public void start(@Observes ObservableInventoryInitialized event) {
        Inventory.Mixin.Observable inventory = event.getInventory();
        install(inventory, Tenant.class);
        install(inventory, Environment.class);
        install(inventory, Feed.class);
        install(inventory, ResourceType.class);
        install(inventory, MetricType.class);
        install(inventory, Resource.class);
        install(inventory, Metric.class);
        //install(inventory, Relationship.class);
    }

    public void stop(@Observes DisposingObservableInventory event) {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }

    private <E extends AbstractElement<?, ?>> void install(Inventory.Mixin.Observable inventory, Class<E> cls) {
        subscriptions.add(inventory.observable(Interest.in(cls).being(created()))
                .subscribe(PartiallyApplied.method(this::react).second(created())));

        subscriptions.add(inventory.observable(Interest.in(cls).being(deleted()))
                .subscribe(PartiallyApplied.method(this::react).second(deleted())));
    }

    @Transactional
    public void react(AbstractElement<?, ?> entity, Action<?, ?> action) {
        switch (action.asEnum()) {
            case CREATED:
                createSecurityResource(entity);
                break;
            case DELETED:
                storage.delete(Security.getStableId(entity));
                break;
        }
    }

    private void createSecurityResource(AbstractElement<?, ?> entity) {
        LOGGER.tracef("Creating security entity for %s", entity);

        org.hawkular.accounts.api.model.Resource parent = ensureParent(entity);

        Persona owner = establishOwner(parent, personas.getCurrent());

        // because the event handling in inventory is not ordered in any way, we might receive the info about creating
        // a parent after a child has been reported. In that case, the security resource for the parent will already
        // exist.
        String stableId = Security.getStableId(entity);
        if (storage.get(stableId) == null) {
            storage.create(stableId, parent, owner);
            LOGGER.debugf("Created security entity with stable ID '%s' for entity %s", stableId, entity);
        }
    }

    private org.hawkular.accounts.api.model.Resource ensureParent(AbstractElement<?, ?> entity) {
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
        Persona owner = personas.getCurrent();

        if (tenantId != null) {
            String parentStableId = Security.getStableId(Tenant.class, tenantId);

            org.hawkular.accounts.api.model.Resource tenantResource = storage.get(parentStableId);

            if (tenantResource == null) {
                tenantResource = storage.create(parentStableId, null, owner);
            } else {
                owner = establishOwner(tenantResource, owner);
            }

            parent = tenantResource;

            if (environmentId != null) {
                parentStableId = Security.getStableId(Environment.class, tenantId, environmentId);
                org.hawkular.accounts.api.model.Resource envResource = storage.get(parentStableId);

                if (envResource == null) {
                    envResource = storage.create(parentStableId, tenantResource, owner);
                } else {
                    owner = establishOwner(envResource, owner);
                }

                parent = envResource;

                if (feedId != null) {
                    parentStableId = Security.getStableId(Feed.class, tenantId, environmentId, feedId);
                    org.hawkular.accounts.api.model.Resource feedResource = storage.get(parentStableId);

                    if (feedResource == null) {
                        storage.create(parentStableId, envResource, owner);
                    }

                    parent = feedResource;
                }
            }
        }

        return parent;
    }

    /**
     * Establishes the owner. If the owner of the parent is the same as the current user, then create the resource
     * as being owner-less, inheriting the owner from the parent.
     */
    private Persona establishOwner(org.hawkular.accounts.api.model.Resource resource, Persona current) {
        while (resource != null && resource.getPersona() == null) {
            resource = resource.getParent();
        }

        if (resource != null && resource.getPersona().equals(current)) {
            current = null;
        }

        return current;
    }
}
