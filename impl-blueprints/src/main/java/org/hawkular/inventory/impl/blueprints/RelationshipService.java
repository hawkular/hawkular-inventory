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
package org.hawkular.inventory.impl.blueprints;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Relationship;

import java.util.Set;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class RelationshipService implements Relationships.ReadWrite {
    private final TransactionalGraph graph;
    private final Vertex startVertex;


    RelationshipService(TransactionalGraph graph, Vertex startVertex) {
        this.graph = graph;
        this.startVertex = startVertex;
    }

    @Override
    public Relationships.Browser get(String id) {
        //TODO implement
        return null;
    }

    @Override
    public Set<String> getAllIds(Filter... filters) {
        //TODO implement
        return null;
    }

    @Override
    public Set<Relationship> getAll(Filter... filters) {
        //TODO implement
        return null;
    }

    @Override
    public Relationships.Browser create(Relationship.Blueprint blueprint) {
        //TODO implement
        return null;
    }

    @Override
    public void update(Relationship entity) {
        //TODO implement

    }

    @Override
    public void delete(String id) {
        //TODO implement

    }
}
