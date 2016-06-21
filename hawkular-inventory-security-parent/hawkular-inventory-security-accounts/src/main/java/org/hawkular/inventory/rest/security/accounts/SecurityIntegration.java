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
package org.hawkular.inventory.rest.security.accounts;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.deleted;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.hawkular.accounts.api.PersonaService;
import org.hawkular.accounts.api.ResourceService;
import org.hawkular.accounts.api.model.Persona;
import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.cdi.DisposingInventory;
import org.hawkular.inventory.cdi.InventoryInitialized;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.rest.security.Security;

import rx.Subscription;

/**
 * Integrates Accounts Tenants model with Inventory.
 * <p>
 * Mutation operations must be checked explicitly in the REST classes using the {@link Security} bean and invoking
 * one of its {@code can*()} methods. The creation of the security resources associated with the newly created inventory
 * entities is handled automagically by this class which does that by observing the mutation events on the Inventory.
 *
 * @author Lukas Krejci
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @since 0.0.2
 */
@ApplicationScoped
public class SecurityIntegration {
    private static final SecurityAccountsLogger log = SecurityAccountsLogger.getLogger(InventorySecurity.class);

    @Inject
    private ResourceService storage;

    @Inject
    private PersonaService personas;

    private final Set<Subscription> subscriptions = new HashSet<>();

    public void start(@Observes InventoryInitialized event) {
        Inventory inventory = event.getInventory();

        Inventory.types().entityTypes().forEach(et -> install(inventory, et.getElementType()));
    }

    public void stop(@Observes DisposingInventory event) {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }

    private <E extends Entity<?, ?>> void install(Inventory inventory, Class<E> cls) {
        subscriptions.add(inventory.observable(Interest.in(cls).being(created()))
                .subscribe((e) -> react(e, created())));

        subscriptions.add(inventory.observable(Interest.in(cls).being(deleted()))
                .subscribe((e) -> react(e, deleted())));
    }

    @Transactional
    public void react(AbstractElement<?, ?> entity, Action<?, ?> action) {
        switch (action.asEnum()) {
            case CREATED:
                createSecurityResource(entity.getPath());
                break;
            case DELETED:
                String stableId = AccountsSecurityUtils.getStableId(entity.getPath());
                storage.delete(stableId);
                log.debugf("Deleted security entity with stable ID '%s' for entity %s", stableId, entity);
                break;
        }
    }

    private org.hawkular.accounts.api.model.Resource createSecurityResource(CanonicalPath path) {
        if (!path.isDefined()) {
            return null;
        }

        log.tracef("Creating security entity for %s", path);

        String stableId = AccountsSecurityUtils.getStableId(path);

        org.hawkular.accounts.api.model.Resource res = storage.get(stableId);
        if (res == null) {
            org.hawkular.accounts.api.model.Resource parent = createSecurityResource(path.up());

            // if the parent is null, it means we're creating a security resource for the tenant - we need to assign
            // it an owner. If the parent exists, we need to establish the owner to assign to the current resource
            Persona owner = personas.getCurrent();
            if (parent != null) {
                owner = establishOwner(parent, owner);
            }
            res = storage.create(stableId, parent, owner);
            log.debugf("Created security entity with stable ID '%s' for entity %s", stableId, path);
        }

        return res;
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
