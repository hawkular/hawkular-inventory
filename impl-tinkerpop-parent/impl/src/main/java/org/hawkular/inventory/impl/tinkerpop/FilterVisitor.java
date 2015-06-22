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
package org.hawkular.inventory.impl.tinkerpop;

import java.util.Arrays;

import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.base.spi.NoopFilter;
import org.hawkular.inventory.base.spi.SwitchElementType;

import com.tinkerpop.blueprints.Compare;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.pipes.Pipe;
import com.tinkerpop.pipes.filter.PropertyFilterPipe;

/**
 * @author Lukas Krejci
 * @author Jirka Kremser
 * @since 0.0.1
 */
class FilterVisitor {

    public void visit(HawkularPipeline<?, ?> query, Related related) {
        switch (related.getEntityRole()) {
            case TARGET:
                if (null != related.getRelationshipName()) {
                    query.in(related.getRelationshipName());
                }
                if (null != related.getRelationshipId()) {
                    // TODO test
                    query.inE().hasEid(related.getRelationshipId()).inV();
                }
                break;
            case SOURCE:
                if (null != related.getRelationshipName()) {
                    query.out(related.getRelationshipName());
                }
                if (null != related.getRelationshipId()) {
                    // TODO test
                    query.outE().hasEid(related.getRelationshipId()).outV();
                }
                break;
            case ANY:
                if (null != related.getRelationshipName()) {
                    query.both(related.getRelationshipName());
                }
                if (null != related.getRelationshipId()) {
                    // TODO test
                    query.bothE().hasEid(related.getRelationshipId()).bothV();
                }
        }

        if (related.getEntityPath() != null) {
            query.hasCanonicalPath(related.getEntityPath());
        }
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularPipeline<?, ?> query, With.Ids ids) {
        if (ids.getIds().length == 1) {
            query.has(Constants.Property.__eid.name(), ids.getIds()[0]);
            return;
        }

        Pipe[] idChecks = new Pipe[ids.getIds().length];

        Arrays.setAll(idChecks, i ->
                new PropertyFilterPipe<Element, String>(Constants.Property.__eid.name(), Compare.EQUAL,
                        ids.getIds()[i]));

        query.or(idChecks);
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularPipeline<?, ?> query, With.Types types) {
        if (types.getTypes().length == 1) {
            Constants.Type type = Constants.Type.of(types.getTypes()[0]);
            query.has(Constants.Property.__type.name(), type.name());
            return;
        }

        Pipe[] typeChecks = new Pipe[types.getTypes().length];

        Arrays.setAll(typeChecks, i -> {
            Constants.Type type = Constants.Type.of(types.getTypes()[i]);
            return new PropertyFilterPipe<Element, String>(Constants.Property.__type.name(), Compare.EQUAL,
                    type.name());
        });

        query.or(typeChecks);
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularPipeline<?, ?> query, RelationWith.Ids ids) {
        if (ids.getIds().length == 1) {
            query.hasEid(ids.getIds()[0]);
            return;
        }

        Pipe[] idChecks = new Pipe[ids.getIds().length];

        Arrays.setAll(idChecks, i ->
                new PropertyFilterPipe<Element, String>(Constants.Property.__eid.name(), Compare.EQUAL,
                        ids.getIds()[i]));

        query.or(idChecks);
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularPipeline<?, ?> query,
            RelationWith.Properties properties) {
        if (properties.getValues().length == 1) {
            query.has(properties.getProperty(), properties.getValues()[0]);
            return;
        }

        Pipe[] idChecks = new Pipe[properties.getValues().length];

        Arrays.setAll(idChecks, i ->
                new PropertyFilterPipe<Element, String>(properties.getProperty(), Compare.EQUAL, properties
                        .getValues()[i]));

        query.or(idChecks);
    }

    public void visit(HawkularPipeline<?, ?> query,
            RelationWith.SourceOfType types) {
        visit(query, types, true);
    }

    public void visit(HawkularPipeline<?, ?> query,
            RelationWith.TargetOfType types) {
        visit(query, types, false);
    }

    public void visit(HawkularPipeline<?, ?> query,
            RelationWith.SourceOrTargetOfType types) {
        visit(query, types, null);
    }

    @SuppressWarnings("unchecked")
    private void visit(HawkularPipeline<?, ?> query,
            RelationWith.SourceOrTargetOfType types, Boolean source) {
        // look ahead if the type of the incidence vertex is of the desired type(s)
        HawkularPipeline<?, ?> q1 = query.remember();
        HawkularPipeline<?, ?> q2;
        if (source == null) {
            q2 = q1.bothV();
        } else if (source) {
            q2 = q1.outV();
        } else {
            q2 = q1.inV();
        }
        if (types.getTypes().length == 1) {
            Constants.Type type = Constants.Type.of(types.getTypes()[0]);
            q2.has(Constants.Property.__type.name(), type.name()).recall();
            return;
        }

        Pipe[] typeChecks = new Pipe[types.getTypes().length];
        Arrays.setAll(typeChecks, i -> {
            Constants.Type type = Constants.Type.of(types.getTypes()[i]);
            return new PropertyFilterPipe<Element, String>(Constants.Property.__type.name(), Compare.EQUAL,
                    type.name());
        });

        q2.or(typeChecks).recall();
    }

    public void visit(HawkularPipeline<?, ?> query, SwitchElementType filter) {
        final boolean jumpFromEdge = filter.isFromEdge();
        switch (filter.getDirection()) {
            case incoming:
                if (jumpFromEdge) {
                    query.outV();
                } else {
                    query.inE();
                }
                break;
            case outgoing:
                if (jumpFromEdge) {
                    query.inV();
                } else {
                    query.outE();
                }
                break;
            case both:
                if (jumpFromEdge) {
                    query.bothV();
                } else {
                    query.bothE();
                }
                break;
        }
    }

    public void visit(HawkularPipeline<?, ?> query, NoopFilter filter) {
        //nothing to do
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularPipeline<?, ?> query, With.CanonicalPaths filter) {
        if (filter.getPaths().length == 1) {
            query.has(Constants.Property.__cp.name(), filter.getPaths()[0].toString());
            return;
        }

        Pipe[] idChecks = new Pipe[filter.getPaths().length];

        Arrays.setAll(idChecks, i ->
                new PropertyFilterPipe<Element, String>(Constants.Property.__cp.name(), Compare.EQUAL,
                        filter.getPaths()[i].toString()));

        query.or(idChecks);
    }
}
