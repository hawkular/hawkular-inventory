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
package org.hawkular.inventory.lazy.spi;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.QueryFragmentTree;

import java.util.Map;
import java.util.function.Function;

/**
 * The backend for the lazy inventory that does all the "low level" stuff like querying the actual inventory store,
 * its modifications, etc.
 *
 * @param <E> the type of the backend-specific objects representing the inventory entities. It is assumed that the
 *            backend is "untyped" and stores all different inventory entities using this single type.
 *
 * @author Lukas Krejci
 * @since 0.0.6
 */
public interface LazyInventoryBackend<E> {

    Page<E> query(QueryFragmentTree query, Pager pager);

    <T> Page<T> query(QueryFragmentTree query, Pager pager, Function<E, T> conversion, Function<T, Boolean> filter);

    String extractId(E entityRepresentation);

    Map<String, Object> extractProperties(E entityRepresentation);

    Class<? extends AbstractElement<?, ?>> getType(E entityRepresentation);

    <T extends AbstractElement<?, ?>> T convert(E entityRepresentation, Class<T> entityType);

    void relate(E sourceEntity, E targetEntity, String label, Map<String, Object> properties);

    E persist(CanonicalPath targetPath, Entity.Blueprint entity);

    void update(E entity, AbstractElement.Update update);

    void delete(AbstractElement<?, ?> element);

    void commit();

    void rollback();
}
