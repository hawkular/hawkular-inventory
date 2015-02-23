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

import com.tinkerpop.blueprints.Compare;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.pipes.Pipe;
import com.tinkerpop.pipes.filter.PropertyFilterPipe;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
class FilterVisitor {
    private static AtomicInteger CNT = new AtomicInteger();

    public void visit(HawkularPipeline<?, ?> query, Related<? extends Entity> related) {
        String step = "filter" + CNT.getAndIncrement();

        query.as(step);

        switch (related.getEntityRole()) {
            case TARGET:
                query.in(related.getRelationshipName());
                break;
            case SOURCE:
                query.out(related.getRelationshipName());
                break;
            case ANY:
                query.both(related.getRelationshipName());
        }

        if (related.getEntity() != null) {
            Constants.Type desiredType = Constants.Type.of(related.getEntity());

            query.hasType(desiredType).hasUid(related.getEntity().getId());
        }

        query.back(step);
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularPipeline<?, ?> query, With.Ids ids) {
        if (ids.getIds().length == 1) {
            query.has(Constants.Property.uid.name(), ids.getIds()[0]);
            return;
        }

        Pipe[] idChecks = new Pipe[ids.getIds().length];

        Arrays.setAll(idChecks, i ->
                new PropertyFilterPipe<Element, String>(Constants.Property.uid.name(), Compare.EQUAL, ids.getIds()[i]));

        query.or(idChecks);
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularPipeline<?, ?> query, With.Types types) {
        if (types.getTypes().length == 1) {
            Constants.Type type = Constants.Type.of(types.getTypes()[0]);
            query.has(Constants.Property.type.name(), type.name());
            return;
        }

        Pipe[] typeChecks = new Pipe[types.getTypes().length];

        Arrays.setAll(typeChecks, i -> {
            Constants.Type type = Constants.Type.of(types.getTypes()[i]);
            return new PropertyFilterPipe<Element, String>(Constants.Property.type.name(), Compare.EQUAL, type.name());
        });

        query.or(typeChecks);
    }
}
