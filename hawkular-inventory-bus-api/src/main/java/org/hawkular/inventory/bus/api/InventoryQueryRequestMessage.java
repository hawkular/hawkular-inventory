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

import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.paging.Pager;

/**
 * @author Pavol Loffay
 * @since 0.13.0
 */
public class InventoryQueryRequestMessage<T extends AbstractElement<?, ?>> extends InventoryAbstractMessage {

    private Query query;
    private Pager pager;
    private Class<T> entity;


    public InventoryQueryRequestMessage() {
    }

    public InventoryQueryRequestMessage(Query query, Class<T> entity, Pager pager) {
        this.query = query;
        this.entity = entity;
        this.pager = pager;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public Class<T> getEntity() {
        return entity;
    }

    public void setEntity(Class<T> entity) {
        this.entity = entity;
    }

    public Pager getPager() {
        return pager;
    }

    public void setPager(Pager pager) {
        this.pager = pager;
    }
}
