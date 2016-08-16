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

import java.io.IOException;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.consumer.ConsumerConnectionContext;
import org.hawkular.bus.common.consumer.RPCBasicMessageListener;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.bus.api.InventoryQueryRequestMessage;
import org.hawkular.inventory.bus.api.InventoryQueryResponseMessage;
import org.hawkular.inventory.bus.api.ResultSet;

/**
 * @author Pavol Loffay
 * @since 0.13.0
 */
public class QueryListener extends
        RPCBasicMessageListener<InventoryQueryRequestMessage, InventoryQueryResponseMessage<?>> {

    private final Inventory inventory;
    private final ConsumerConnectionContext connectionContext;

    public QueryListener(Inventory inventory, ConnectionFactory contextFactory, String queName)
            throws JMSException {
        this.inventory = inventory;

        ConnectionContextFactory factory = new ConnectionContextFactory(contextFactory);
        Endpoint endpoint = new Endpoint(Endpoint.Type.QUEUE, queName);
        connectionContext = factory.createConsumerConnectionContext(endpoint);

        MessageProcessor processor = new MessageProcessor();
        processor.listen(connectionContext, this);
    }

    public void close() throws IOException {
        connectionContext.close();
    }

    @Override
    public InventoryQueryResponseMessage<?> onBasicMessage(
            BasicMessageWithExtraData<InventoryQueryRequestMessage> msgWithExtraData) {

        final InventoryQueryRequestMessage message = msgWithExtraData.getBasicMessage();

        Log.LOG.tracef("Query message received, entity = %s", message.getEntity().toString());

        Page page = inventory.execute(message.getQuery(), message.getEntity(), message.getPager());

        ResultSet resultSet = new ResultSet(page.toList(), page.getPageContext(), page.getTotalSize());
        InventoryQueryResponseMessage<?> response = new InventoryQueryResponseMessage<>(resultSet, message.getEntity());

        return response;
    }
}
