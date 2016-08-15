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
package org.hawkular.inventory.bus;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.jms.JMSException;
import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.consumer.ConsumerConnectionContext;
import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.PageContext;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.bus.api.DataEntityEvent;
import org.hawkular.inventory.bus.api.EnvironmentEvent;
import org.hawkular.inventory.bus.api.FeedEvent;
import org.hawkular.inventory.bus.api.InventoryEvent;
import org.hawkular.inventory.bus.api.InventoryEventMessageListener;
import org.hawkular.inventory.bus.api.InventoryQueryRequestMessage;
import org.hawkular.inventory.bus.api.InventoryQueryResponseMessage;
import org.hawkular.inventory.bus.api.MetricEvent;
import org.hawkular.inventory.bus.api.MetricTypeEvent;
import org.hawkular.inventory.bus.api.RelationshipEvent;
import org.hawkular.inventory.bus.api.ResourceEvent;
import org.hawkular.inventory.bus.api.ResourceTypeEvent;
import org.hawkular.inventory.bus.api.ResultSet;
import org.hawkular.inventory.bus.api.TenantEvent;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

//import org.hawkular.bus.common.test.SimpleTestListener;
//import org.hawkular.bus.common.test.VMEmbeddedBrokerWrapper;

/**
 * @author Pavol Loffay
 * @since 0.2.0
 */
public class BusTest {

    private static final String PROP_KEY = "p1";
    private static final String PROP_VALUE = "prop1";
    private static final Map<String, Object> objectProperties = new HashMap<>();
    private static final Map<String, String> messageHeaders = new HashMap<>();

    @BeforeClass
    public static void init() {
        objectProperties.put(PROP_KEY, PROP_VALUE);
        messageHeaders.put(PROP_KEY, PROP_VALUE);
    }

    @Test
    public void messagesSerializationTest() {
        Tenant tenant = new Tenant(CanonicalPath.fromString("/t;c"), "t", objectProperties);
        MetricType metricType = new MetricType(CanonicalPath.fromString("/t;t/mt;mt"), null, null, null,
                MetricUnit.MINUTES,
                MetricDataType.GAUGE);
        ResourceType resourceType = new ResourceType(CanonicalPath.fromString("/t;t/rt;rt"), null, null, null,
                objectProperties);

        Tenant tenant2 = new Tenant(CanonicalPath.fromString("/t;t"), null);
        TenantEvent tenantEvent = new TenantEvent(Action.Enumerated.CREATED, tenant);
        EnvironmentEvent environmentEvent = new EnvironmentEvent(Action.Enumerated.CREATED,
                tenant2,
                new Environment(CanonicalPath.fromString("/t;t/e;e"), null, objectProperties));
        FeedEvent feedEvent = new FeedEvent(Action.Enumerated.UPDATED,
                tenant2,
                new Feed(CanonicalPath.fromString("/t;t/f;f"), null, null, null, objectProperties));
        MetricEvent metricEvent = new MetricEvent(Action.Enumerated.DELETED,
                tenant2,
                new Metric(CanonicalPath.fromString("/t;t/e;e/m;m"), null, null, null, metricType, objectProperties));
        MetricTypeEvent metricTypeEvent = new MetricTypeEvent(Action.Enumerated.COPIED, tenant2, metricType);
        RelationshipEvent relationshipEvent = new RelationshipEvent(Action.Enumerated.REGISTERED,
                tenant2,
                new Relationship("id", "rel1", CanonicalPath.fromString("/t;t"), CanonicalPath.fromString("/t;t/e;e")
                        , objectProperties));
        ResourceEvent resourceEvent = new ResourceEvent(Action.Enumerated.UPDATED,
                tenant2,
                new Resource(CanonicalPath.fromString("/t;t/e;e/r;r"), null, null, null, resourceType));
        ResourceTypeEvent resourceTypeEvent = new ResourceTypeEvent(Action.Enumerated.COPIED, tenant2, resourceType);
        DataEntityEvent dataEntityEvent = new DataEntityEvent(Action.Enumerated.DELETED,
                tenant2,
                new DataEntity(resourceType.getPath(), DataRole.ResourceType.configurationSchema,
                        StructuredData.get().undefined(), "hash", null, null));

        String tenantJSON = tenantEvent.toJSON();
        String envJSON = environmentEvent.toJSON();
        String feedJSON = feedEvent.toJSON();
        String metricJSON = metricEvent.toJSON();
        String metricTypeJSON = metricTypeEvent.toJSON();
        String relationshipJSON = relationshipEvent.toJSON();
        String resourceJSON = resourceEvent.toJSON();
        String resourceTypeJSON = resourceTypeEvent.toJSON();
        String dataEntityJSON = dataEntityEvent.toJSON();

        assertThat(tenantEvent.getObject(),
                is(equalTo(TenantEvent.fromJSON(tenantJSON, TenantEvent.class).getObject())));
        assertThat(environmentEvent.getObject(),
                is(equalTo(EnvironmentEvent.fromJSON(envJSON, EnvironmentEvent.class).getObject())));
        assertThat(feedEvent.getObject(),
                is(equalTo(feedEvent.fromJSON(feedJSON, FeedEvent.class).getObject())));
        assertThat(metricEvent.getObject(),
                is(equalTo(MetricEvent.fromJSON(metricJSON, MetricEvent.class).getObject())));
        assertThat(metricTypeEvent.getObject(),
                is(equalTo(MetricTypeEvent.fromJSON(metricTypeJSON, MetricTypeEvent.class).getObject())));
        assertThat(relationshipEvent.getObject(),
                is(equalTo(RelationshipEvent.fromJSON(relationshipJSON, RelationshipEvent.class).getObject())));
        assertThat(resourceEvent.getObject(),
                is(equalTo(ResourceEvent.fromJSON(resourceJSON, ResourceEvent.class).getObject())));
        assertThat(resourceTypeEvent.getObject(),
                is(equalTo(ResourceTypeEvent.fromJSON(resourceTypeJSON, ResourceTypeEvent.class).getObject())));
        assertThat(dataEntityEvent.getObject(), is(equalTo(DataEntityEvent.fromJSON(dataEntityJSON,
                DataEntityEvent.class).getObject())));
    }

    @Test
    public void createTenantEventFromJSON() {
        Tenant tenant = new Tenant(CanonicalPath.fromString("/t;c"), null, objectProperties);
        TenantEvent tenantEvent = new TenantEvent(Action.Enumerated.CREATED, tenant);

        String tenantJSON = "{\"action\":\"CREATED\",\"object\":{\"path\":\"/t;c\",\"properties\":{\"p1\":\"prop1\"}}}";
        TenantEvent tenantEventFromJSON = TenantEvent.fromJSON(tenantJSON, TenantEvent.class);

        assertThat(tenantEvent.getObject().getPath(), is(equalTo(tenantEventFromJSON.getObject().getPath())));
    }

    @Test
    public void createMetricEventFromJSON() {
        Tenant tenant = new Tenant(CanonicalPath.fromString("/t;t"), null);
        MetricType metricType = new MetricType(CanonicalPath.fromString("/t;t/mt;mt"), null, null, null,
                MetricUnit.MINUTES,
                MetricDataType.GAUGE);
        MetricEvent metricEvent = new MetricEvent(Action.Enumerated.DELETED,
                tenant,
                new Metric(CanonicalPath.fromString("/t;t/e;e/m;m"), null, null, null, metricType, objectProperties));

        String metricJSON = "{\"action\":\"DELETED\",\"object\":{\"path\":\"/t;t/e;e/m;m\",\"type\"" +
                ":{\"path\":\"/t;t/mt;mt\",\"properties\":null,\"unit\":\"MINUTES\",\"type\":\"GAUGE\"}," +
                "\"properties\":{}}}";
        MetricEvent metricEventFromJSON = MetricEvent.fromJSON(metricJSON, MetricEvent.class);

        assertThat(metricEvent.getObject().getPath(), is(equalTo(metricEventFromJSON.getObject().getPath())));
    }

    @Test
    public void sendTenantEvent() throws Exception {
//        // this is the same test as testFilter except the headers will be put directly in BasicMessage
//        ConnectionContextFactory consumerFactory = null;
//        ConnectionContextFactory producerFactory = null;
//
//        VMEmbeddedBrokerWrapper broker = new VMEmbeddedBrokerWrapper();
//        broker.start();
//
//        // set up message to send
//        Tenant tenantToSend = new Tenant(CanonicalPath.fromString("/t;c"), objectProperties);
//        // send one that will match the selector
//        TenantEvent tenantEventToSend = new TenantEvent(Action.Enumerated.CREATED, tenantToSend);
//        tenantEventToSend.setHeaders(messageHeaders);
//
//        try {
//            String brokerURL = broker.getBrokerURL();
//            Endpoint endpoint = new Endpoint(Endpoint.Type.QUEUE, "testq");
//
//            // mimic server-side
//            consumerFactory = new ConnectionContextFactory(brokerURL);
//            ConsumerConnectionContext consumerContext = consumerFactory.createConsumerConnectionContext(endpoint);
//            // tenant listener
//            SimpleTestListener<TenantEvent> tenantListener = new SimpleTestListener<TenantEvent>(TenantEvent.class);
//            MessageProcessor serverSideTenantProcessor = new MessageProcessor();
//            serverSideTenantProcessor.listen(consumerContext, tenantListener);
//
//            // mimic client side
//            producerFactory = new ConnectionContextFactory(brokerURL);
//            ProducerConnectionContext producerContext = producerFactory.createProducerConnectionContext(endpoint);
//            MessageProcessor clientSideProcessor = new MessageProcessor();
//            clientSideProcessor.send(producerContext, tenantEventToSend);
//
//            // wait for the message to flow - we should get it now
//            tenantListener.waitForMessage(3);
//            TenantEvent tenantReceivedMsg = tenantListener.getReceivedMessage();
//            assertEquals("Should have received the message",
//                    tenantReceivedMsg.getObject().getProperties().get(PROP_KEY), PROP_VALUE);
//            assertNotNull(tenantReceivedMsg.getHeaders());
//            assertEquals(2, tenantReceivedMsg.getHeaders().size()); // the other one is bus api's own classname header
//            assertEquals(PROP_VALUE, tenantReceivedMsg.getHeaders().get(PROP_KEY));
//        } finally {
//            // close everything
//            producerFactory.close();
//            consumerFactory.close();
//            broker.stop();
//        }
    }

    @Test
    public void sendMetricEvent() throws Exception {
//        // this is the same test as testFilter except the headers will be put directly in BasicMessage
//        ConnectionContextFactory consumerFactory = null;
//        ConnectionContextFactory producerFactory = null;
//
//        VMEmbeddedBrokerWrapper broker = new VMEmbeddedBrokerWrapper();
//        broker.start();
//
//        // set up message to send
//        Tenant tenant = new Tenant(CanonicalPath.fromString("/t;t"));
//        MetricType metricType = new MetricType(CanonicalPath.fromString("/t;t/mt;mt"), MetricUnit.MINUTES,
//                MetricDataType.GAUGE);
//        MetricEvent metricEventToSend = new MetricEvent(Action.Enumerated.DELETED,
//                tenant,
//                new Metric(CanonicalPath.fromString("/t;t/e;e/m;m"), metricType, objectProperties));
//        metricEventToSend.setHeaders(messageHeaders);
//
//        try {
//            String brokerURL = broker.getBrokerURL();
//            Endpoint endpoint = new Endpoint(Endpoint.Type.QUEUE, "testq");
//
//            // mimic server-side
//            consumerFactory = new ConnectionContextFactory(brokerURL);
//            ConsumerConnectionContext consumerContext = consumerFactory.createConsumerConnectionContext(endpoint);
//
//            // metric listener
//            SimpleTestListener<MetricEvent> metricListener = new SimpleTestListener<MetricEvent>(MetricEvent.class);
//            MessageProcessor serverSideMetricProcessor = new MessageProcessor();
//            serverSideMetricProcessor.listen(consumerContext, metricListener);
//
//            // mimic client side
//            producerFactory = new ConnectionContextFactory(brokerURL);
//            ProducerConnectionContext producerContext = producerFactory.createProducerConnectionContext(endpoint);
//            MessageProcessor clientSideProcessor = new MessageProcessor();
//
//            //send data
//            clientSideProcessor.send(producerContext, metricEventToSend);
//            //receive data
//            metricListener.waitForMessage(3);
//            MetricEvent metricReceivedMsg = metricListener.getReceivedMessage();
//
//            assertEquals("Should have received the message",
//                    metricReceivedMsg.getObject().getProperties().get(PROP_KEY), PROP_VALUE);
//            assertNotNull(metricReceivedMsg.getHeaders());
//            assertEquals(2, metricReceivedMsg.getHeaders().size()); // the other one is bus api's own classname header
//            assertEquals(metricType.getType(), metricReceivedMsg.getObject().getType().getType());
//            assertEquals(PROP_VALUE, metricReceivedMsg.getHeaders().get(PROP_KEY));
//        } finally {
//            // close everything
//            producerFactory.close();
//            consumerFactory.close();
//            broker.stop();
//        }
    }

    @Test
    public void testBusIntegrationHeaderInjection() throws Exception {
//        VMEmbeddedBrokerWrapper broker = new VMEmbeddedBrokerWrapper();
//
//        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, ContextFactory.class.getName());
//
//        InitialContext namingContext = new InitialContext();
//
//        Inventory inventory = ServiceLoader.load(Inventory.class).iterator().next();
//        inventory.initialize(new Configuration(new RandomUUIDFeedIdStrategy(), null, Collections.emptyMap()));
//
//        try (ConnectionContextFactory producerFactory = new ConnectionContextFactory(broker.getBrokerURL());
//             ConnectionContextFactory consumerFactory = new ConnectionContextFactory(broker.getBrokerURL())) {
//
//            broker.start();
//
//            String topicName =
//                    org.hawkular.inventory.bus.Configuration.Property.INVENTORY_CHANGES_TOPIC_NAME.getDefaultValue();
//            Endpoint topic = new Endpoint(Endpoint.Type.TOPIC, topicName);
//
//            namingContext.bind(
//                    org.hawkular.inventory.bus.Configuration.Property.CONNECTION_FACTORY_JNDI_NAME.getDefaultValue(),
//                    new TopicConnectionFactory() {
//                        @Override
//                        public TopicConnection createTopicConnection() throws JMSException {
//                            return (TopicConnection) producerFactory.createProducerConnectionContext(topic)
//                                    .getConnection();
//                        }
//
//                        @Override
//                        public TopicConnection createTopicConnection(String userName,
//                                                                     String password) throws JMSException {
//                            return createTopicConnection();
//                        }
//
//                        @Override
//                        public Connection createConnection() throws JMSException {
//                            return createTopicConnection();
//                        }
//
//                        @Override
//                        public Connection createConnection(String userName, String password) throws JMSException {
//                            return createTopicConnection(userName, password);
//                        }
//                    });
//
//            BusIntegration busIntegration = new BusIntegration(inventory);
//            busIntegration.configure(org.hawkular.inventory.bus.Configuration.builder().build());
//            busIntegration.start();
//
//
//            ConsumerConnectionContext consumerContext = consumerFactory.createConsumerConnectionContext(topic);
//
//            testHeaders(consumerContext, TenantEvent.class,
//                    () -> inventory.tenants().create(Tenant.Blueprint.builder().withId("t").build()),
//                    (headers) -> {
//                        assertThat(headers.size(), is(equalTo(4))); // the 4th is the bus API's own classname header
//                        assertThat(headers.get("path"), is(equalTo(CanonicalPath.of().tenant("t").get().toString())));
//                        assertThat(headers.get("action"), is(equalTo(Action.Enumerated.CREATED.name())));
//                        assertThat(headers.get("entityType"), is(equalTo("tenant")));
//                    });
//
//            testHeaders(consumerContext, ResourceTypeEvent.class,
//                    () -> inventory.tenants().get("t").resourceTypes().create(ResourceType.Blueprint.builder()
//                            .withId("rt").build()),
//                    (headers) -> {
//                        assertThat(headers.size(), is(equalTo(4))); // the 4th is the bus API's own classname header
//                        assertThat(headers.get("path"), is(equalTo(CanonicalPath.of().tenant("t")
//                                .resourceType("rt").get().toString())));
//                        assertThat(headers.get("action"), is(equalTo(Action.Enumerated.CREATED.name())));
//                        assertThat(headers.get("entityType"), is(equalTo("resourceType")));
//                    });
//
//            //data entity events declare one more header, so we need to check for that, too
//            testHeaders(consumerContext, DataEntityEvent.class,
//                    () -> inventory.tenants().get("t").resourceTypes().get("rt").data()
//                            .create(DataEntity.Blueprint.<ResourceTypes.DataRole>builder()
//                                    .withRole(ResourceTypes.DataRole.configurationSchema).build()),
//                    (headers) -> {
//                        assertThat(headers.size(), is(equalTo(5))); // the 5th is the bus API's own classname header
//                        assertThat(headers.get("path"), is(equalTo(CanonicalPath.of().tenant("t")
//                                .resourceType("rt").data(ResourceTypes.DataRole.configurationSchema).get()
//                                .toString())));
//                        assertThat(headers.get("action"), is(equalTo(Action.Enumerated.CREATED.name())));
//                        assertThat(headers.get("entityType"), is(equalTo("dataEntity")));
//                        assertThat(headers.get("dataRole"),
//                                is(equalTo(ResourceTypes.DataRole.configurationSchema.name())));
//                    });
//        } finally {
//            broker.stop();
//            namingContext.close();
//            inventory.close();
//        }
    }

    @Test
    public void testQueryRequestSerialization() throws IOException {
        Query allTenants = Query.filter().with(With.type(Tenant.class)).get();
        InventoryQueryRequestMessage<Tenant> requestMessage = new InventoryQueryRequestMessage<>(allTenants,
                Tenant.class, Pager.single());

        String json = requestMessage.toJSON();
        InventoryQueryRequestMessage messageFromJson = InventoryQueryRequestMessage.fromJSON(json,
                InventoryQueryRequestMessage.class);

        assertThat(requestMessage.getQuery(), is(equalTo(messageFromJson.getQuery())));
    }

    @Test
    public void testQueryResponseSerialization() {
        List<Tenant> tenantList = Arrays.asList(new Tenant("name", CanonicalPath.fromString("/t;tenant"), null));

        ResultSet<Tenant> resultSet = new ResultSet<>(tenantList,  new PageContext(1, 15, Order.unspecified()), 156);

        InventoryQueryResponseMessage<Tenant> response = new InventoryQueryResponseMessage<>(resultSet, Tenant.class);
        String json = response.toJSON();

        InventoryQueryResponseMessage<Tenant> responseFromJson = InventoryQueryResponseMessage.fromJSON(json,
                InventoryQueryResponseMessage.class);

        assertThat(responseFromJson.getResult().getPageContext(), is(equalTo(response.getResult().getPageContext())));
    }

    private void testHeaders(ConsumerConnectionContext consumerContext, Class<? extends InventoryEvent<?>> eventClass,
                             Runnable inventoryAction, Consumer<Map<String, String>> assertions)
            throws JMSException, InterruptedException {

        List<InventoryEvent<?>> receivedMessages = new ArrayList<>();

        InventoryEventMessageListener listener = new InventoryEventMessageListener() {
            @Override
            protected void onBasicMessage(InventoryEvent<?> inventoryEvent) {
                receivedMessages.add(inventoryEvent);
            }
        };

        MessageProcessor receiver = new MessageProcessor();
        receiver.listen(consumerContext, listener);

        inventoryAction.run();

        Thread.sleep(3000);

        boolean checked = false;
        for (InventoryEvent<?> e : receivedMessages) {
            if (eventClass.isAssignableFrom(e.getClass())) {
                assertions.accept(e.getHeaders());
                checked = true;
            }
        }

        if (!checked) {
            Assert.fail("No event of type " + eventClass + " received. We obtained: " + receivedMessages);
        }
    }

    public static class ContextFactory implements InitialContextFactory {

        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
            return new MapContext();
        }
    }

    public static class MapContext implements Context {
        private static final Map<String, Object> objects = new Hashtable<>();

        @Override
        public Object lookup(Name name) throws NamingException {
            return lookup(name.toString());
        }

        @Override
        public Object lookup(String name) throws NamingException {
            return objects.get(name);
        }

        @Override
        public void bind(Name name, Object obj) throws NamingException {
            bind(name.toString(), obj);
        }

        @Override
        public void bind(String name, Object obj) throws NamingException {
            objects.put(name, obj);
        }

        @Override
        public void rebind(Name name, Object obj) throws NamingException {
            rebind(name.toString(), obj);
        }

        @Override
        public void rebind(String name, Object obj) throws NamingException {
            objects.put(name, obj);
        }

        @Override
        public void unbind(Name name) throws NamingException {
            unbind(name.toString());
        }

        @Override
        public void unbind(String name) throws NamingException {
            objects.remove(name);
        }

        @Override
        public void rename(Name oldName, Name newName) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rename(String oldName, String newName) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroySubcontext(Name name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroySubcontext(String name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Context createSubcontext(Name name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Context createSubcontext(String name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object lookupLink(Name name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object lookupLink(String name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public NameParser getNameParser(Name name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public NameParser getNameParser(String name) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Name composeName(Name name, Name prefix) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String composeName(String name, String prefix) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object addToEnvironment(String propName, Object propVal) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object removeFromEnvironment(String propName) throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Hashtable<?, ?> getEnvironment() throws NamingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws NamingException {
        }

        @Override
        public String getNameInNamespace() throws NamingException {
            throw new UnsupportedOperationException();
        }
    }
}
