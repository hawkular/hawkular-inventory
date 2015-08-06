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
package org.hawkular.inventory.bus;

import java.util.HashSet;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.TopicConnectionFactory;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.PartiallyApplied;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import rx.Subscription;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class BusIntegration {

    private final Inventory inventory;
    private MessageSender messageSender;
    private final Set<Subscription> subscriptions = new HashSet<>();
    private Configuration configuration;
    private InitialContext namingContext;

    public BusIntegration(Inventory inventory) {
        this.inventory = inventory;
    }

    public void configure(Configuration configuration) {
        this.configuration = configuration;
    }

    public void start() throws NamingException, JMSException {
        if (namingContext != null) {
            return;
        }

        namingContext = new InitialContext();
        TopicConnectionFactory tcf = (TopicConnectionFactory) namingContext.lookup(
                configuration.getConnectionFactoryJndiName());

        ConnectionContextFactory ccf = new ConnectionContextFactory(tcf);

        this.messageSender = new MessageSender(ccf, configuration.getInventoryChangesTopicName());

        install();
    }

    public void stop() throws NamingException {
        uninstall();
        namingContext.close();
        namingContext = null;
    }

    private void install() {
        install(inventory, subscriptions, Tenant.class, messageSender);
        install(inventory, subscriptions, ResourceType.class, messageSender);
        install(inventory, subscriptions, MetricType.class, messageSender);
        install(inventory, subscriptions, Environment.class, messageSender, Action.copied());
        install(inventory, subscriptions, Feed.class, messageSender, Action.registered());
        install(inventory, subscriptions, Resource.class, messageSender);
        install(inventory, subscriptions, Metric.class, messageSender);
        install(inventory, subscriptions, Relationship.class, messageSender);
    }

    private void uninstall() {
        subscriptions.forEach(Subscription::unsubscribe);
    }

    private static <U extends AbstractElement.Update, T extends AbstractElement<?, U>>
    void install(Inventory inventory, Set<Subscription> subscriptions, Class<T> entityClass,
            MessageSender sender, Action<?, T>... additionalActions) {

        installAction(inventory, subscriptions, entityClass, sender, Action.created());
        installAction(inventory, subscriptions, entityClass, sender, Action.updated());
        installAction(inventory, subscriptions, entityClass, sender, Action.deleted());
        for (Action<?, T> a : additionalActions) {
            installAction(inventory, subscriptions, entityClass, sender, a);
        }
    }

    private static <C, T> void installAction(Inventory inventory, Set<Subscription> subscriptions,
            Class<T> entityClass, MessageSender sender, Action<C, T> action) {

        Interest<C, T> interest = Interest.in(entityClass).being(action);

        Subscription s = inventory.observable(interest).subscribe(PartiallyApplied.procedure(sender::send)
                .first(interest));
        subscriptions.add(s);
    }
}
