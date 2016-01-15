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
package org.hawkular.inventory.bus.api;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.bus.api.mixins.ResultSetMixin;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Pavol Loffay
 * @since 0.13.0
 */
public class InventoryQueryResponseMessage<T extends AbstractElement<?, ?>> extends
        InventoryAbstractMessage {

    private ResultSet<T> result;
    private Class<? extends AbstractElement> entityClass;


    private InventoryQueryResponseMessage() {
    }

    public InventoryQueryResponseMessage(ResultSet<T> result, Class<? extends AbstractElement> entityClass) {
        this.result = result;
        this.entityClass = entityClass;
    }

    public ResultSet<T> getResult() {
        return result;
    }

    public void setResult(ResultSet<T> result) {
        this.result = result;
    }

    public Class<? extends AbstractElement> getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(Class<? extends AbstractElement> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    protected ObjectMapper buildObjectMapperForSerialization() {
        ObjectMapper objectMapper = super.buildObjectMapperForSerialization();

        objectMapper.addMixIn(ResultSet.class, ResultSetMixin.class);
        return objectMapper;
    }

    public static ObjectMapper buildObjectMapperForDeserialization() {
        ObjectMapper objectMapper = InventoryAbstractMessage.buildObjectMapperForDeserialization();

        objectMapper.addMixIn(ResultSet.class, ResultSetMixin.class);
        return objectMapper;
    }
}
