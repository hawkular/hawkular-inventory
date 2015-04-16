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

import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.model.Entity;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
class PathVisitor extends FilterVisitor {
    @Override
    public void visit(HawkularPipeline<?, ?> query, Related<? extends Entity> related) {
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

        if (related.getEntity() != null) {
            Constants.Type desiredType = Constants.Type.of(related.getEntity());

            query.hasType(desiredType).hasEid(related.getEntity().getId());
        }
    }
}
