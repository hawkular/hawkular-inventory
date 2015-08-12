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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.consumer.ConsumerConnectionContext;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.hawkular.bus.common.test.SimpleTestListener;
import org.hawkular.bus.common.test.VMEmbeddedBrokerWrapper;
import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.bus.api.EnvironmentEvent;
import org.hawkular.inventory.bus.api.FeedEvent;
import org.hawkular.inventory.bus.api.MetricEvent;
import org.hawkular.inventory.bus.api.MetricTypeEvent;
import org.hawkular.inventory.bus.api.RelationshipEvent;
import org.hawkular.inventory.bus.api.ResourceEvent;
import org.hawkular.inventory.bus.api.ResourceTypeEvent;
import org.hawkular.inventory.bus.api.TenantEvent;
import org.junit.BeforeClass;
import org.junit.Test;

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
        Tenant tenant = new Tenant(CanonicalPath.fromString("/t;c"), objectProperties);
        MetricType metricType = new MetricType(CanonicalPath.fromString("/t;t/mt;mt"), MetricUnit.MINUTES,
                MetricDataType.GAUGE);
        ResourceType resourceType = new ResourceType(CanonicalPath.fromString("/t;t/rt;rt"), objectProperties);

        TenantEvent tenantEvent = new TenantEvent(Action.Enumerated.CREATED, tenant);
        EnvironmentEvent environmentEvent = new EnvironmentEvent(Action.Enumerated.CREATED,
                new Environment(CanonicalPath.fromString("/t;t/e;e"), objectProperties));
        FeedEvent feedEvent = new FeedEvent(Action.Enumerated.UPDATED,
                new Feed(CanonicalPath.fromString("/t;t/e;e/f;f"), objectProperties));
        MetricEvent metricEvent = new MetricEvent(Action.Enumerated.DELETED,
                new Metric(CanonicalPath.fromString("/t;t/e;e/m;m"), metricType, objectProperties));
        MetricTypeEvent metricTypeEvent = new MetricTypeEvent(Action.Enumerated.COPIED, metricType);
        RelationshipEvent relationshipEvent = new RelationshipEvent(Action.Enumerated.REGISTERED,
                new Relationship("id", "rel1", CanonicalPath.fromString("/t;t"), CanonicalPath.fromString("/t;t/e;e")
                        , objectProperties));
        ResourceEvent resourceEvent = new ResourceEvent(Action.Enumerated.UPDATED,
                new Resource(CanonicalPath.fromString("/t;t/e;e/r;r"), resourceType));
        ResourceTypeEvent resourceTypeEvent = new ResourceTypeEvent(Action.Enumerated.COPIED, resourceType);

        String tenantJSON = tenantEvent.toJSON();
        String envJSON = environmentEvent.toJSON();
        String feedJSON = feedEvent.toJSON();
        String metricJSON = metricEvent.toJSON();
        String metricTypeJSON = metricTypeEvent.toJSON();
        String relationshipJSON = relationshipEvent.toJSON();
        String resourceJSON = resourceEvent.toJSON();
        String resourceTypeJSON = resourceTypeEvent.toJSON();

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
    }

    @Test
    public void createTenantEventFromJSON() {
        Tenant tenant = new Tenant(CanonicalPath.fromString("/t;c"), objectProperties);
        TenantEvent tenantEvent = new TenantEvent(Action.Enumerated.CREATED, tenant);

        String tenantJSON = "{\"action\":\"CREATED\",\"object\":{\"path\":\"/t;c\",\"properties\":{\"p1\":\"prop1\"}}}";
        TenantEvent tenantEventFromJSON = TenantEvent.fromJSON(tenantJSON, TenantEvent.class);

        assertThat(tenantEvent.getObject().getPath(), is(equalTo(tenantEventFromJSON.getObject().getPath())));
    }

    @Test
    public void createMetricEventFromJSON() {
        MetricType metricType = new MetricType(CanonicalPath.fromString("/t;t/mt;mt"), MetricUnit.MINUTES,
                MetricDataType.GAUGE);
        MetricEvent metricEvent = new MetricEvent(Action.Enumerated.DELETED,
                new Metric(CanonicalPath.fromString("/t;t/e;e/m;m"), metricType, objectProperties));

        String metricJSON = "{\"action\":\"DELETED\",\"object\":{\"path\":\"/t;t/e;e/m;m\",\"type\"" +
                ":{\"path\":\"/t;t/mt;mt\",\"properties\":null,\"unit\":\"MINUTES\",\"type\":\"GAUGE\"}," +
                "\"properties\":{}}}";
        MetricEvent metricEventFromJSON = MetricEvent.fromJSON(metricJSON, MetricEvent.class);

        assertThat(metricEvent.getObject().getPath(), is(equalTo(metricEventFromJSON.getObject().getPath())));
    }

    @Test
    public void sendTenantEvent() throws Exception {
        // this is the same test as testFilter except the headers will be put directly in BasicMessage
        ConnectionContextFactory consumerFactory = null;
        ConnectionContextFactory producerFactory = null;

        VMEmbeddedBrokerWrapper broker = new VMEmbeddedBrokerWrapper();
        broker.start();

        // set up message to send
        Tenant tenantToSend = new Tenant(CanonicalPath.fromString("/t;c"), objectProperties);
        // send one that will match the selector
        TenantEvent tenantEventToSend = new TenantEvent(Action.Enumerated.CREATED, tenantToSend);
        tenantEventToSend.setHeaders(messageHeaders);

        try {
            String brokerURL = broker.getBrokerURL();
            Endpoint endpoint = new Endpoint(Endpoint.Type.QUEUE, "testq");

            // mimic server-side
            consumerFactory = new ConnectionContextFactory(brokerURL);
            ConsumerConnectionContext consumerContext = consumerFactory.createConsumerConnectionContext(endpoint);
            // tenant listener
            SimpleTestListener<TenantEvent> tenantListener = new SimpleTestListener<TenantEvent>(TenantEvent.class);
            MessageProcessor serverSideTenantProcessor = new MessageProcessor();
            serverSideTenantProcessor.listen(consumerContext, tenantListener);

            // mimic client side
            producerFactory = new ConnectionContextFactory(brokerURL);
            ProducerConnectionContext producerContext = producerFactory.createProducerConnectionContext(endpoint);
            MessageProcessor clientSideProcessor = new MessageProcessor();
            clientSideProcessor.send(producerContext, tenantEventToSend);

            // wait for the message to flow - we should get it now
            tenantListener.waitForMessage(3);
            TenantEvent tenantReceivedMsg = tenantListener.getReceivedMessage();
            assertEquals("Should have received the message",
                         tenantReceivedMsg.getObject().getProperties().get(PROP_KEY), PROP_VALUE);
            assertNotNull(tenantReceivedMsg.getHeaders());
            assertEquals(1, tenantReceivedMsg.getHeaders().size());
            assertEquals(PROP_VALUE, tenantReceivedMsg.getHeaders().get(PROP_KEY));
        } finally {
            // close everything
            producerFactory.close();
            consumerFactory.close();
            broker.stop();
        }
    }

    @Test
    public void sendMetricEvent() throws Exception {
        // this is the same test as testFilter except the headers will be put directly in BasicMessage
        ConnectionContextFactory consumerFactory = null;
        ConnectionContextFactory producerFactory = null;

        VMEmbeddedBrokerWrapper broker = new VMEmbeddedBrokerWrapper();
        broker.start();

        // set up message to send
        MetricType metricType = new MetricType(CanonicalPath.fromString("/t;t/mt;mt"), MetricUnit.MINUTES,
                MetricDataType.GAUGE);
        MetricEvent metricEventToSend = new MetricEvent(Action.Enumerated.DELETED,
                new Metric(CanonicalPath.fromString("/t;t/e;e/m;m"), metricType, objectProperties));
        metricEventToSend.setHeaders(messageHeaders);

        try {
            String brokerURL = broker.getBrokerURL();
            Endpoint endpoint = new Endpoint(Endpoint.Type.QUEUE, "testq");

            // mimic server-side
            consumerFactory = new ConnectionContextFactory(brokerURL);
            ConsumerConnectionContext consumerContext = consumerFactory.createConsumerConnectionContext(endpoint);

            // metric listener
            SimpleTestListener<MetricEvent> metricListener = new SimpleTestListener<MetricEvent>(MetricEvent.class);
            MessageProcessor serverSideMetricProcessor = new MessageProcessor();
            serverSideMetricProcessor.listen(consumerContext, metricListener);

            // mimic client side
            producerFactory = new ConnectionContextFactory(brokerURL);
            ProducerConnectionContext producerContext = producerFactory.createProducerConnectionContext(endpoint);
            MessageProcessor clientSideProcessor = new MessageProcessor();

            //send data
            clientSideProcessor.send(producerContext, metricEventToSend);
            //receive data
            metricListener.waitForMessage(3);
            MetricEvent metricReceivedMsg = metricListener.getReceivedMessage();

            assertEquals("Should have received the message",
                         metricReceivedMsg.getObject().getProperties().get(PROP_KEY), PROP_VALUE);
            assertNotNull(metricReceivedMsg.getHeaders());
            assertEquals(1, metricReceivedMsg.getHeaders().size());
            assertEquals(metricType.getType(), metricReceivedMsg.getObject().getType().getType());
            assertEquals(PROP_VALUE, metricReceivedMsg.getHeaders().get(PROP_KEY));
        } finally {
            // close everything
            producerFactory.close();
            consumerFactory.close();
            broker.stop();
        }
    }
}
