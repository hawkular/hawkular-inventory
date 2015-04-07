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

import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.hawkular.inventory.api.observable.Interest;

import javax.jms.JMSException;

import java.util.HashMap;
import java.util.Map;

import static org.hawkular.inventory.bus.Log.LOG;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class MessageSender {
    private final ProducerConnectionContext producerConnectionContext;
    private final MessageProcessor messageProcessor;

    public MessageSender(ProducerConnectionContext producerConnectionContext) {
        this.producerConnectionContext = producerConnectionContext;
        this.messageProcessor = new MessageProcessor();
    }

    public void send(Interest<?, ?> interest, Object inventoryEvent) {
        InventoryEvent message = new InventoryEvent(inventoryEvent);
        try {
            Map<String, String> headers = toHeaders(interest);
            messageProcessor.send(producerConnectionContext, message, headers);
        } catch (JMSException e) {
            LOG.failedToSendMessage(message.toString());
        }
    }

    private Map<String, String> toHeaders(Interest<?, ?> interest) {
        HashMap<String, String> ret = new HashMap<>();

        ret.put("action", interest.getAction().asEnum().name().toLowerCase());
        ret.put("entityType", firstLetterLowercased(interest.getEntityType().getSimpleName()));

        return ret;
    }

    private String firstLetterLowercased(String source) {
        return Character.toLowerCase(source.charAt(0)) + source.substring(1);
    }
}
